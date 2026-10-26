# Prior art: universal Android share-capture ("yearn")

Research date: 2026-10-26. Scope: open-source projects that accept Android shares and capture content, and the platform/extraction constraints behind them. Repo metadata (license, stars, `pushed_at`, `archived`, latest release) was pulled from the GitHub REST API (`gh api repos/OWNER/REPO`, `.../releases/latest`) and the Codeberg API on 2026-10-26. Share-intent claims come from each project's `AndroidManifest.xml` or share-handling source, linked inline. Anything not checked against a primary source is marked **[unverified]**.

---

## Summary: headline findings

1. **No open-source project does the full yearn loop.** That loop is: any-app share, then maximal raw capture (real media, caption, author, source app), then a searchable graph. Every project covers one or two of those three steps. The read-it-later/bookmark tools store the URL and archive the *web page*. The media downloaders fetch the *real media* but keep no knowledge store. The PKM/graph tools store whatever text or file the share carried and fetch nothing.
2. **The closest server-side match is ArchiveBox plus its new Android app.** ArchiveBox runs yt-dlp with `--write-info-json --write-description --write-thumbnail --write-sub --write-auto-subs` and gallery-dl with `--write-metadata --write-info-json` by default. It also captures SingleFile/HTML/PDF/screenshot/WARC ([ytdlp config](https://github.com/ArchiveBox/abx-plugins/blob/main/abx_plugins/plugins/ytdlp/config.json), [gallerydl config](https://github.com/ArchiveBox/abx-plugins/blob/main/abx_plugins/plugins/gallerydl/config.json)). The native Kotlin client [ArchiveBox/android-archivebox](https://github.com/ArchiveBox/android-archivebox) (MIT, created 2026-09-20, v1.13.13 on 2026-10-25) accepts only `text/plain` and `text/uri-list` shares ([manifest](https://github.com/ArchiveBox/android-archivebox/blob/main/app/src/main/AndroidManifest.xml)). It has no graph or semantic layer and needs a reachable server.
3. **The closest "bookmark-everything + AI" match is Karakeep (formerly Hoarder).** It is AGPL-3.0 with about 30k stars and released v0.34.1 on 2026-10-22. It has an Android share target, AI tagging/summaries, OCR, Meilisearch full-text, experimental semantic search, monolith full-page archive and optional yt-dlp video download ([README](https://github.com/karakeep-app/karakeep/blob/main/README.md), [env docs](https://github.com/karakeep-app/karakeep/blob/main/docs/docs/03-configuration/01-environment-variables.md)). However, the mobile share handler keeps **only the first URL found in the text, or the first file**, and drops the rest of the shared text and the subject. Its video worker saves only the video file, with no info-json or caption ([sharing.tsx](https://github.com/karakeep-app/karakeep/blob/main/apps/mobile/app/sharing.tsx), [videoWorker.ts](https://github.com/karakeep-app/karakeep/blob/main/apps/workers/workers/videoWorker.ts)). It has no graph view.
4. **The closest match for capturing a post's media and caption is Sharewerk** ([Azentrius/sharewerk](https://github.com/Azentrius/sharewerk), Apache-2.0, created 2026-10-19, 0 stars). It is a PWA share target backed by a Docker server running gallery-dl, yt-dlp, ffmpeg and faster-whisper. For Instagram and Facebook it returns every carousel image, video key frames, a transcript and a `post.txt` with source, author, date and caption. It needs cookies from a *secondary* Instagram account. It is a pipe that passes content on to an AI chat app, not a library. **On-device**, the closest is [Sesame](https://github.com/Abdull0100/sesame) (GPL-2.0, created 2026-08-02, no stable release). Sesame embeds gallery-dl and yt-dlp through Chaquopy and offers optional metadata, thumbnails, subtitles and Instagram comments.
5. **The closest on-device "memory bank" is MemGallery** ([Past-da-king/MemGallery](https://github.com/Past-da-king/MemGallery), MIT, Kotlin, 103 stars, last push 2026-08-23). It is a local-first app with share targets for `text/plain` and `image/*`, AI indexing (local, Gemini, Groq or any OpenAI-compatible provider), semantic search and auto-tags. It does not download the media behind links (not documented) and has no graph.
6. **Android does not reliably tell you which app shared.** `Activity.getReferrer()` (API 22) returns `EXTRA_REFERRER` or the launching package, but the docs say "this is not a security feature… applications can spoof it". `getLaunchedFromPackage()` (API 34) returns null unless the sender opts in through `ActivityOptions.setShareIdentityEnabled(true)` ([Activity reference](https://developer.android.com/reference/android/app/Activity)). The AOSP chooser does launch targets with `startActivityAsCaller`, so the referrer is usually the original app when the system sheet is used ([DisplayResolveInfo.java](https://android.googlesource.com/platform/packages/modules/IntentResolver/+/refs/heads/main/java/src/com/android/intentresolver/chooser/DisplayResolveInfo.java)). Treat the source app as a best-effort hint plus URL-host inference.
7. **The share itself almost always carries only text.** Chrome's own source sends `text/plain` with `EXTRA_TEXT` set to optional highlighted text plus the URL, and `EXTRA_SUBJECT`/`EXTRA_TITLE` set to the page title ([Chromium ShareHelper.java](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/components/browser_ui/share/android/java/src/org/chromium/components/browser_ui/share/ShareHelper.java)). Closed-source social apps (Instagram, TikTok, X, Reddit, YouTube) are not documented. Receivers such as NewPipe, Seal and expo-share-intent all regex the *first URL out of free text*, which implies those apps send text with a URL embedded in it. Real media and captions therefore require a post-share extraction step: yt-dlp, gallery-dl or instaloader. For Instagram that step practically requires logged-in cookies.
8. **Public metadata endpoints are thin.** YouTube oEmbed returns title, author and thumbnail without auth. X oEmbed returns author and tweet text inside `html` without auth (both live-probed). Instagram oEmbed needs a Meta access token and its documented fields are just `html`, `provider_*`, `type`, `version` and `width`; private, inactive and age-restricted accounts are unsupported ([Graph API instagram_oembed](https://developers.facebook.com/docs/graph-api/reference/instagram-oembed/)). Reddit's unauthenticated `.json` returned **403** from this machine. `tiktok.com` **redirected to `/in/about`** from this machine's network (India), so TikTok capture from an Indian IP cannot be assumed to work.

---

## 1. Read-it-later / bookmark tools with Android share targets

These all store a **URL** and archive the **web page** server-side. None captures Instagram, TikTok or X post media and captions as structured data, except Karakeep's optional yt-dlp video-file download.

### Karakeep (formerly Hoarder)
- Repo: [karakeep-app/karakeep](https://github.com/karakeep-app/karakeep). AGPL-3.0, about 30.0k stars, pushed 2026-10-26, latest release [v0.34.1](https://github.com/karakeep-app/karakeep/releases) on 2026-10-22. README: "Karakeep (previously Hoarder) is a self-hostable bookmark-everything app" ([README](https://github.com/karakeep-app/karakeep/blob/main/README.md)).
- Stack: TypeScript monorepo (Next.js web, workers). The mobile app is Expo/React Native using `expo-share-intent`, and its Android intent filters are `text/*`, `image/*` and `application/pdf` ([app.config.js](https://github.com/karakeep-app/karakeep/blob/main/apps/mobile/app.config.js)). The Android package is `app.hoarder.hoardermobile` (README Play link).
- What a share captures ([sharing.tsx](https://github.com/karakeep-app/karakeep/blob/main/apps/mobile/app/sharing.tsx)):
  - If `shareIntent.webUrl` exists, it creates a LINK bookmark with just that URL. `webUrl` is the first `http…` match regexed out of the shared text ([expo-share-intent utils.ts](https://github.com/achorein/expo-share-intent/blob/main/src/utils.ts)), so the rest of the text (caption fragment, highlight) is discarded.
  - Otherwise text becomes a TEXT bookmark.
  - Otherwise **only `files[0]`** is uploaded.
  - The subject/title is not passed. Android `expo-share-intent` populates `meta.title` from `EXTRA_TITLE` only; [an open PR #229](https://github.com/achorein/expo-share-intent/pull/229) adds `EXTRA_SUBJECT`.
- Server-side enrichment ([env docs](https://github.com/karakeep-app/karakeep/blob/main/docs/docs/03-configuration/01-environment-variables.md)):
  - headless-Chrome crawl, screenshot (`CRAWLER_STORE_SCREENSHOT` default true, full-page optional)
  - PDF snapshot (`CRAWLER_STORE_PDF`, default false)
  - monolith full-page archive (`CRAWLER_FULL_PAGE_ARCHIVE`, default false)
  - video download via yt-dlp (`CRAWLER_VIDEO_DOWNLOAD`, default false, 50 MB default cap, extra args via `CRAWLER_YTDLP_ARGS`). The worker passes only `url`, `-f`, user args, `-o`, `--no-playlist` and `--proxy`, and stores the single output file as an asset ([videoWorker.ts](https://github.com/karakeep-app/karakeep/blob/main/apps/workers/workers/videoWorker.ts)). No info.json or captions are kept as assets.
  - OCR (tesseract.js or an LLM)
  - LLM auto-tagging and optional summarization (OpenAI or Ollama)
  - embeddings and "hybrid and semantic bookmark search modes. **Experimental.**"
  - Meilisearch full-text search, rule engine, RSS feeds
- Organization: tags, lists, smart lists, rule engine. **No graph view** (not in the README feature list).
- Self-hosted server required.

### Linkwarden
- Repo: [linkwarden/linkwarden](https://github.com/linkwarden/linkwarden). AGPL-3.0, about 20.2k stars, pushed 2026-10-26, [v2.16.3](https://github.com/linkwarden/linkwarden/releases) on 2026-09-09.
- Stack: TypeScript monorepo. The official mobile app is Expo/RN, and its Android share filter is `text/*` only ([app.config.ts](https://github.com/linkwarden/linkwarden/blob/main/apps/mobile/app.config.ts), handler [incoming.tsx](https://github.com/linkwarden/linkwarden/blob/main/apps/mobile/app/incoming.tsx)).
- Capture: URL. The server's archive handler produces screenshot, PDF, monolith single-file HTML and readability text, with optional Wayback submission and AI tagging, configurable per tag ([archiveHandler.ts](https://github.com/linkwarden/linkwarden/blob/main/apps/worker/lib/archiveHandler.ts)). Direct image and PDF links get dedicated handlers. **There is no yt-dlp anywhere in the repo tree** (checked the git tree), so no video or social-media fetch.
- Search and organization: full-text search, collections and sub-collections, tags, highlights and annotations, RSS ([README](https://github.com/linkwarden/linkwarden/blob/main/README.md)). No graph.

### wallabag
- Server: [wallabag/wallabag](https://github.com/wallabag/wallabag). MIT, PHP, pushed 2026-10-26, last release 2.6.14 on 2025-10-07. Android app: [wallabag/android-app](https://github.com/wallabag/android-app), GPL-3.0, Java, [2.6.0](https://github.com/wallabag/android-app/releases) on 2026-06-01.
- Share: one `ACTION_SEND` filter with `text/plain` on a proxy activity ([manifest](https://github.com/wallabag/android-app/blob/master/app/src/main/AndroidManifest.xml)). The URL is sent to the server, which extracts readable article content ([README](https://github.com/wallabag/wallabag/blob/master/README.md)). No media download and no screenshots (not mentioned in the README).

### Omnivore
- Repo: [omnivore-app/omnivore](https://github.com/omnivore-app/omnivore). AGPL-3.0. README: "The Cloud application was deprecated in November of 2024", and it is now "a completely Self-Hosted application" ([README](https://github.com/omnivore-app/omnivore/blob/main/README.md)).
- Still active as a community self-host project: pushed 2026-10-25 (mostly dependabot plus some human fixes), latest tag `android-0.230.0` on 2026-10-14.
- Native Kotlin Android app with a `text/*` share filter ([manifest](https://github.com/omnivore-app/omnivore/blob/main/mobile/android/Omnivore/app/src/main/AndroidManifest.xml)). Readability-based article saving, highlights, labels, search. No media capture.

### Shiori
- [go-shiori/shiori](https://github.com/go-shiori/shiori): MIT, Go, pushed 2026-10-24, last release v1.8.0 on 2025-09-26. It "will parse the readable content and create an offline archive of the webpage" ([README](https://github.com/go-shiori/shiori/blob/master/README.md)).
- Android client [DesarrolloAntonio/Shiori-Android-Client](https://github.com/DesarrolloAntonio/Shiori-Android-Client): Apache-2.0, Kotlin, v1.52.03 on 2026-09-24. Share filter is `text/plain` only ([manifest](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/master/presentation/src/main/AndroidManifest.xml)).

### Readeck
- [codeberg.org/readeck/readeck](https://codeberg.org/readeck/readeck): AGPL-3.0 (README badge), 0.23.5 on 2026-10-23. It saves readable text and images into the instance at save time and "detects when a page is an image or a video and adapts its process accordingly". However, "with the exception of videos, not a single request is made… to an external website", which implies videos are embedded, not downloaded ([README](https://codeberg.org/readeck/readeck/src/branch/main/README.md)). Labels, highlights, collections (saved searches), full-text search, EPUB/OPDS export.
- Android clients:
  - An official Play app `org.readeck.apps.android` is linked from [readeck.org/en/apps](https://readeck.org/en/apps). **[unverified]** where its source lives.
  - [jensomato/ReadeckApp](https://github.com/jensomato/ReadeckApp): GPL-3.0, Kotlin, on F-Droid as `de.readeckapp`, `text/plain` share ([manifest](https://github.com/jensomato/ReadeckApp/blob/develop/app/src/main/AndroidManifest.xml)).
  - [gollyhatch/eckard](https://codeberg.org/gollyhatch/eckard) on Codeberg.

### linkding
- [sissbruecker/linkding](https://github.com/sissbruecker/linkding): MIT, Python/Django, v1.47.0 on 2026-09-13. Archives "either as local HTML file or on Internet Archive" and is "Installable as a PWA" ([README](https://github.com/sissbruecker/linkding/blob/master/README.md)).
- The PWA manifest declares a `share_target` (GET) that maps both `url` and `text` to `url`, and `title` to `title` ([manifest.py](https://github.com/sissbruecker/linkding/blob/master/bookmarks/views/manifest.py)).
- Android clients listed on the [community page](https://github.com/sissbruecker/linkding/blob/master/docs/src/content/docs/community.md): LinkBuddy (React Native), Linkdy (Flutter), Linklater (Kotlin), Pinkt (Kotlin). The page also lists `linkding-archiver`, which integrates SingleFile and Tube Archivist for videos. Per-client share behavior is **[unverified]**.

### ArchiveBox (archiver rather than read-later, but the most complete capture)
- [ArchiveBox/ArchiveBox](https://github.com/ArchiveBox/ArchiveBox): MIT, Python, about 28.9k stars, v0.9.73 on 2026-10-06.
- Output per URL: "original HTML+CSS+JS, singlefile HTML, screenshot PNG, PDF, WARC, title, article text, favicon, headers". For social media/news: "post content TXT, comments, title, author, images". For YouTube/SoundCloud: "MP3/MP4s, subtitles, metadata, thumbnail" ([README](https://github.com/ArchiveBox/ArchiveBox/blob/dev/README.md)).
- Extractors live in [ArchiveBox/abx-plugins](https://github.com/ArchiveBox/abx-plugins) (MIT): ytdlp, gallerydl, forumdl, papersdl, singlefile, chrome_mhtml, defuddle and more. Default ytdlp args include `--write-description --write-info-json --write-thumbnail --write-sub --write-auto-subs --convert-subs=srt --add-metadata` ([config.json](https://github.com/ArchiveBox/abx-plugins/blob/main/abx_plugins/plugins/ytdlp/config.json)). gallerydl defaults to `--write-metadata --write-info-json` and takes a cookies file ([config.json](https://github.com/ArchiveBox/abx-plugins/blob/main/abx_plugins/plugins/gallerydl/config.json)).
- Android client [ArchiveBox/android-archivebox](https://github.com/ArchiveBox/android-archivebox): MIT, Kotlin/Compose, created 2026-09-20, 8 stars, v1.13.13 on 2026-10-25. `SEND`/`SEND_MULTIPLE` for `text/plain` and `text/uri-list`, tagging at share time, search; "A successful submission means the server accepted the archive request" ([README](https://github.com/ArchiveBox/android-archivebox/blob/main/README.md)). Shared files and images are not accepted.

---

## 2. Note / knowledge apps with share intents

These store **whatever the share carried** (text and/or files) and fetch nothing behind a URL.

| App | Android share filters (from manifest) | What the handler does |
|---|---|---|
| **Logseq** ([logseq/logseq](https://github.com/logseq/logseq), AGPL-3.0, Capacitor/ClojureScript, 2.0.2 on 2026-10-07) | `SEND` `text/plain`, `image/*`, `application/*`, `video/*` ([manifest](https://github.com/logseq/logseq/blob/master/android/app/src/main/AndroidManifest.xml)) | Text goes to quick-capture with `:title :url`. A regex splits a "highlight + trailing URL" into `:content` and `:url`. Media and app files are embedded as assets ([intent.cljs](https://github.com/logseq/logseq/blob/master/src/main/frontend/mobile/intent.cljs)). Graph view exists. README says the new DB-version mobile app is "iOS, with Android coming soon" ([README](https://github.com/logseq/logseq/blob/master/README.md)); the current Android app status is **[unverified]**. |
| **Obsidian** | n/a | **Not open source.** "Obsidian is not open source software and this repo DOES NOT contain the source code" ([obsidian-releases](https://github.com/obsidianmd/obsidian-releases/blob/master/README.md)). Excluded. |
| **Joplin** ([laurent22/joplin](https://github.com/laurent22/joplin), AGPL-3.0-or-later per [LICENSE](https://github.com/laurent22/joplin/blob/dev/LICENSE), React Native, v3.7.21 on 2026-09-25) | `SEND` and `SEND_MULTIPLE` `*/*` ([manifest](https://github.com/laurent22/joplin/blob/dev/packages/app-mobile/android/app/src/main/AndroidManifest.xml)) | Reads `EXTRA_TEXT`, title from `EXTRA_SUBJECT` falling back to `EXTRA_TITLE`, and one or many `EXTRA_STREAM` files as resources, then creates a note ([SharePackage.java](https://github.com/laurent22/joplin/blob/dev/packages/app-mobile/android/app/src/main/java/net/cozic/joplin/share/SharePackage.java), [shareHandler.ts](https://github.com/laurent22/joplin/blob/dev/packages/app-mobile/utils/shareHandler.ts)). The most complete *intent-field* capture of the note apps. No graph. |
| **Memos** ([usememos/memos](https://github.com/usememos/memos), MIT, Go, v0.31.0 on 2026-09-19) | PWA manifest has **no** `share_target` ([site.webmanifest](https://github.com/usememos/memos/blob/main/web/public/site.webmanifest)) | Server only. |
| **MoeMemos Android** ([mudkipme/MoeMemosAndroid](https://github.com/mudkipme/MoeMemosAndroid), GPL-3.0, Kotlin, 2.0.6 on 2026-10-07) | `SEND` `image/*`, `text/plain`; `SEND_MULTIPLE` `image/*` | Memo with text and images. The iOS sibling [mudkipme/MoeMemos](https://github.com/mudkipme/MoeMemos) (MPL-2.0) supports local-only storage, and it supports Memos 0.21 and 0.27–0.31 ([README](https://github.com/mudkipme/MoeMemos/blob/main/README.md)). |
| **Anytype** ([anyproto/anytype-kotlin](https://github.com/anyproto/anytype-kotlin), **"Any Source Available License 1.0"**, not OSI open source ([LICENSE.md](https://github.com/anyproto/anytype-kotlin/blob/main/LICENSE.md)), Kotlin, 0.50.4 on 2026-09-28) | `SEND`/`SEND_MULTIPLE` text, image, video, `application/*` ([manifest](https://github.com/anyproto/anytype-kotlin/blob/main/app/src/main/AndroidManifest.xml)) | `text/plain` is classified as `Url` when the *entire* text is a valid URL, otherwise `Text`. Media and files are parsed single or multiple. `EXTRA_SUBJECT` is not read ([IntentToSharedContentConverter.kt](https://github.com/anyproto/anytype-kotlin/blob/main/presentation/src/main/java/com/anytypeio/anytype/presentation/sharing/IntentToSharedContentConverter.kt)). Local-first with P2P sync and a graph view **[graph view not re-verified]**. |
| **AppFlowy** ([AppFlowy-IO/AppFlowy](https://github.com/AppFlowy-IO/AppFlowy), AGPL-3.0, Flutter, 0.14.11 on 2026-10-26) | **No `SEND` filter** in the Android manifest (only MAIN and VIEW) ([manifest](https://github.com/AppFlowy-IO/AppFlowy/blob/main/frontend/appflowy_flutter/android/app/src/main/AndroidManifest.xml)) | Not a share target. |
| **SiYuan** ([siyuan-note/siyuan](https://github.com/siyuan-note/siyuan) AGPL-3.0; Android shell [siyuan-note/siyuan-android](https://github.com/siyuan-note/siyuan-android) AGPL-3.0, Java, v3.8.8 on 2026-10-20) | `SEND`/`SEND_MULTIPLE` text, image, video, audio, `application/*` ([manifest](https://github.com/siyuan-note/siyuan-android/blob/main/app/src/main/AndroidManifest.xml)) | Writes a timestamped "shorthand" `.md` into an inbox directory. `EXTRA_TEXT` is appended, and streams are copied into `assets/` and linked ([ShortcutActivity.java](https://github.com/siyuan-note/siyuan-android/blob/main/app/src/main/java/org/b3log/siyuan/ShortcutActivity.java)). Graph view in core. |
| **Trilium** ([TriliumNext/Trilium](https://github.com/TriliumNext/Trilium), AGPL-3.0, v0.106.1 on 2026-10-23; old `zadam/trilium` and `TriliumNext/Notes` are archived) | No official Android app. [FliegendeWurst/TriliumDroid](https://github.com/FliegendeWurst/TriliumDroid) (AGPL-3.0, Kotlin) shares `text/plain` and `text/html` | Creates a note. Trilium has relation maps and link maps **[not re-verified]**. |
| **Markor** ([gsantner/markor](https://github.com/gsantner/markor), Apache-2.0 ([LICENSE.txt](https://github.com/gsantner/markor/blob/master/LICENSE.txt)), Java, v2026.10.0 on 2026-10-25) | `SEND` `text/plain`, `text/*`, `*/*`, plus Gmail `AUTO_SEND` and Google `CREATE_NOTE` ([manifest](https://github.com/gsantner/markor/blob/master/app/src/main/AndroidManifest.xml)) | Shares into a plain Markdown/todo.txt file on device. Local-only, no fetch. |
| **Blinko** ([blinkospace/blinko](https://github.com/blinkospace/blinko), GPL-3.0, Tauri, 1.8.8 on 2026-06-20) | `SEND` `text/*`, `image/*`, `*/*` ([manifest](https://github.com/blinkospace/blinko/blob/main/app/src-tauri/gen/android/app/src/main/AndroidManifest.xml)) | AI card notes with RAG natural-language search, self-hosted ([README](https://github.com/blinkospace/blinko/blob/main/README.md)). No media fetch. |

---

## 3. "Save everything" / universal capture apps on Android

| Project | What it is | Share filters / capture | Notes |
|---|---|---|---|
| **MemGallery** ([Past-da-king/MemGallery](https://github.com/Past-da-king/MemGallery)) | "Smart, Local-First Memory Bank", Kotlin, MIT, created 2025-11-20, last push 2026-08-23, 103 stars | `SEND` `image/*`, `text/plain`, `text/calendar` ([manifest](https://github.com/Past-da-king/MemGallery/blob/master/MemGallery/app/src/main/AndroidManifest.xml)) | Screenshot auto-indexing, URL summaries, audio transcription, AI tags and action extraction, semantic search, local or BYO-LLM ([README](https://github.com/Past-da-king/MemGallery/blob/master/README.md)). No documented media download for social links; no graph. |
| **Cobalt (save-for-later)** ([hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt), not the imputnet downloader) | "offline-first personal knowledge base and universal capture suite for Android", Kotlin, AGPL-3.0, created 2026-07-12, 9 stars, F-Droid | `SEND` `text/plain`, `image/*`, `video/*` ([manifest](https://github.com/hanan-bhatti/cobalt/blob/main/app/src/main/AndroidManifest.xml)) | Edge "SideDock", Gemini OCR (Play flavour), voice memos ([README](https://github.com/hanan-bhatti/cobalt/blob/main/README.md)). No link-media fetch documented. |
| **Memex (hasmeni)** ([hasmeni/memex](https://github.com/hasmeni/memex)) | Self-hosted link collection "rendered as an animated mind map" (D3 force graph), MIT per README badge (the API reports no license file), created 2026-07-06, last push 2026-07-12, 7 stars | Android companion: `SEND` `text/plain` ([manifest](https://github.com/hasmeni/memex/blob/main/memex-android/app/src/main/AndroidManifest.xml)) | Only open-source project found that pairs an Android share target with a **mind-map view**, but for links only. No content capture ([README](https://github.com/hasmeni/memex/blob/main/README.md)). |
| **Shaarlier** ([dimtion/Shaarlier](https://github.com/dimtion/Shaarlier)) | Share-to-Shaarli, GPL-3.0 | URL | Last push 2023-06-06; stale. |
| **KernMind** ([clemensjl/kernmind](https://github.com/clemensjl/kernmind)) | "open-source alternative to mymind.com", MIT, created 2026-08-18, 0 stars | Web, extension, Windows; **no Android app** ([README](https://github.com/clemensjl/kernmind/blob/main/README.md)) | RAG "Ask your Mind" chat. Two days of commits; treat as abandoned. |

No open-source "Raindrop/mymind-class" Android app with server-grade capture was found beyond Karakeep and Linkwarden.

---

## 4. Media archivers that take a shared link and download real media

| Project | License / stack / status | Share filter | What it pulls |
|---|---|---|---|
| **yt-dlp** ([yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)) | Unlicense, Python, 2026.10.21 | n/a (engine) | Media plus optional `--write-info-json`, `--write-description`, `--write-comments`, `--write-thumbnail`/`--write-all-thumbnails`, `--write-subs`/`--write-auto-subs`, `--cookies-from-browser` ([README](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)). Sites include Instagram, instagram:story, TikTok, twitter, Reddit, facebook:reel, Pinterest, Bluesky. `instagram:user` and several `tiktok:*` extractors are marked "Currently broken" ([supportedsites.md](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)). TikTok info includes `description`, `uploader`, `comment_count` and `track` ([tiktok.py](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/tiktok.py)). Full YouTube support now needs `yt-dlp-ejs` plus a JS runtime (deno recommended; node, bun or QuickJS also work) ([README §dependencies](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)). |
| **gallery-dl** ([mikf/gallery-dl](https://github.com/mikf/gallery-dl)) | GPL-2.0, Python, v1.32.18 on 2026-10-25 | n/a (engine) | Images and carousels plus `--write-metadata`, `--write-info-json`, `--write-tags`, `--cookies-from-browser`, `-j/--dump-json` ([options.md](https://github.com/mikf/gallery-dl/blob/master/docs/options.md)). Covers Instagram (posts, reels, stories, highlights, saved), TikTok (posts, stories, saved), Twitter/X (tweets, bookmarks, likes, quotes), Reddit, Pinterest, Bluesky, Facebook, Tumblr. The auth column lists **Cookies** for Instagram, TikTok, Twitter, Pinterest and Facebook, and **OAuth** for Reddit and Tumblr ([supportedsites.md](https://github.com/mikf/gallery-dl/blob/master/docs/supportedsites.md)). |
| **instaloader** ([instaloader/instaloader](https://github.com/instaloader/instaloader)) | MIT, Python, v4.15.5 on 2026-10-24 | n/a (CLI) | Instagram pictures and videos "along with their captions and other metadata", plus "comments, geotags and captions", saved media, stories ([README](https://github.com/instaloader/instaloader/blob/master/README.rst)). |
| **youtubedl-android** ([yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)) | GPL-3.0, Kotlin library; last push 2025-11-16; published as `io.github.junkfood02.youtubedl-android` 0.18.1 | n/a | Bundles yt-dlp plus Python ("python 3.8" per the README, likely stale) and optional ffmpeg/aria2c ([README](https://github.com/yausername/youtubedl-android/blob/master/README.md)). The code wires in a bundled **QuickJS** path ([YoutubeDL.kt](https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt)). Scoped storage limits direct writes to `Download/` and `Documents/`. |
| **Seal** ([JunkFood02/Seal](https://github.com/JunkFood02/Seal)) | GPL-3.0, Kotlin/Compose, about 30k stars. **Last stable v1.13.1 2024-10-16, last code commit 2025-04-06**; since then only monthly sponsor-README commits (dormant) | `SEND` `text/plain` | Extracts the first URL from `EXTRA_TEXT` ([QuickDownloadActivity.kt](https://github.com/JunkFood02/Seal/blob/main/app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt)). Media, embedded metadata and thumbnail, subtitles, custom yt-dlp command templates ([README](https://github.com/JunkFood02/Seal/blob/main/README.md)). Fork [MaheshTechnicals/Sealplus](https://github.com/MaheshTechnicals/Sealplus) is GPL-3.0, active (push 2026-10-14). |
| **YTDLnis** ([deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis)) | GPL-3.0, Kotlin, v1.9.1.1 on 2026-10-19, active | Several `SEND` `text/plain` and `application/txt` | yt-dlp front-end with cookies (in-app login), custom commands, built-in terminal, embedded metadata and subtitles. Pluggable **Python, Node/Deno, FFmpeg, aria2c** packages ([README](https://github.com/deniscerri/ytdlnis/blob/main/README.md), [Deno.kt](https://github.com/deniscerri/ytdlnis/blob/main/app/src/main/java/com/deniscerri/ytdl/core/packages/Deno.kt)). Custom commands make `--write-info-json` possible, but it is not a default. |
| **NewPipe** ([TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)) | GPL-3.0, Java/Kotlin, v0.29.1 on 2026-08-15 | `SEND` `text/plain`, first URL via `UrlFinder.firstUrlFromInput` ([RouterActivity.java](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/RouterActivity.java)) | Only YouTube/YT Music, PeerTube, Bandcamp, SoundCloud ([README](https://github.com/TeamNewPipe/NewPipe/blob/dev/README.md)). No Instagram, TikTok or X. |
| **Sesame** ([Abdull0100/sesame](https://github.com/Abdull0100/sesame)) | GPL-2.0, Kotlin plus **Chaquopy** (Python 3.11, pip `gallery-dl==1.32.1`, `yt-dlp==2026.7.4`) ([build.gradle.kts](https://github.com/Abdull0100/sesame/blob/main/app/build.gradle.kts)). Created and last pushed 2026-08-02, 0 stars, "no stable release" | `SEND` `text/plain` plus Process Text | Auto-routes between gallery-dl and yt-dlp; "Optional metadata, thumbnails, subtitles, and comments"; "Best-effort Instagram comment archive"; `cookies.txt` import; no FFmpeg (no merging) ([README](https://github.com/Abdull0100/sesame/blob/main/README.md)). **Most complete on-device extractor found.** |
| **Flipper the Ripper** ([pepperonas/flipper-the-ripper](https://github.com/pepperonas/flipper-the-ripper)) | MIT, Kotlin, 5 stars, push 2026-10-18 | Direct-share target | YouTube, Instagram, TikTok, Facebook, X, Dailymotion; WebView Instagram sign-in that keeps only the session cookie; auto-updating yt-dlp. README observes that "Apps that draw their own in-app share sheet (TikTok among them) show a fixed set of destinations plus a *More* entry" ([README](https://github.com/pepperonas/flipper-the-ripper/blob/main/README.md)). |
| **ShareDL** ([ma-tcha/sharedl](https://github.com/ma-tcha/sharedl)) | No license, Kotlin, created 2026-10-09 | `SEND` | Instagram only, WebView login, re-shares the file. Uses `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2` ([README](https://github.com/ma-tcha/sharedl/blob/master/README.md)). |
| **Sharewerk** ([Azentrius/sharewerk](https://github.com/Azentrius/sharewerk)) | Apache-2.0, Python server plus PWA, created 2026-10-19 | PWA Web Share Target (Chrome on Android; iOS unsupported) | Instagram/Facebook post → all images (JPEG ≤1568 px), video key frames plus a faster-whisper transcript, `post.txt` (source, author, date, caption). Cookies from a secondary account via a Chrome extension or `cookies.txt`; 20 s pause between Instagram jobs ([README](https://github.com/Azentrius/sharewerk/blob/main/README.md)). |
| **cobalt** ([imputnet/cobalt](https://github.com/imputnet/cobalt)) | AGPL-3.0, Node API plus Svelte web; last push 2026-04-06, no GitHub releases | Web/API (third-party Android wrappers **[unverified]**) | Media only. The services table marks `metadata` as "➖ unreasonable/impossible" for Instagram, Twitter/X, Pinterest, Bluesky and Facebook, and "❌ not supported" for TikTok and Reddit ([api/README.md](https://github.com/imputnet/cobalt/blob/main/api/README.md)). The API returns a tunnel/redirect URL and filename ([docs/api.md](https://github.com/imputnet/cobalt/blob/main/docs/api.md)). **Not suitable for caption/author capture.** |

---

## 5. AI / semantic-search / graph-oriented capture tools

| Project | Status | Mobile share? | Relevance |
|---|---|---|---|
| **Karakeep** | Active (see §1) | Yes | AI tags/summaries, OCR, semantic search (experimental). No graph. |
| **Linkwarden** | Active | Yes (text only) | "Local AI Tagging", full-text search. No graph. |
| **Khoj** ([khoj-ai/khoj](https://github.com/khoj-ai/khoj), AGPL-3.0) | Last release 2.0.0-beta.28 on 2026-03-26; last commit 2026-08-02; README now promotes their new project "Pipali" | Android app is a **Trusted Web Activity** with no `SEND` filter ([manifest](https://github.com/khoj-ai/khoj/blob/master/src/interface/android/app/src/main/AndroidManifest.xml)); WhatsApp interface per README | Semantic search and chat over docs. Not a capture tool. Slowing. |
| **Reor** ([reorproject/reor](https://github.com/reorproject/reor), AGPL-3.0) | **Archived**, last release v-0.2.32 on 2025-04-05 | Desktop only | Auto-linked notes via embeddings. Dead. |
| **Fabric** ([danielmiessler/Fabric](https://github.com/danielmiessler/Fabric), MIT, Go) | Very active (v1.4.555 on 2026-10-25) | CLI only | Prompt "patterns"; `fabric -y <url> --transcript` pulls YouTube transcripts ([README](https://github.com/danielmiessler/Fabric/blob/main/README.md)). Useful as a post-processing idea, not capture. |
| **Supermemory** ([supermemoryai/supermemory](https://github.com/supermemoryai/supermemory), MIT) | Active | No mobile app in `apps/` (`docs mcp memory-graph-playground sdk-playground web`) | Self-hostable memory API with an "embedded Supermemory graph engine", hybrid search ([README](https://github.com/supermemoryai/supermemory/blob/main/README.md)). Candidate *backend* for the graph phase, not a capture client. |
| **Blinko** | Active | Yes | RAG search over notes. |
| **MemGallery** | Active-ish | Yes | On-device AI indexing and semantic search. |
| **hasmeni/memex** | Tiny | Yes (URL) | D3 mind-map of links. |
| **Logseq / SiYuan / Trilium / Anytype** | Active | Yes (raw intent only) | Graph views over *notes*, not auto-built from captures. |
| **WorldBrain/Memex** ([repo](https://github.com/WorldBrain/Memex)) | No license detected by GitHub; last release 2.2.0 in 2020; last push 2025-12-02 | Browser extension | Effectively inactive; excluded. |

---

## 6. Technical constraints

### 6.1 What the Android share sheet delivers
- **Actions.** `ACTION_SEND` carries one item and `ACTION_SEND_MULTIPLE` carries several. "get*Extra can have either a EXTRA_TEXT or EXTRA_STREAM field"; "If using EXTRA_TEXT, the MIME type should be 'text/plain'; otherwise it should be the MIME type of the data in EXTRA_STREAM". Optional `EXTRA_HTML_TEXT` (which still requires `EXTRA_TEXT`) and `EXTRA_EMAIL/CC/BCC/SUBJECT`. Since API 16 the data may also be in `ClipData`, and "If you don't set a ClipData, it will be copied there for you when calling Context.startActivity" ([Intent reference](https://developer.android.com/reference/android/content/Intent)).
- **Mixed types.** For `SEND_MULTIPLE`, "Multiple types are supported, and receivers should handle mixed types… The right way for the receiver to check them is to use the content resolver on each URI"; the intent type may be `image/*` or `*/*` (same page).
- **Extras.**
  - `EXTRA_TEXT` "may be a styled CharSequence, so you must use Bundle.getCharSequence()".
  - `EXTRA_STREAM` is "A content: URI holding a stream of data".
  - `EXTRA_SUBJECT` is the "subject line".
  - `EXTRA_TITLE` is formally the chooser dialog title, but Chrome also sets it on text shares (below).
- **Receiver guidance** ([Receive simple data](https://developer.android.com/training/sharing/receive)):
  - Accept `ACTION_SEND`/`SEND_MULTIPLE` with the "widest possible range of MIME types".
  - Avoid `*/*` "unless your app is fully capable of handling any type".
  - "the wrong MIME type might be set, or an image being sent might be extremely large".
  - Do I/O off the main thread.
  - Let users confirm and edit the shared content.
- **URI grant lifetime.** Temporary URI permissions "last until the activity that receives them is finished" ([content provider basics](https://developer.android.com/guide/topics/providers/content-provider-basics)). `FLAG_GRANT_READ_URI_PERMISSION` covers the data URI and all `ClipData` URIs ([Intent](https://developer.android.com/reference/android/content/Intent)). → yearn must **copy every `EXTRA_STREAM`/ClipData URI to app storage immediately** inside the share activity, before handing work to a background worker.
- **Direct Share targets** (sharing shortcuts) can put yearn in the top row of the system sheet ([Receive simple data](https://developer.android.com/training/sharing/receive)). Apps with their own in-app share UI (TikTok per the Flipper README) put third-party targets behind a "More" entry.
- **PWA alternative.** The Web App Manifest `share_target` supports GET params (`title`, `text`, `url`) and POST `multipart/form-data` with `files` ([MDN share_target](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest/Reference/share_target)). linkding and Sharewerk use it. Sharewerk notes Safari does not implement it. A PWA gets no `getReferrer`, no `ClipData` and no styled text.

### 6.2 What common source apps send
- **Chrome (verified from source)** ([ShareHelper.java](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/components/browser_ui/share/android/java/src/org/chromium/components/browser_ui/share/ShareHelper.java)):
  - Page share: `type=text/plain`, `EXTRA_TEXT` = `params.getTextAndUrl()` (selected text plus URL when sharing a highlight), `EXTRA_SUBJECT` = title (if it differs from the text), `EXTRA_TITLE` = title. An optional preview image goes in `ClipData`.
  - Image share: `EXTRA_STREAM` image with `FLAG_GRANT_READ_URI_PERMISSION`, `EXTRA_TEXT` text+URL, and alt text in `android.intent.extra.STREAM_ALT_TEXT`.
  - Offline-page share: `multipart/related` stream (MHTML) plus `EXTRA_SUBJECT`.
  - Multiple files: `SEND_MULTIPLE`.
  - Also sets a Chrome-specific `ShareParams.EXTRA_SHARE_ORIGIN`.
- **Instagram, TikTok, YouTube, X, Reddit, WhatsApp: [unverified]** (closed source, no public documentation of their outgoing intents). Indirect evidence:
  - Logseq ([intent.cljs](https://github.com/logseq/logseq/blob/master/src/main/frontend/mobile/intent.cljs)), NewPipe ([RouterActivity.java](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/RouterActivity.java)), Seal ([QuickDownloadActivity.kt](https://github.com/JunkFood02/Seal/blob/main/app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt)) and expo-share-intent ([utils.ts](https://github.com/achorein/expo-share-intent/blob/main/src/utils.ts)) all extract the first URL from `EXTRA_TEXT`. Shared text is therefore commonly "some words + URL", not a bare URL.
  - yt-dlp has an `InstagramIOS` extractor for `instagram://` URLs ([supportedsites.md](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)).
  - An unmerged expo-share-intent PR states that Chrome and Firefox put the title in `EXTRA_SUBJECT` ([PR #229](https://github.com/achorein/expo-share-intent/pull/229)); this is consistent with the Chromium source above.
  - Gallery and Files apps send `EXTRA_STREAM` content URIs with an image or video MIME type (standard `ACTION_SEND` contract above). Exact behavior per app is **[unverified]**.
  - **Recommendation:** yearn's first build should log the *entire* raw intent (action, type, every extra key and its class, every ClipData item, flags, `getReferrer()`) to build an empirical fixture table per source app.

### 6.3 Can the receiver learn which app shared?
- `getReferrer()` (API 22) returns `EXTRA_REFERRER` "as-is" if present, otherwise "an android-app: referrer URI containing the package name that started the Intent", or null. "Note that this is not a security feature -- you can not trust the referrer information, applications can spoof it." ([Activity](https://developer.android.com/reference/android/app/Activity))
- `getLaunchedFromPackage()` / `getLaunchedFromUid()` (API 34) return the launcher's identity only if the launcher called `ActivityOptions.setShareIdentityEnabled(true)`, has the same UID, or is platform-signed (plus result-based launches from a newer release). Otherwise they return null or `INVALID_UID`. `getInitialCaller()` (API 35) returns a `ComponentCaller` (same page). Ordinary apps sharing through a chooser will not opt in, so **expect null**.
- The system chooser (IntentResolver) launches the picked target via `activity.startActivityAsCaller(...)` ([DisplayResolveInfo.java](https://android.googlesource.com/platform/packages/modules/IntentResolver/+/refs/heads/main/java/src/com/android/intentresolver/chooser/DisplayResolveInfo.java), called from [ChooserActivity.java](https://android.googlesource.com/platform/packages/modules/IntentResolver/+/refs/heads/main/java/src/com/android/intentresolver/ChooserActivity.java)). The launch is therefore attributed to the *original* app, and `getReferrer()` should usually name the source app for system-sheet shares and for in-app share sheets that start the target directly. Behavior on OEM sheets (Samsung, MIUI) is **[unverified]**.
- `getCallingPackage()` is only non-null for `startActivityForResult` launches, which shares are not.
- **Practical approach:** record `getReferrer()` as `source_app_hint`, and cross-check it against the URL host (instagram.com → Instagram) and app-specific extras such as Chrome's `EXTRA_SHARE_ORIGIN`.

### 6.4 Getting the raw media and caption behind a link
- **Coverage.** yt-dlp and gallery-dl together cover Instagram (posts, reels, stories), TikTok, X, Reddit, Pinterest, Bluesky and Facebook (tables in §4). instaloader is Instagram-specific but the richest for captions, comments and geotags.
- **Metadata richness.**
  - yt-dlp `--write-info-json` gives title, description (caption), uploader, timestamps, counts, tags and formats; `--write-comments` adds comments; subs and auto-subs are available.
  - gallery-dl `--write-metadata` writes per-file JSON.
  - cobalt deliberately returns no metadata for social sites (§4).
- **Login and cookies.**
  - yt-dlp's Instagram extractor detects login redirects ("Main webpage is locked behind the login page"), reads account cookies, warns when "The provided Instagram account cookies are no longer valid", and calls `raise_login_required` ([instagram.py](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/instagram.py)). TikTok also has `raise_login_required` paths ([tiktok.py](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/tiktok.py)).
  - Instagram story-cookie issues were still being filed in Oct 2026 ([#17903](https://github.com/yt-dlp/yt-dlp/issues/17903), [#17755](https://github.com/yt-dlp/yt-dlp/issues/17755)).
  - Sharewerk: "Instagram shows almost nothing to logged-out visitors". Use a secondary account, because "accounts used this way can be rate-limited or blocked" ([README](https://github.com/Azentrius/sharewerk/blob/main/README.md)).
  - On-device patterns seen in the wild: a WebView login that keeps only the session cookie (Flipper, ShareDL), and `cookies.txt` import (Sesame, YTDLnis).
- **YouTube** now needs `yt-dlp-ejs` plus a JS runtime for full support. Without one, the `web` and `tv_samsung` clients are omitted ([README](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)). On Android, youtubedl-android bundles QuickJS and YTDLnis ships Deno/Node packages.
- **Running Python extractors on Android.** Three proven routes:
  1. **youtubedl-android**: bundled CPython plus yt-dlp binary, GPL-3.0, Maven `io.github.junkfood02.youtubedl-android`, used by Seal. It has no gallery-dl.
  2. **Chaquopy** ([chaquo/chaquopy](https://github.com/chaquo/chaquopy), MIT): pip-installs both gallery-dl and yt-dlp into the APK (Sesame).
  3. **YTDLnis-style downloadable packages** ([ytdlnis-packages](https://github.com/deniscerri/ytdlnis-packages)) to update Python and runtimes independently of the APK.

  All three need self-update of yt-dlp and gallery-dl, because extractors break within weeks. Flipper refreshes yt-dlp on every share.
- **oEmbed and OpenGraph limits (live-probed 2026-10-26 from this machine unless noted):**
  - YouTube `/oembed`: no auth; returns `title, author_name, author_url, thumbnail_url, …`.
  - X `publish.twitter.com/oembed` (301 to `publish.x.com`): no auth; returns `author_name, author_url`, and the tweet text inside `html`. No media URLs.
  - Instagram `instagram_oembed`: Graph API v26.0, access token required (error 190 "Invalid OAuth 2.0 Access Token"). Documented fields are `html, provider_name, provider_url, type, version, width`, with `hidecaption`/`omitscript` params. "Posts on private, inactive, and age-restricted Instagram accounts are not supported", and the limit is 1,000 requests per hour ([reference](https://developers.facebook.com/docs/graph-api/reference/instagram-oembed/), [overview](https://developers.facebook.com/docs/instagram-platform/oembed)). Any caption only arrives embedded in `html`.
  - TikTok `tiktok.com/oembed`: **302 to `/in/about` from this (Indian) network**; TikTok's doc page returned 503. Field list **[unverified]**.
  - Reddit `www.reddit.com/r/…/top.json` unauthenticated returned **403**, and `reddit.com/oembed` returned "not found" for a bogus post. API terms and OAuth requirements are **[unverified]** here.
  - OpenGraph tags on logged-out Instagram, TikTok and X pages are **[unverified]**.
- **On-device vs server-side.**
  - *On-device* (Sesame, Seal, YTDLnis, Flipper): no server and private, but the APK is large (CPython plus extractors; Sesame skips FFmpeg to save size). Cookies live on the phone, extraction competes with battery and Doze, and extractor updates must ship to each device.
  - *Server-side* (Karakeep, ArchiveBox, Sharewerk): headless Chrome, FFmpeg, Whisper and OCR are all available, updates happen in one place, and the share completes instantly with work queued. The costs: a reachable server (Tailscale or a reverse proxy), cookies stored server-side, and the server IP's geography. TikTok is blocked from India, so a non-Indian egress or VPN would be needed.
  - A hybrid is plausible: copy streams and log the intent on-device, then extract later on a server. None of the projects found does both.

---

## 7. Comparison table

| project | license | stack | captures from share | fetches real media? | local / self-hosted | search / graph | status 2026 |
|---|---|---|---|---|---|---|---|
| Karakeep | AGPL-3.0 | TS server; Expo RN app | first URL in text, or text, or 1st file (text/*, image/*, pdf) | optional yt-dlp video file only (no info-json); screenshot/PDF/monolith | self-hosted | Meili FTS, semantic (exp.), AI tags, lists, rules; no graph | very active (v0.34.1 2026-10-22) |
| Linkwarden | AGPL-3.0 | TS server; Expo RN app | text/* (URL) | no; screenshot, PDF, monolith, readable, Wayback | self-hosted | FTS, AI tags, collections; no graph | active (v2.16.3 2026-09-09) |
| wallabag + android-app | MIT / GPL-3.0 | PHP; Java | text/plain URL | no; article text | self-hosted | FTS, tags | active (app 2.6.0 2026-06) |
| Omnivore | AGPL-3.0 | TS; Kotlin | text/* URL | no; readability | self-hosted only (cloud shut Nov 2024) | search, labels | community maintenance (android-0.230.0 2026-10-14) |
| Shiori + Android client | MIT / Apache-2.0 | Go; Kotlin | text/plain URL | no; offline page archive | self-hosted | search, tags | server v1.8.0 2025-09; client 2026-09 |
| Readeck + ReadeckApp | AGPL-3.0 / GPL-3.0 | Go; Kotlin | text/plain URL | images stored; videos not downloaded | self-hosted | FTS, labels, collections | active (0.23.5 2026-10-23) |
| linkding (+clients) | MIT | Python; PWA share_target; RN/Flutter/Kotlin clients | url/text→url, title | no; HTML snapshot / IA | self-hosted | search, tags | active (v1.47.0 2026-09-13) |
| ArchiveBox + android-archivebox | MIT / MIT | Python; Kotlin/Compose | text/plain, uri-list (URLs) + tags | **yes**: yt-dlp (+info-json, description, subs, thumbnail) and gallery-dl (+metadata) by default; plus singlefile/PDF/PNG/WARC | self-hosted | FTS; no graph | very active (server v0.9.73; app beta v1.13.13 2026-10-25) |
| Logseq | AGPL-3.0 | Capacitor/CLJS | text (title, url, highlight), image, video, app files | no | local files/DB | search + graph view | active (2.0.2); Android DB app status [unverified] |
| Obsidian | proprietary | — | — | — | — | — | not open source |
| Joplin | AGPL-3.0+ | React Native | EXTRA_TEXT + SUBJECT/TITLE + all streams (*/*) | no | local + sync server/E2EE | FTS; no graph | active (v3.7.21) |
| Memos / MoeMemosAndroid | MIT / GPL-3.0 | Go; Kotlin | (server: none) / text, images | no | self-hosted or local (MoeMemos) | search, tags | active |
| Anytype | Any Source Available 1.0 (not OSI) | Kotlin + Go | full-URL text→Url, else Text; images/video/files | no | local-first P2P | search, graph [not re-verified] | active (0.50.4) |
| AppFlowy | AGPL-3.0 | Flutter | **not a share target** | — | local/cloud | — | active |
| SiYuan | AGPL-3.0 | Java shell + Go kernel | text + all streams → inbox .md + assets | no | local + optional sync | FTS, graph | active (v3.8.8) |
| Trilium / TriliumDroid | AGPL-3.0 | TS; Kotlin | text/plain, text/html | no | self-hosted | search, relation maps [not re-verified] | active |
| Markor | Apache-2.0 | Java | text/*, */* into a file | no | local only | file search | active (v2026.10.0) |
| Blinko | GPL-3.0 | Tauri | text/*, image/*, */* | no | self-hosted | AI RAG search | 1.8.8 2026-06 |
| MemGallery | MIT | Kotlin | text/plain, image/*, text/calendar | no (URL summaries only) | **on-device** (+BYO LLM) | semantic search, AI tags; no graph | push 2026-08-23 |
| Cobalt (hanan-bhatti) | AGPL-3.0 | Kotlin | text, image/*, video/* | no | on-device | [unverified] | young (2026-07), active |
| hasmeni/memex | MIT (README) | Python/JS + Kotlin | text/plain URL | no | self-hosted | **D3 mind map** of links | tiny, last push 2026-07-12 |
| Seal | GPL-3.0 | Kotlin + youtubedl-android | first URL in text | **yes** (yt-dlp; embeds metadata/subs; custom cmds) | on-device | download history only | **dormant** (code 2025-04) |
| YTDLnis | GPL-3.0 | Kotlin + pluggable Python/Deno | text/plain, txt lists | **yes** (yt-dlp, cookies, custom cmds) | on-device | history only | very active (v1.9.1.1 2026-10-19) |
| NewPipe | GPL-3.0 | Java/Kotlin | first URL in text | yes, but YouTube/PeerTube/Bandcamp/SoundCloud only | on-device | in-app | active (v0.29.1) |
| Sesame | GPL-2.0 | Kotlin + Chaquopy (gallery-dl + yt-dlp) | text/plain, Process Text | **yes**, incl. optional metadata, subs, IG comments | on-device | history | prototype (one day of commits, 2026-08) |
| Flipper the Ripper | MIT | Kotlin + yt-dlp | direct share | yes (video), IG WebView login | on-device | history | active small |
| ShareDL | none | Kotlin + yt-dlp-android | text URL | yes (Instagram only) | on-device | none | new (2026-10) |
| Sharewerk | Apache-2.0 | Python server + PWA | URL (Web Share Target) | **yes**: all images, key frames, transcript, caption/author txt (IG/FB) | self-hosted | none (pass-through) | new (2026-10) |
| cobalt (imputnet) | AGPL-3.0 | Node + Svelte | URL (web/API) | media only; no social metadata | self-hostable API | none | last push 2026-04 |
| yt-dlp / gallery-dl / instaloader | Unlicense / GPL-2.0 / MIT | Python | (engines) | yes + JSON metadata/comments | either | — | all very active |
| Khoj | AGPL-3.0 | Python; Android TWA | **no share target** | no | self-hosted | semantic search, chat | slowing (beta 2026-03) |
| Reor | AGPL-3.0 | Electron | — | — | local | embeddings auto-link | **archived** |
| Supermemory | MIT | TS | no mobile client | no | self-hostable API | hybrid search, graph engine | active |

---

## 8. Gaps no existing project fills (relevant to yearn)

1. **Lossless intent capture.** No project persists the *whole* share as the original record. The whole share means `EXTRA_TEXT` (including styled spans and `EXTRA_HTML_TEXT`), `EXTRA_SUBJECT`, `EXTRA_TITLE`, every `EXTRA_STREAM`/ClipData URI copied before the grant expires, MIME type, vendor extras (Chrome `EXTRA_SHARE_ORIGIN`, `STREAM_ALT_TEXT`), timestamp and `getReferrer()`.
   - Karakeep keeps only the first URL or first file. Anytype keeps text *or* URL. Bookmark clients keep only the URL.
   - Joplin and SiYuan come closest on fields (text, subject or title, all streams) but store them as a note, not as structured provenance, and record no source app.
2. **Real media plus caption plus author as structured data, from a universal target.** Each half exists separately:
   - The downloaders (YTDLnis, Sesame, Seal) fetch media, and Sesame and ArchiveBox add info-json, comments and subtitles. But they are download managers or archives with no unified item model across text, images, articles and posts.
   - The knowledge apps have the item model but fetch nothing.
   - ArchiveBox is the only stack that does maximal capture by default, and its Android client accepts URLs only (no images, files or text notes) and requires a server.
3. **Source-app provenance.** No project records which app the share came from. Receiver code reviewed for this report never calls `getReferrer()`; a full audit was not done.
4. **Capture-first, organize-later graph.** Graph and mind-map views exist only over hand-written notes (Logseq, SiYuan, Trilium, Anytype) or bare links (hasmeni/memex). Nobody auto-builds a graph from captured posts and pages (shared author, topic, embedding similarity, source app, time) with search over captions, transcripts and OCR. Karakeep, Blinko and MemGallery have semantic search but no graph. Supermemory has a graph engine but no capture client.
5. **Offline-tolerant hybrid pipeline.** No project does all of the following:
   - capture instantly on-device and offline (copy streams, log intent, parse URL)
   - enrich asynchronously, on-device (Chaquopy or youtubedl-android) or on a self-hosted worker (yt-dlp, gallery-dl, instaloader, Whisper, OCR)
   - retry when extractors are updated, and keep the raw share even if extraction fails
6. **Auth and geography as first-class concerns.**
   - Instagram needs logged-in cookies (risking the account).
   - YouTube needs a JS runtime.
   - TikTok is unreachable from India without a non-Indian egress.
   - Reddit's unauthenticated JSON is 403.

   Existing tools handle these ad hoc: per-app WebView login, or a cookie file on the server. None surfaces a per-capture "extraction status / needs-login / blocked-region" state that the user can act on later.

---

## 9. Could not verify
- What Instagram, TikTok, YouTube, X, Reddit, WhatsApp and the gallery apps actually put in their outgoing share intents (closed source). Only indirect evidence exists (§6.2).
- Whether `getReferrer()` names the source app on OEM share sheets (Samsung One UI, MIUI/HyperOS) and in apps with custom in-app sheets.
- Source location of the official Readeck Android app (`org.readeck.apps.android`).
- The current state of Logseq's Android app (the README says the DB-version Android app is "coming soon"; the 2.0.x release notes were not read).
- The TikTok oEmbed field list (the docs page returned 503, and the endpoint redirected from India).
- Logged-out OpenGraph contents for Instagram, TikTok and X posts.
- Graph or relation-map features of Anytype and Trilium (not re-checked in this pass).
- Third-party cobalt Android clients.
- The behavior of the linkding Android clients (LinkBuddy, Linkdy, Linklater, Pinkt) on share.
