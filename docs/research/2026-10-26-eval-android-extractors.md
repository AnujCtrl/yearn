# Evaluation: Kotlin Android link-to-media extractors (Sesame, YTDLnis, youtubedl-android, Seal, Seal Plus)

Research date: 2026-10-26. Companion to [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md); context is not repeated here.

**Method.**
- Each repo was shallow-cloned and its source read. Nothing was built or run.
- Issue and PR stats come from complete dumps: `gh issue list --state all --limit 3000 --json …,reactionGroups,comments` and `gh pr list`, analysed locally with `jq`. "12 mo" means created or closed on or after 2025-10-26.
- Reaction rankings were computed from each issue's `reactionGroups`.
- Maintainer response time is the time to the first comment by a repo owner or core committer.
- Repo metadata comes from `gh api repos/…`, `/releases` and `/contributors`. Discussions came from GraphQL, sorted by `upvoteCount`.

Permalinks are pinned to the commits that were read:

| repo | commit | commit date |
|---|---|---|
| Sesame | `59485d3` | 2026-08-03 |
| YTDLnis | `7c8e048` | 2026-10-25 |
| youtubedl-android | `d725d5c` | 2025-11-16 |
| Seal | `7677f61` | 2026-09-25 (README-only commit; last code change 2025-04-06) |
| Seal Plus | `5fb3f0d` | 2026-10-13 |

## Verdicts

| project | license | health in one line | verdict |
|---|---|---|---|
| **YTDLnis** (deniscerri/ytdlnis) | GPL-3.0, plus a name-use restriction in the README | 10.8k★. 11 releases in 12 months. 55 open / 748 closed issues. Median 14 h to the first maintainer reply. Bus factor 1. | **grab specific components** |
| **Sesame** (Abdull0100/sesame) | GPL-2.0 (pulls in gallery-dl, which is GPL-2.0-only) | 0★, 6 commits in one evening (2026-08-02/03), 0 issues, 8 downloads. A one-off. | **grab specific components** (Python option sets and the Instagram comments archiver, for the home server) |
| **youtubedl-android** (yausername/youtubedl-android) | GPL-3.0 | Last release 0.18.1 (2025-11-16). 1 PR merged in 12 months; 10 PRs open and unreviewed. 1 of 25 new issues got a maintainer reply. | **avoid** |
| **Seal** (JunkFood02/Seal) | GPL-3.0 | 30k★, but no code commit since 2025-04-06 and no maintainer comment since 2024-11-15. 706 open issues; Instagram reports are bot-closed. | **ideas only** |
| **Seal Plus** (MaheshTechnicals/Sealplus) | GPL-3.0 (fork of Seal) | 1.4k★, 23 releases in 12 months. Single developer. Commits read like AI audit passes. YouTube-centric additions. | **avoid** |

**Bottom line:** none of the five is worth forking for yearn. None reads `EXTRA_SUBJECT`, `getReferrer()`, inbound `SEND_MULTIPLE` or inbound `EXTRA_STREAM` media. All five keep only the first URL regexed out of `EXTRA_TEXT` (§6 has the grep).

---

## 1. YTDLnis (deniscerri/ytdlnis)

Repo facts:
- GPL-3.0, 10,774★, 504 forks, created 2022-05-27, pushed 2026-10-25.
- Latest release [v1.9.1.1](https://github.com/deniscerri/ytdlnis/releases), 2026-10-19.

### What it does best
- **The most complete yt-dlp front-end on Android.**
  - Queue, scheduled downloads and concurrent downloads.
  - Per-item playlist editing, custom command templates and a full in-app terminal (Termux terminal-view plus bash).
  - Cut by timestamp or chapter, SponsorBlock, embedded subs/metadata/chapters, metadata editing.
  - Incognito mode, and "observe sources" (auto-download new items from a channel or playlist).
  - Feature list: [README](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/README.md#L62-L91).
- **Runtime packaging that keeps up with yt-dlp.** YTDLnis no longer uses youtubedl-android's Python/yt-dlp core on its main flavours.
  - The `github` and `foss` flavours depend on its own `ytdlnis_packages.python:3.14.6`. Only the `izzy` flavour still pulls `youtubedl-android:library:0.18.1` ([app/build.gradle L175-L188](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/build.gradle#L175-L188)).
  - The changelog says Python 3.14 ships "with CURL-CFFI built-in" (arm64 only) ([CHANGELOG L90-L99](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/CHANGELOG.md#L90-L99)). That matters because yt-dlp's Instagram extractor needed impersonation in mid-2026. The maintainer said so in [#1252](https://github.com/deniscerri/ytdlnis/issues/1252) (24 comments, closed in 3 days), noting it also "depends on the location".
- **Pluggable runtimes** (Python, FFmpeg, aria2c, Deno, Node, QuickJS) as separately installable APKs.
  - `PackageBase` runs each executable from the companion APK's `nativeLibraryDir` and falls back to the bundled copy ([PackageBase.kt L85-L110](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/packages/PackageBase.kt#L85-L110), [L147-L190](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/packages/PackageBase.kt#L147-L190)).
  - This is a clean way around Android's rule against executing files from writable app storage. Runtimes update without an app release.
- **Process-tree kill.** `runKillable` / `destroyProcessTree` walk descendant PIDs, so cancelling a coroutine also kills the ffmpeg, aria2c or JS runtime that yt-dlp spawned ([RuntimeManager.kt L318-L400](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/RuntimeManager.kt#L318-L400)). youtubedl-android gets this wrong (§3).
- **What users ask for and upvote.**
  - The tracker has low reaction counts; the top issue has 9. The top items are "can't download" breakages: [#687](https://github.com/deniscerri/ytdlnis/issues/687) (9), [#969](https://github.com/deniscerri/ytdlnis/issues/969) (6), [#1009](https://github.com/deniscerri/ytdlnis/issues/1009) js-runtime (3), [#970](https://github.com/deniscerri/ytdlnis/issues/970) impersonation dependencies (3). Each was fixed and closed.
  - The top feature request is [#514](https://github.com/deniscerri/ytdlnis/issues/514), "extra content in the file.txt" (5).
  - Discussions show users steering people *to* YTDLnis: in Seal's [discussion #2495](https://github.com/JunkFood02/Seal/discussions/2495), "Is this project still actively maintained?", a user answers "No I suggest you to move to ytdlins".
- **Better than its peers because** it is the only one of the five that tracks yt-dlp's moving dependencies (JS runtime, PO tokens, curl_cffi) in shipped releases.

### Code worth stealing (with permalinks)
- **Share entry points.**
  - The `ShareActivity` bottom-sheet activity is `singleInstance`, `excludeFromRecents` and has an empty `taskAffinity`. It takes `SEND text/plain` plus `VIEW` filters for about 20 hosts (instagram, x/twitter, reddit, tiktok…) ([AndroidManifest L74-L330](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L74-L330)).
  - A *second* share target, "Quick download", is an `activity-alias` with `quick_run_background` meta-data. It is disabled by default and toggled at runtime, so the user picks which targets appear in the share sheet ([L391-L408](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L391-L408)). The pattern is worth copying for a "capture silently" versus "capture with note" pair of targets.
  - `SEND application/txt` reads a shared text file as a list of URLs ([manifest L411-L430](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L411-L430), [MainActivity.kt L397-L446](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/MainActivity.kt#L397-L446)).
  - URL parsing is first-match-only, falling back to the raw string ([Extensions.kt L664-L673](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/Extensions.kt#L664-L673)).
- **Cookie capture.** A WebView login (optionally in a separate `:incognito_process`) is converted to a Netscape `cookies.txt`.
  - The cookies are read directly from WebView's Chromium `Cookies` SQLite DB. That picks up HttpOnly cookies, plus path and secure flags ([CookieViewModel.kt L99-L152](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/database/viewmodel/CookieViewModel.kt#L99-L152), [WebViewActivity.kt L255-L289](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/ui/more/cookies/WebViewActivity.kt#L255-L289)).
  - The technique came from Seal (§4).
  - Bug: `expires_utc` is Chromium-epoch microseconds, and it is written raw into the Netscape field, which expects Unix seconds ([L93](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/database/viewmodel/CookieViewModel.kt#L93)).
  - Cookie generation fails on some ROMs: GrapheneOS [#1373](https://github.com/deniscerri/ytdlnis/issues/1373), OnePlus clone [#1293](https://github.com/deniscerri/ytdlnis/issues/1293), both open.
- **Keeping metadata.**
  - The info JSON is captured as a side effect via `--no-clean-info-json --print-to-file video:%()j <cache>/<crc32>…info.json`, rather than with `--write-info-json` ([YTDLPUtil.kt L730-L743](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt#L730-L743)).
  - `--write-description` is opt-in ([L1157-L1159](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt#L1157-L1159)), as is `--write-thumbnail` ([L1091-L1094](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt#L1091-L1094)).
  - The download archive is used ([L1192](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt#L1192)).
  - Cookies are attached through `--cookies` only when the user enables them ([L87-L97](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/util/extractors/ytdlp/YTDLPUtil.kt#L87-L97)).
- **Background work and anti-rate-limit.**
  - One long-lived `DownloadWorker` (WorkManager, foreground `dataSync`) drains a Room-persisted queue via a Flow of `getQueuedScheduledDownloadsUntil` ([DownloadWorker.kt L84-L175](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/work/DownloadWorker.kt#L84-L175)).
  - Concurrency is capped by a preference.
  - A **random min–max delay between downloads** is applied (`download_delay`, L132-L135 and L186-L200), which is useful against Instagram throttling.
- **Updater.** It fetches the GitHub `releases/latest` JSON and swaps the yt-dlp zipapp in place ([YTDLUpdater.kt L24-L64](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/YTDLUpdater.kt#L24-L64)). It does **no checksum or signature check** (no sha/digest in the file).
- **UI.** Mostly Views/XML plus Material 3; Compose is only partial. No reusable animation or blur work: a grep for `RuntimeShader`, `RenderEffect` and `blur` finds nothing.

### Issue-tracker health
- **Volume.** 803 issues: 55 open, 748 closed. Bug label: 30 open / 409 closed.
- **Last 12 months.** 291 opened, 273 closed (94/89 in the last 90 days). The maintainer commented on 195/291, with a median 14.3 h to first reply and a median 4.5 days to close.
- **Labels.** "Waiting For Release" is on 145 issues, so fixes land and wait for a release.
- **PRs.** 102 merged in 12 months; 73 of them are Weblate translations, about 26 are code from others.
- **Commits.** deniscerri has 1,454; the next human has 177. **Bus factor 1.**
- **Recurring themes (12 mo, by title):**
  - crash: 19, mostly UI-state crashes such as [#1306](https://github.com/deniscerri/ytdlnis/issues/1306) "Fragment not attached" and [#1150](https://github.com/deniscerri/ytdlnis/issues/1150) CursorWindow overflow on 2,800-item playlists
  - cookies/login: 10
  - update: 10
  - 403: 4
  - Instagram: 3 ([#1376](https://github.com/deniscerri/ytdlnis/issues/1376) open)
  - TikTok: 3
  - JS runtime: 4, all closed
  - Android-version: 2
  - background-kill: 2, so battery and Doze are **not** a recurring theme
- **Directly relevant to yearn:** [#1194](https://github.com/deniscerri/ytdlnis/issues/1194) asked to prefill the title from `EXTRA_SUBJECT`. The reporter says YouTube puts the title there. It was **closed wontfix** because a filled title makes YTDLnis skip its full JSON dump.

### Code quality
- **Tests.** The only test is `ExampleUnitTest` (2+2).
- **God files.** `UiUtil.kt` has 3,354 lines, `YTDLPUtil.kt` 1,757, `DownloadViewModel.kt` 1,261. Preferences are read ad hoc via `getString(...)!!`.
- **Share activity** ([ShareActivity.kt](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/receiver/ShareActivity.kt)):
  - logs `Log.e("aa", intent)` (L160)
  - calls `resultViewModel.deleteAll()` on every new share (L190)
  - adds a raw `WindowManager` overlay view when `SYSTEM_ALERT_WINDOW` is granted (L75-L117)
  - `!!`-dereferences `EXTRA_TEXT` (L173)
- **Error handling.** `YTDLPCoroutineWorker` swallows every exception into `Result.failure()` ([L30-L42](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/work/YTDLPCoroutineWorker.kt#L30-L42)).
- **Exported components without permissions:**
  - `WebViewActivity` takes a `url` extra ([manifest L523-L533](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L523-L533)), so any app can open YTDLnis' cookie-capturing WebView on an arbitrary URL.
  - `BgUtilsPoTokenGeneratorService` is `exported="true"` ([L606-L610](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L606-L610)).
- **Architecture.** Sensible underneath: Room plus WorkManager, and the `core/` runtime layer is cleanly separated.

### License
- GPL-3.0. Copying any of its code into yearn's APK makes the APK GPL-3.0. The source must then be offered to whoever receives the APK; for an APK only you use, that obligation is effectively nil.
- The README forbids other apps using the "YTDLnis" name ([L185](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/README.md#L185)).
- **Incompatible with Sesame's GPL-2.0-only**: the two codebases cannot be merged into one binary.

---

## 2. Sesame (Abdull0100/sesame)

Repo facts:
- GPL-2.0, 0★, 0 forks, created and last pushed 2026-08-02.
- Releases v0.1.0/v0.1.1, which together have 8 downloads.
- Owner account has 2 public repos and 5 followers.

### What it does best
- **The only on-device app here that embeds gallery-dl as well as yt-dlp**, both in-process through Chaquopy.
  - Python 3.11, with pip pins `gallery-dl==1.32.1` and `yt-dlp==2026.7.4` ([build.gradle.kts L86-L97](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/build.gradle.kts#L86-L97)).
  - There are no subprocesses, so progress, logging and cancellation go through Python callbacks.
- **Engine routing by host.**
  - instagram, pixiv, deviantart, flickr and imgur go to gallery-dl; everything else goes to yt-dlp ([EngineRouter.kt L8-L20](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/EngineRouter.kt#L8-L20)).
  - Sending Instagram to gallery-dl sidesteps yt-dlp's 2026 curl_cffi impersonation problem. gallery-dl uses its own Instagram API calls with cookies.
- **Metadata-first option sets.** These are the best part of the repo.
  - gallery-dl gets a `metadata` post-processor that writes `{post_id|media_id|id|shortcode}.json` per post, plus `instagram.videos = "merged"` ([sesame_bridge.py L177-L232](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L177-L232)).
  - yt-dlp gets `writeinfojson`, `writedescription`, `writethumbnail`, `getcomments`, `writesubtitles`, `writeautomaticsub` and `allsubtitles` ([L267-L289](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L267-L289)).
- **Instagram comment archiver.** No other project in this set has one.
  - The shortcode becomes a media ID via base64 ([L412-L417](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L412-L417)).
  - It pages `/api/v1/media/{id}/comments/` with `min_id` cursors, at most 200 pages, sleeping 0.8 s between them.
  - It writes `comments.json` and downloads GIF/video attachments in comments while skipping avatars ([L298-L404](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L298-L404)).
  - The session is the cookies.txt plus the `X-IG-App-ID: 936619743392459` and CSRF headers ([L339-L355](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L339-L355)).
- **Share entry points.**
  - `SEND text/plain` **and `PROCESS_TEXT`**, so selected text in any app gets a "Sesame" entry in the text-selection menu ([manifest L34-L44](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/AndroidManifest.xml#L34-L44)).
  - It reads `EXTRA_TEXT` / `EXTRA_PROCESS_TEXT` only ([MainActivity.kt L42-L49](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/MainActivity.kt#L42-L49)).
- **Background work.**
  - Room records feed WorkManager `enqueueUniqueWork("download:$id")`, with the network constraint switching to UNMETERED when "wifi only" is set ([DownloadRepository.kt L69-L81](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/DownloadRepository.kt#L69-L81)).
  - The worker runs in the foreground (`dataSync`), behind a global `Mutex` so only one Python job runs at a time ([DownloadWorker.kt L32-L43](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/DownloadWorker.kt#L32-L43), [L182](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/DownloadWorker.kt#L182)).
  - Errors map to user-facing hints, for example "Instagram refused the request; refresh your cookies" ([L168-L177](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/DownloadWorker.kt#L168-L177)).
- **Clean MediaStore publishing.** Files go into `Download/<folder>/<subdirs>` using `IS_PENDING` 1→0 and are deleted on failure ([MediaPublisher.kt L27-L52](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/MediaPublisher.kt#L27-L52)).
- **Cookie import.** A size-capped (5 MB) cookies.txt import, with a light format check ([AppSettings.kt L56-L65](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/data/AppSettings.kt#L56-L65)).

### Issue-tracker health
- Zero issues, zero PRs, no discussions.
- 6 commits by one author over 1 h 16 min (2026-08-03 01:33 → 02:49 +0500), with nothing since.
- **This is a one-off, not a project.** The README itself says "There is no stable release yet" and that it has not had "broad device testing".

### Code quality
- **Tidy for its size** (about 1,500 lines of Kotlin and Python), and it has the only real tests among the five:
  - Python unit tests for the shortcode, media-URL and filename helpers ([tests/test_sesame_bridge.py](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/tests/test_sesame_bridge.py))
  - a Kotlin `EngineRouterTest`
  - CI that runs both
- **Weaknesses:**
  - **Extractors are frozen in the APK, with no updater.** yt-dlp is 2026.7.4, about 3.5 months stale, and Chaquopy pip pins only change with a rebuild.
  - **No curl_cffi**, so yt-dlp-routed X/TikTok downloads hit the same impersonation wall.
  - **No FFmpeg**, so yt-dlp formats are pre-merged only.
  - No retry or backoff: the worker always returns `Result.failure()`.
  - The `archive_file` argument is accepted and then discarded (`del archive_file`, [L82](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L82)).
  - The progress callback does `runBlocking` DB writes from the Python thread.

### License
- GPL-2.0, and gallery-dl is GPL-2.0-only; the README calls this out ([L56-L57](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/README.md#L56-L57)).
- Embedding gallery-dl in yearn's APK would force the APK to GPL-2.0-compatible terms. That **excludes mixing in GPL-3.0-only code** from YTDLnis, Seal or youtubedl-android, and Apache-2.0 code too under FSF interpretation.
- **On the home server this mostly disappears.** Running gallery-dl or yt-dlp as separate processes, privately, is not distribution, so no GPL obligations attach.

---

## 3. youtubedl-android (yausername/youtubedl-android)

Repo facts:
- GPL-3.0, 1,380★, 252 forks.
- Maven artifact `io.github.junkfood02.youtubedl-android`, latest 0.18.1 (2025-11-16, tagged prerelease like every release since 2022).

### What it does best
- **The de-facto way to run CPython plus yt-dlp as a subprocess on Android.**
  - Python, FFmpeg, aria2c and QuickJS ship as `lib*.so` / `lib*.zip.so` inside `nativeLibraryDir`. They are unzipped to `noBackupFilesDir` on init, and the Python archive's size doubles as its "version" ([YoutubeDL.kt L30-L96](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L30-L96)).
  - The process environment sets `LD_LIBRARY_PATH`, `SSL_CERT_FILE`, `PYTHONHOME` and `TMPDIR` ([L216-L223](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L216-L223)).
  - Every call is forced to `--js-runtimes quickjs:…` and `--ffmpeg-location …` ([L199-L202](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L199-L202)). QuickJS was added after the [#337](https://github.com/yausername/youtubedl-android/issues/337) "JS Runtime needed" crisis (closed 2025-11).
  - Update channels are stable, nightly and master, driven by GitHub releases ([L289-L295](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L289-L295)).
- **What people upvote.** Build and distribution pain, not features:
  - [#309](https://github.com/yausername/youtubedl-android/issues/309), no debug APK (4)
  - [#215](https://github.com/yausername/youtubedl-android/issues/215), failure to get stream (4)
  - [#265](https://github.com/yausername/youtubedl-android/issues/265), Python 3.12 request (2, open since 2024-01)
- **Better than its peers because** it is a reusable library, and the only one here. Seal, Seal Plus and YTDLnis' izzy flavour all depend on it.

### Issue-tracker health
- **Volume.** 295 issues: 109 open, 186 closed.
- **Last 12 months.** 25 opened, 5 closed, **0 closed in the last 90 days**. A maintainer commented on 1 of 25. The last maintainer comment was 2025-11-20.
- **PRs.** 1 merged in 12 months; the last merge was 2025-11-16. **10 open PRs are unreviewed**, including:
  - Python 3.14 plus full curl_cffi impersonation, [#361](https://github.com/yausername/youtubedl-android/pull/361)
  - 16 KB page-size fix, [#350](https://github.com/yausername/youtubedl-android/pull/350)
  - process-ID lifecycle fixes, [#364](https://github.com/yausername/youtubedl-android/pull/364) and [#367](https://github.com/yausername/youtubedl-android/pull/367)
  - yt-dlp bump, [#359](https://github.com/yausername/youtubedl-android/pull/359)
- **Open breakages:**
  - Instagram, [#353](https://github.com/yausername/youtubedl-android/issues/353) and [#355](https://github.com/yausername/youtubedl-android/issues/355)
  - 16 KB-page crash in the bundled libwebp, [#365](https://github.com/yausername/youtubedl-android/issues/365) and [#345](https://github.com/yausername/youtubedl-android/issues/345)
  - ffmpeg missing `libc++_shared.so`, [#369](https://github.com/yausername/youtubedl-android/issues/369)
  - storage permission on Android 16, [#344](https://github.com/yausername/youtubedl-android/issues/344), and on 0.18.0, [#335](https://github.com/yausername/youtubedl-android/issues/335) (12 comments)
  - slow QuickJS load, [#371](https://github.com/yausername/youtubedl-android/issues/371)
- **Requests for build provenance of the prebuilt native blobs**, [#358](https://github.com/yausername/youtubedl-android/issues/358) and [#363](https://github.com/yausername/youtubedl-android/issues/363), are unanswered.
- **Commits.** yausername 83, xibr 82, JunkFood02 44. In practice only xibr is active, and only until 2025-11, so the effective bus factor is 1, now idle.

### Code quality
- **Small** (about 900 lines of library Kotlin). Tests are only the template `ExampleUnitTest`.
- **Bugs:**
  - `destroyChildProcesses` runs `pstree -p $id`, where `$id` is the caller's *string process ID*, not a PID. Child processes (ffmpeg, aria2c) are therefore not killed ([L142-L152](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt#L142-L152)). YTDLnis rewrote this (§1).
  - `quickJsPath!!` and `ffmpegPath!!` are forced on every request.
  - The README still says "python 3.8" ([L64](https://github.com/yausername/youtubedl-android/blob/d725d5c9a18c3a99a13ee0308bf78275dc310760/README.md#L64)), while the code bumped Python to 3.12.11 (commit log 2025-09-28).
  - The bundled yt-dlp is 2025.11.12, so the runtime updater is mandatory. The updater does no integrity check.

### License
GPL-3.0. Linking it into yearn makes the app GPL-3.0. Its native blobs are Termux-derived builds with no provenance.

---

## 4. Seal (JunkFood02/Seal)

Repo facts:
- GPL-3.0, 30,053★, 1,509 forks.
- Last stable [v1.13.1](https://github.com/JunkFood02/Seal/releases), 2024-10-16; last prerelease v2.0.0-alpha.5, 2024-11-29.

### What it does best
- **Polish and Material You design.** That is the reason for its 30k★.
  - Transitions are built on an Apache-2.0 `MaterialSharedAxis` port (copyright SOUP) ([MaterialSharedAxis.kt](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/ui/common/motion/MaterialSharedAxis.kt)).
  - It hand-tunes M3 "emphasized" easing curves, including a `PathEasing` for the true emphasized curve ([AnimationSpecs.kt L15-L31](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/ui/common/motion/AnimationSpecs.kt#L15-L31)).
- **Share-sheet overlay in pure Compose.** `QuickDownloadActivity` is a transparent full-window activity that hosts a `ModalBottomSheet` over the sharing app and finishes when the sheet hides ([QuickDownloadActivity.kt L41-L131](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt#L41-L131)). It is the closest existing pattern to yearn's "drop over the source app" capture UI, though it uses a stock sheet with no blur or fluid motion.
- **It originated the WebView cookie-DB technique** later copied by YTDLnis and Seal Plus. It opens `app_webview/Default/Cookies` read-only after `CookieManager.flush()` ([DownloadUtil.kt L374-L395](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt#L374-L395)).
- **What people upvote** (the highest counts in this set):
  - [#736](https://github.com/JunkFood02/Seal/issues/736), incomplete playlist (29, open since 2023)
  - [#324](https://github.com/JunkFood02/Seal/issues/324), download queue (20)
  - [#1729](https://github.com/JunkFood02/Seal/issues/1729), Python error (14)
  - [#2305](https://github.com/JunkFood02/Seal/issues/2305), "implement a JS runtime (as Deno)" (13, open)
  - [#1161](https://github.com/JunkFood02/Seal/issues/1161), no temp directory (11, open)
  - [#2455](https://github.com/JunkFood02/Seal/issues/2455), "can't download video in youtube" (10, open)
  - [#152](https://github.com/JunkFood02/Seal/issues/152), "Overlay UI when a URL is shared" (8), the origin of the quick-download sheet

### Issue-tracker health
- **Volume.** 2,168 issues: 706 open, 1,462 closed. Bug label: 497 open / 1,047 closed.
- **Last 12 months.** 326 opened, 118 closed. **Zero maintainer comments.** The last JunkFood02 comment on any issue was 2024-11-15.
- **The 0-day median close time is an artefact.** 105 of the 12-month issues carry `action-closed`: a GitHub Action closes them as duplicates of [#1399](https://github.com/JunkFood02/Seal/issues/1399).
- **Instagram.** 208 Instagram-titled issues (24 in 12 months) are almost all bot-closed, for example [#2671](https://github.com/JunkFood02/Seal/issues/2671) and [#2673](https://github.com/JunkFood02/Seal/issues/2673) (Oct 2026). One user explains the cause in [#2566](https://github.com/JunkFood02/Seal/issues/2566): Seal's bundled yt-dlp "does not include curl_cffi".
- **Other themes:** TikTok 61 (25 open), cookies 56 (16 open), Android-version 38 (16 open), share 31 (10 open).
- **PRs.** 34 open; none merged since 2024-11-29.
- **Commits.** Since 2025-04-06, only monthly "update sponsor info" README commits ([commit log](https://github.com/JunkFood02/Seal/commits/main)).
- **Bus factor 1, and that maintainer is inactive.**

### Code quality
- **Good Compose code by 2024 standards.** `DownloaderV2` is an in-memory `SnapshotStateMap` with `MAX_CONCURRENCY = 3` and an MMKV task-list backup ([DownloaderV2.kt L43](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/download/DownloaderV2.kt#L43), [L119](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/download/DownloaderV2.kt#L119)). It runs in a plain foreground `DownloadService`; there is no WorkManager.
- **Tests.** Template only.
- **Bugs:**
  - `QuickDownloadActivity.onCreate` calls `finish()` without `return` when no URL is found, then carries on and starts the service ([L70-L74](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt#L70-L74)).
  - It starts the download service on *every* share, even a cancelled one. That causes a stuck "downloading" notification, fixed only in Seal Plus [#25](https://github.com/MaheshTechnicals/Sealplus/issues/25).
- **Share handling.** `matchUrlFromSharedText` keeps the first URL only ([TextUtil.kt L91-L97](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/util/TextUtil.kt#L91-L97)).
- **Metadata.** No info.json is kept: Seal passes `--no-embed-info-json` and only `--dump-single-json` for preview ([DownloadUtil.kt L441-L442](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt#L441-L442)).
- **Dependencies are stale:** youtubedl-android 0.17.3 and Kotlin 2.0.20 ([libs.versions.toml](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/gradle/libs.versions.toml)). 0.17.3 predates the QuickJS addition (0.18.1), so current YouTube support needs a JS runtime it does not have.

### License
- GPL-3.0. The README also restricts the "Seal" name.
- The `motion/` files are Apache-2.0 per their headers and can be lifted under Apache terms with attribution.

---

## 5. Seal Plus (MaheshTechnicals/Sealplus)

Repo facts:
- GPL-3.0, GitHub fork of Seal, 1,394★, created 2026-01-04, pushed 2026-10-14.
- Releases v2.4.0 → v3.1.0: 23 in 12 months, roughly every 3–4 weeks.

### What it does best
- **It keeps Seal alive.**
  - youtubedl-android 0.18.1, so QuickJS is present; Kotlin 2.3.21; targetSdk 37 ([libs.versions.toml](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/gradle/libs.versions.toml)).
  - It fixes Seal's share-sheet service leak ([#25](https://github.com/MaheshTechnicals/Sealplus/issues/25)) and the ignored "configure before download" setting ([#30](https://github.com/MaheshTechnicals/Sealplus/issues/30)).
  - It restores queued tasks as Paused after a force-stop ([DownloaderV2.kt L250-L320](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/app/src/main/java/com/junkfood/seal/download/DownloaderV2.kt#L250-L320)).
- **New "tools":**
  - A comment downloader: `--skip-download --write-comments --dump-single-json` with a `youtube:max_comments=N` cap and top sort ([DownloadUtil.kt L386-L420](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt#L386-L420)). It is YouTube-only by construction.
  - Video-info and thumbnail export to TXT/JSON ([VideoInfoExportUtil.kt](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/app/src/main/java/com/junkfood/seal/util/VideoInfoExportUtil.kt)).
  - Batch URL import, app lock, a "Gradient Dark" theme.
  - "Premium glass" cards, which are a 1 dp border plus a press-scale spring, **not real blur or glass** ([PremiumComponents.kt L31-L80](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/app/src/main/java/com/junkfood/seal/ui/component/PremiumComponents.kt#L31-L80)).
- **What people upvote.** Almost nothing: the maximum is 1 reaction ([#6](https://github.com/MaheshTechnicals/Sealplus/issues/6)). Users mostly file breakages (Reddit [#32](https://github.com/MaheshTechnicals/Sealplus/issues/32), TikTok [#23](https://github.com/MaheshTechnicals/Sealplus/issues/23)) and ask for F-Droid ([#22](https://github.com/MaheshTechnicals/Sealplus/issues/22)).

### Issue-tracker health
- **Volume.** 25 issues: 5 open, 20 closed. The maintainer commented on 21/25, but with a **median 16 days to first reply** (385 h). Fixes come in batches per release, e.g. "fix: resolve open GitHub issues #30, #25, #26, #14, #27, #19".
- **Open data-loss bug:** [#36](https://github.com/MaheshTechnicals/Sealplus/issues/36), "App crashes during manual retry and loses all unfinished download tasks" (2026-10-24).
- **PRs.** 12 opened; 2 merged.
- **Bus factor 1.** 73 of 74 recent commits are by the owner.
- **The commit style raises a quality flag.** Messages like "comprehensive production-readiness audit — 20 bugs fixed" and "fix: resolve 4 critical bugs found in second audit" read as LLM audit passes; 4 commits mention "audit" ([commits](https://github.com/MaheshTechnicals/Sealplus/commits/main)).
- **Monetisation.** It adds crypto-donation and sponsor pages, and its self-updater pulls APKs from its own GitHub releases ([UpdateUtil.kt L53-L57](https://github.com/MaheshTechnicals/Sealplus/blob/5fb3f0d541ed62369bf643d969442b458c8534be/app/src/main/java/com/junkfood/seal/util/UpdateUtil.kt#L53-L57)).

### Code quality
- About 57k lines of Kotlin, versus Seal's 34k. Tests remain template-only.
- It inherits Seal's limits: first-URL-only sharing, no info.json, and **no curl_cffi**, because youtubedl-android 0.18.1 has none (the PR adding it, [#361](https://github.com/yausername/youtubedl-android/pull/361), is unmerged). Instagram therefore stays broken without cookies in regions that need impersonation.

### License
GPL-3.0, inherited from Seal, plus Seal's name restriction.

---

## 6. Cross-cutting findings

- **Share-intent capture is primitive everywhere.** A grep of all five repos for `getReferrer`, `EXTRA_REFERRER` and `EXTRA_SUBJECT`, and for *inbound* `SEND_MULTIPLE` and `EXTRA_STREAM`, finds:
  - no use of the referrer extras or `EXTRA_SUBJECT`
  - no inbound `SEND_MULTIPLE`
  - inbound `EXTRA_STREAM` only in YTDLnis, where a shared .txt file is read as a URL list
  - no Direct Share `share-target` shortcuts
  - every `EXTRA_STREAM` hit is *outbound* sharing of downloaded files

  yearn's lossless capture has no donor here; [Joplin/SiYuan](2026-10-26-share-capture-prior-art.md) remain the better references.
- **2026's dominant breakage: Instagram plus curl_cffi.**
  - YTDLnis shipped curl-cffi in Python 3.14, on arm64 only.
  - youtubedl-android, and therefore Seal and Seal Plus, has not shipped it.
  - Sesame avoids the problem by routing Instagram to gallery-dl.
  - For yearn's home server (x86_64 Linux) this is a non-issue: `pip install "yt-dlp[default,curl-cffi]"` (yt-dlp README; **[not re-verified in this pass]**).
- **No project runs extraction without cookies for Instagram reliably.** Cookie UX is the recurring support load:
  - YTDLnis: 18 cookie issues
  - Seal: 56
- **No reusable "liquid glass" UI.** No `RuntimeShader`, `RenderEffect` or `Modifier.blur` appears in the five apps' sources. yearn's drop/frosted-sheet animation needs another source.
- **Tests are effectively absent.** Sesame is the only one with meaningful tests, and it is a one-evening prototype.

## 7. Worth grabbing wholesale?

**No. Fork none of them.** All five are download managers built around "URL → media file". yearn's phase 1 is "intent → lossless record → queue → upload", and extraction lives on the home server. What does transfer, ranked:

1. **Sesame's Python option sets, for the phase-2 server.**
   - The gallery-dl `metadata` post-processor writing per-post JSON, and `instagram.videos=merged` ([L177-L232](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L177-L232)).
   - yt-dlp `writeinfojson`, `writedescription`, `writethumbnail`, `getcomments` and all subtitles ([L267-L289](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L267-L289)).
   - Host routing that sends Instagram to gallery-dl.
   - The Instagram comments archiver ([L298-L417](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/python/sesame_bridge.py#L298-L417)).

   Running these privately on the server carries no GPL obligation. Pin nothing; update gallery-dl and yt-dlp continuously.
2. **The WebView cookie-DB → Netscape exporter idea**, from Seal and YTDLnis ([Seal DownloadUtil L374-L395](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/util/DownloadUtil.kt#L374-L395), [YTDLnis CookieViewModel L99-L152](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/database/viewmodel/CookieViewModel.kt#L99-L152)).
   - It lets the phone log into Instagram or X in an isolated WebView, then **upload `cookies.txt` to the server**.
   - Re-implement it yourself (it is about 50 lines) to avoid GPL, and convert `expires_utc` properly: µs since 1601 → Unix seconds.
3. **YTDLnis share-target ergonomics.**
   - Two activity-alias share targets, toggled at runtime: one with UI, one silent ([manifest L391-L408](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/AndroidManifest.xml#L391-L408)).
   - Sesame's `PROCESS_TEXT` filter as an extra entry point ([L40-L44](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/AndroidManifest.xml#L40-L44)).
   - Seal's transparent-activity-plus-Compose-sheet host ([QuickDownloadActivity L65-L131](https://github.com/JunkFood02/Seal/blob/7677f61a20fda4210e84215fef9e9b51d25daa47/app/src/main/java/com/junkfood/seal/QuickDownloadActivity.kt#L65-L131)) as the skeleton for the drop UI.

   These are all patterns, not code to copy.
4. **Queue and worker patterns:**
   - Room-persisted items plus WorkManager unique work with network constraints (Sesame [DownloadRepository L69-L81](https://github.com/Abdull0100/sesame/blob/59485d354f24553c183387df19bcda4a5fa3d508/app/src/main/java/dev/sesame/app/download/DownloadRepository.kt#L69-L81)) for yearn's upload queue.
   - YTDLnis' random inter-job delay ([DownloadWorker L132-L200](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/work/DownloadWorker.kt#L132-L200)) for the server's Instagram fetcher.
5. **Only if yearn ever needs on-phone extraction as a fallback:** YTDLnis' `core/` runtime layer is the one to study: pluggable packages run from `nativeLibraryDir` plus process-tree kill ([PackageBase](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/packages/PackageBase.kt), [RuntimeManager L318-L400](https://github.com/deniscerri/ytdlnis/blob/7c8e0483c13b678a159e84e73792c2d6a9398beb/app/src/main/java/com/deniscerri/ytdl/core/RuntimeManager.kt#L318-L400)). Copying it makes yearn GPL-3.0. Do not depend on youtubedl-android.

**Licensing summary for yearn's APK.**
- Copying from YTDLnis, Seal, Seal Plus or youtubedl-android makes it GPL-3.0.
- Embedding Sesame or gallery-dl makes it GPL-2.0, and the two cannot be combined.
- Taking only ideas, plus the Apache-2.0 Seal `motion/` files, keeps yearn's license free.
