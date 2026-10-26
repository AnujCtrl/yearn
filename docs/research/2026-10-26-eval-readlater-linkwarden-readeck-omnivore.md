# Eval: Linkwarden, Readeck, Omnivore. What each does best

Research date: 2026-10-26. This builds on [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md), which covers license, stack and status. That material is not repeated here.

**Method.**
- Shallow clones were read only, never built. HEADs: linkwarden `eff7c6b` (2026-10-25), readeck `32d0536` (2026-10-23), readeck-android `e71da31` (2026-10-26), ReadeckApp `84537d9` (2026-10-02), omnivore `92caa68` (2026-10-25).
- Issue data covers every issue, not a sample:
  - GitHub: `gh issue list --state all --limit 2000` (Linkwarden 1,105 issues, Omnivore 1,343, ReadeckApp 92).
  - Codeberg API: 769 issues and 2,933 comments for Readeck, with per-issue reactions fetched separately.
- "Top requests" rank issues by total reactions in that local data. The ranking was cross-checked against `gh api search/issues -f sort=reactions`, and the results agreed.
- "Maintainer reply" is the time from an issue being opened to the first comment by a core login: daniel31x13/crkos (Linkwarden), olivier/mislav (Readeck), jensomato (ReadeckApp), jacksonh/sywhb/Podginator/satindar (Omnivore). Issues those people opened themselves are excluded. Codeberg timestamps were compared without their timezone offset, so those figures can be off by up to 2 hours.
- Bug ratio counts issues with a label containing "bug". For Readeck it uses the `Bug` label.

Permalink bases:
- LW = `https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/`
- RD = `https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/`
- RA = `https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/`
- JA = `https://github.com/jensomato/ReadeckApp/blob/84537d95871b79d56b614b024a5a93621ad8f413/`
- OM = `https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/`

| Project | Best at | Quality grade | License | Grab verdict |
|---|---|---|---|---|
| **Linkwarden** (+ Expo mobile app in the monorepo) | Multi-format preservation per link: screenshot, PDF, monolith single-file HTML, readable text and Wayback. Format choice is set per tag, and users can upload their own client-side capture. AI tagging with three modes. | **C**. Feature-rich and actively built, but one developer wrote 537 of about 560 commits in the last year. Open bugs outnumber closed bugs (199 vs 176). Only 17% of 2026 issues got a maintainer reply. The Android share path silently drops links (fix PR #1847 is not merged). There are about 11 unit-test files for about 60k LOC. | AGPL-3.0 | **Ideas plus small pieces.** Borrow the per-tag format matrix, the "upload your own capture, then let the server re-render it" pattern, and the AI tag prompts (EXISTING mode). Do not take the app: its Expo share flow is online-only and buggy. |
| **Readeck** (Go server) + **readeck-android** (official Kotlin app, a fork of ReadeckApp) + **ReadeckApp** (jensomato, Kotlin) | Per-site extraction as sandboxed JS "content scripts". YouTube transcripts, the Reddit JSON API, Bluesky and the fediverse each get a script, layered on 2,197 CC0 FiveFilters site configs. Each bookmark is stored as one immutable ZIP. The official Android app has a durable offline add queue with idempotent retry. | **A−** for the server: 74% of issues closed, bugs closed in a median of about 2 days, 80% of issues get a maintainer reply (median about 8 h), 25k lines of tests against 50k lines of Go, golangci-lint in CI. The main risk is a single maintainer. **B+** for readeck-android (only 5 months old, documented specs, 23k lines of tests). **B−** for ReadeckApp (solo developer, share is online-only). | Server AGPL-3.0-only, with pkg/archiver under MIT and site configs under CC0. Both Android apps GPL-3.0. | **Best source of components.** The content-script design and the CC0 site configs map directly onto yearn's home-server. The readeck-android offline queue (`DrainQueuedAddsWorker` plus reconcile-on-retry) is the closest existing match to yearn's capture-then-sync path. The API also accepts client-supplied HTML and resources. |
| **Omnivore** (TS monorepo + native Kotlin Android) | Rules engine: saved-search filters trigger label, archive, webhook or export actions when an item is created or highlighted. Also a rich search grammar, newsletter ingest by email, YouTube transcripts cleaned up by an LLM, and per-site handlers (oEmbed for X and TikTok). | **D.** The cloud service was shut down in November 2024. On 2026-08-25, 338 issues were bulk-closed as `NOT_PLANNED`. One community maintainer, Podginator, is mostly doing Docker and dependency upkeep. There is no test CI workflow, and the Android app has one test file. | AGPL-3.0 | **Ideas only, plus a code mine.** The rules model (filter in search grammar → actions) and the search-grammar field list are worth copying conceptually. The Android share worker drops a save on any server error. Do not fork. |

---

## 1. Linkwarden

### Standout features (code)
- **Per-tag preservation matrix.**
  - Each tag can carry archival settings: screenshot, monolith, PDF, readable, Wayback and aiTag. If a link has any "archival tag", its settings override the user defaults ([archiveHandler.ts L88-L110](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L88-L110)).
  - Content type is sniffed first, so direct images and PDFs skip the browser and go to dedicated handlers ([L237-L266](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L237-L266), [L125-L130](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L125-L130)).
  - A hard browser timeout wraps the whole job, and every format left unproduced is marked `"unavailable"` so the queue does not loop on it ([L66-L78](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L66-L78), [L206-L227](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L206-L227)).
- **Client-side capture, server-side render.**
  - Users (or the extension) can upload a SingleFile/monolith HTML, PDF or image ([archives/index.ts L139-L144, L228](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/web/pages/api/v1/archives/index.ts#L139-L228)).
  - The worker then loads that uploaded HTML into Playwright with `page.setContent`, instead of re-fetching the live URL. That lets the screenshot, readability and preview be produced from a page that needed a login ([archiveHandler.ts L134-L152](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L134-L152)).
  - This is the pattern yearn wants for logged-in Instagram/X/LinkedIn content.
- **AI tagging with three modes** ([autoTagLink.ts L86-L146](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/autoTagLink.ts#L86-L146), prompts [prompts.ts](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/prompts.ts)):
  - `GENERATE`: free-form tags, title-cased.
  - `EXISTING`: picks from the user's 50 most-used tags, and the output is filtered back to that list.
  - `PREDEFINED`: picks from a fixed list.
  - Results are capped at 5 tags, and tags are marked `aiGenerated`.
  - Providers use the Vercel AI SDK (OpenAI-compatible, Ollama, Anthropic, Azure, OpenRouter, Perplexity) ([L25-L67](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/autoTagLink.ts#L25-L67)).
  - EXISTING mode is the cheapest guard against tag sprawl when auto-organizing.
- **Fair batching and SSRF guards.** `getLinkBatchFairly` spreads worker load across users ([getLinkBatchFairly.ts](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/getLinkBatchFairly.ts)). A URL safety check runs before any server-side fetch ([archiveHandler.ts L35-L45](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/archiveHandler.ts#L35-L45)), and the SSRF code is one of the few modules with tests ([packages/lib/ssrf.test.ts](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/packages/lib/ssrf.test.ts)).
- **Search.**
  - Uses Meilisearch, with request bodies chunked under 80 MiB so huge `textContent` cannot break indexing ([linkIndexing.ts L9-L41](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/workers/linkIndexing.ts#L9-L41)).
  - The query grammar supports `url: name: description: type: collection: pinned: public: before: after: tag:`, `!` negation and quoting ([searchQueryBuilder.ts L7-L80](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/web/lib/api/searchQueryBuilder.ts#L7-L80)).
- **Mobile offline reading.** The app downloads preserved formats to the device, shows progress and storage use, and pauses when offline ([apps/mobile/lib/offlineSync.ts](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/mobile/lib/offlineSync.ts)).

### What users love and want (reactions)
- Shipped and loved:
  - mobile app, [#246](https://github.com/linkwarden/linkwarden/issues/246) (91 reactions)
  - drag and drop, [#307](https://github.com/linkwarden/linkwarden/issues/307) (40)
  - nested collections, [#239](https://github.com/linkwarden/linkwarden/issues/239) (40)
  - AI integration, [#396](https://github.com/linkwarden/linkwarden/issues/396) (39)
  - web-clipping extension, [#186](https://github.com/linkwarden/linkwarden/issues/186) (38)
  - SingleFile uploads, [#923](https://github.com/linkwarden/linkwarden/issues/923) (18)
- Loudest open requests:
  - export to HTML, [#587](https://github.com/linkwarden/linkwarden/issues/587) (57)
  - resource usage, [#744](https://github.com/linkwarden/linkwarden/issues/744) (41)
  - mark as read, [#499](https://github.com/linkwarden/linkwarden/issues/499) (36)
  - browser sync, [#245](https://github.com/linkwarden/linkwarden/issues/245) (34)
  - SQLite in Docker, [#690](https://github.com/linkwarden/linkwarden/issues/690) (30)
  - hide cookie banners in captures, [#138](https://github.com/linkwarden/linkwarden/issues/138) (27, open since 2023)
  - archive pages behind a login, [#1138](https://github.com/linkwarden/linkwarden/issues/1138) (25)
  - archive as Markdown, [#904](https://github.com/linkwarden/linkwarden/issues/904)
  - Reddit titles, [#878](https://github.com/linkwarden/linkwarden/issues/878)
- GitHub Discussions are disabled.

### Quality from the tracker
- **Counts.** 1,105 issues: 643 open, 462 closed. Bugs: 199 open vs 176 closed, a ratio of 1.13. In the last 12 months, 249 issues were opened and 73 closed, so the backlog is growing.
- **Time to close.** Median 14.3 days for all issues, 6.0 days for bugs.
- **Maintainer reply.** 40% of issues overall. By year:

  | Year opened | Issues with a reply | Median time to first reply |
  |---|---|---|
  | 2023 | 111/126 | 64 h |
  | 2025 | 104/409 | 35 h |
  | 2026 | 32/187 (17%) | 267 h |

- **Maintainers.** Effectively one: daniel31x13 wrote 537 of the commits since 2025-10-26, and the next contributor wrote 7.
- **Recurring themes (issue titles):**
  - **Mobile share drops or duplicates links:**
    - [#1861](https://github.com/linkwarden/linkwarden/issues/1861): "Sharing via android sharing menu mostly not working"
    - [#1621](https://github.com/linkwarden/linkwarden/issues/1621): share saves a *previously* shared link
    - [#1708](https://github.com/linkwarden/linkwarden/issues/1708): app hangs on a second share
    - [#1807](https://github.com/linkwarden/linkwarden/issues/1807) and [#1511](https://github.com/linkwarden/linkwarden/issues/1511): duplicates on iOS cold start
    - Root cause: PR [#1847](https://github.com/linkwarden/linkwarden/pull/1847) (open) traces a navigation race in `_layout.tsx`. In its test, 0 of 5 cold shares were posted on 1.4.3, against 10 of 10 with the fix.
    - The share screen posts straight to the server and only shows an alert on failure, with no offline queue ([incoming.tsx L41-L77](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/mobile/app/incoming.tsx#L41-L77)).
  - **Worker and crawler failures:**
    - [#1845](https://github.com/linkwarden/linkwarden/issues/1845): 2.16.3 crash-loops because the Playwright binary is missing
    - [#1628](https://github.com/linkwarden/linkwarden/issues/1628): a download page blocks the whole archive queue
    - [#1773](https://github.com/linkwarden/linkwarden/issues/1773): Facebook and Instagram previews broken since 2.15
    - [#1854](https://github.com/linkwarden/linkwarden/issues/1854): zombie processes
    - [#1504](https://github.com/linkwarden/linkwarden/issues/1504): memory spikes on large libraries
  - **Upgrade pain:** [#1257](https://github.com/linkwarden/linkwarden/issues/1257) (migrations fail on 2.11), [#1544](https://github.com/linkwarden/linkwarden/issues/1544), [#1579](https://github.com/linkwarden/linkwarden/issues/1579) (Prisma crash loop).
  - **AI JSON parsing:** [#1576](https://github.com/linkwarden/linkwarden/issues/1576). The code now strips ```` ```json ```` fences ([autoTagLink.ts L131-L134](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/autoTagLink.ts#L131-L134)).

### Code spot-check
- **Structure.** Clean workspace layering: prisma → types → lib/filesystem → router → apps. The documented map is in [AGENTS.md](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/AGENTS.md). The `packages/router` TanStack hooks are shared between web and mobile.
- **Error handling.** Mixed. There is a top-level timeout and "unavailable" marking, but there are also many `console.log` + swallow paths; for example, AI tag failures are only logged ([autoTagLink.ts L179-L182](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/apps/worker/lib/autoTagLink.ts#L179-L182)).
- **Tests.** About 11 `*.test.ts` files, mostly security and API edge cases, plus a thin Playwright login e2e. That is against roughly 60k TS LOC.

### License
AGPL-3.0 ([LICENSE.md](https://github.com/linkwarden/linkwarden/blob/eff7c6b735f6b20a4766745dc86c6bdc52bd9840/LICENSE.md)), covering the mobile app too, since it ships in the same repo. Copying code makes yearn AGPL. Running a modified copy as a network service for other users obliges you to offer them its source; personal use carries no obligation. There is also a commercial Cloud tier (Stripe/IAP, per AGENTS.md).

---

## 2. Readeck (server) + readeck-android (official) + ReadeckApp (community)

### Standout features (code)
- **Content scripts: sandboxed per-site JS inside a Go server.**
  - Scripts run in goja ([runtime.go](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/runtime.go)). Each exports hooks: `isActive`, `setConfig`, `processMeta`, `documentLoaded`, `documentReady` (before readability) and `documentDone`.
  - Scripts can read and write `$.meta`, `$.authors`, `$.type` (photo/video/article) and `$.properties`, which is persisted into the archive.
  - Scripts get an HTTP client (`requests.get/post`, [requests.go](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/requests.go)), and users can override built-ins with `throw abort` ([README](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/README.md)).
  - User script directories come from config ([configs.go L127](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/configs/configs.go#L127), loader [content_scripts.go L35](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/internal/bookmarks/content_scripts.go#L35)).
  - Built-in scripts (1,973 lines): reddit, youtube, instagram, bluesky, fediverse, hackernews, substack, vimeo, t.co, and others ([assets/scripts](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/assets/scripts)).
- **Examples relevant to yearn:**
  - **YouTube transcript.** POSTs to the innertube `youtubei/v1/player` endpoint as an ANDROID client, then picks a caption track (manual before automatic, with language priority) and appends the transcript to the article ([youtube.js L97-L115, L123+](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/assets/scripts/youtube.js#L97-L115)).
  - **Reddit.** Fetches `<post>.json` for title, author, selftext, gallery and `reddit_video`. When the JSON endpoint returns 403 it falls back to DOM parsing, with separate paths for old and new Reddit. It skips processing when the user submitted a selection ([reddit.js](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/assets/scripts/reddit.js)).
  - **Instagram.** Metadata only: it sets type=photo and title/description from meta tags ([instagram.js](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/assets/scripts/instagram.js)). This is weak, and is exactly the gap yearn wants to fill.
- **Site-config corpus.**
  - 2,197 XPath rule files from FiveFilters ftr-site-config, synced by [Makefile L156-L159](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/Makefile#L156) and `tools/ftr`. They are licensed **CC0-1.0** ([REUSE.toml L102-L108](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/REUSE.toml#L102-L108)).
  - Files exist for github.com, linkedin.com, twitter.com and instagram.com ([site-config](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/extract/contentscripts/assets/site-config)).
- **One immutable ZIP per bookmark.** HTML, images and metadata go into a single ZIP that is served directly or converted to EPUB on demand ([README L139-L145](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/README.md#L139); [archive.go](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/internal/bookmarks/archive.go)). Image processing is semaphore-limited to 2 jobs at a time so it cannot freeze the host ([archive.go](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/internal/bookmarks/archive.go)). The resource archiver is a fork of Obelisk and is **MIT**-licensed ([pkg/archiver/README.md](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/archiver/README.md)).
- **The API accepts client-captured content.**
  - `POST /bookmarks` takes `html` plus multiple `resource` file parts, each with a `Location` header ([doc-create.md](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/docs/api/bookmarks/doc-create.md)).
  - `/bookmarks/sync` streams a `multipart/mixed` export (JSON, HTML or Markdown plus resources) for offline clients ([doc-sync.md](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/docs/api/bookmarks/doc-sync.md)).
  - So a yearn phone capture could hand raw HTML and images to Readeck unchanged.
- **Other features:**
  - single binary, SQLite by default ([README L90-L92, L145](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/README.md#L90))
  - highlights and notes ([pkg/annotate](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/pkg/annotate))
  - EPUB and OPDS export
  - importers for Pocket, Wallabag, Linkwarden, Readwise, GoodLinks and Pinboard ([importer/](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/internal/bookmarks/importer))
- **readeck-android: offline add queue.**
  - Adds go to a Room table `queued_bookmark_adds`, and `DrainQueuedAddsWorker` sends them under a `CONNECTED` constraint with exponential backoff and a KEEP-coalesced unique work request.
  - Each row is processed independently. IOException, 5xx and policy blocks count as transient and retry forever; 4xx marks the row FAILED for manual retry ([DrainQueuedAddsWorker.kt L27-L107, L122-L140](https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/app/src/main/java/com/mydeck/app/worker/DrainQueuedAddsWorker.kt#L27-L140)).
  - **Lost-response idempotency.** Before re-POSTing a retry, the repository looks for a bookmark with the same URL created after the original `queuedAt` and adopts it, so retries do not create duplicates ([BookmarkRepositoryImpl.kt ~L483-L531](https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt#L483-L531)).
  - Share uses a bottom sheet with an auto-submit timer ([ShareActivity.kt](https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/app/src/main/java/com/mydeck/app/ui/share/ShareActivity.kt), [README](https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/README.md)).
- **ReadeckApp** (the original the official app was forked from): a `text/plain` share opens the main activity and navigates to the list with the shared text pre-filled ([MainActivity.kt L65-L76](https://github.com/jensomato/ReadeckApp/blob/84537d95871b79d56b614b024a5a93621ad8f413/app/src/main/java/de/readeckapp/MainActivity.kt#L65-L76)). Saving needs the server to be reachable; an offline queue is requested in [#139](https://github.com/jensomato/ReadeckApp/issues/139) and [#137](https://github.com/jensomato/ReadeckApp/issues/137), both open.

### What users love and want (Codeberg reactions)
- **Requests:**
  - Feed reader / subscriptions epic, [#424](https://codeberg.org/readeck/readeck/issues/424) (44 reactions)
  - Save PDF files, [#24](https://codeberg.org/readeck/readeck/issues/24) (28)
  - batch operations, [#5](https://codeberg.org/readeck/readeck/issues/5) (21)
  - external auth / OIDC, [#68](https://codeberg.org/readeck/readeck/issues/68) (21, shipped)
  - refetch a bookmark, [#59](https://codeberg.org/readeck/readeck/issues/59) (17)
  - bookmark notes, [#72](https://codeberg.org/readeck/readeck/issues/72) (17, shipped)
  - ignore duplicates, [#73](https://codeberg.org/readeck/readeck/issues/73) (14)
  - native mobile app, [#273](https://codeberg.org/readeck/readeck/issues/273) (11, now the official Android app)
  - **download videos**, [#217](https://codeberg.org/readeck/readeck/issues/217) (10). Closed. The maintainer called it "a totally different scope" and said he had no time to explore it. Commenters pointed to Karakeep's youtube-dl and a MeTube hand-off. **This is yearn's opening.**
  - rule-based auto-labeling, [#134](https://codeberg.org/readeck/readeck/issues/134)
  - PWA share intent, [#180](https://codeberg.org/readeck/readeck/issues/180)
- **ReadeckApp top requests:** labels at share time, [#76](https://github.com/jensomato/ReadeckApp/issues/76); search, [#70](https://github.com/jensomato/ReadeckApp/issues/70); TTS, [#63](https://github.com/jensomato/ReadeckApp/issues/63).

### Quality from the tracker
- **Readeck server:**
  - 769 issues: 203 open, 566 closed. Bugs: 31 open vs 178 closed, a ratio of 0.17.
  - Median time to close: 4.1 days for all issues, 2.0 days for bugs. In the last 12 months, 240 were opened and 230 closed, so the backlog is flat.
  - Maintainer reply on 576 of 716 issues (80%), median about 8 h, p75 59 h.
  - Commits since 2025-10-26: Olivier Meunier 188 (two identities), then translators and small contributors. mislav is the second most frequent commenter (349 comments).
  - The dominant label is **`Extraction/Error`, with 138 issues**. These are site-specific breakages, usually fixed with a content-script or site-config update. Examples:
    - YouTube intermittently missing info, [#832](https://codeberg.org/readeck/readeck/issues/832), and [#1160](https://codeberg.org/readeck/readeck/issues/1160)
    - fediverse links, [#1103](https://codeberg.org/readeck/readeck/issues/1103), and Mastodon, [#1373](https://codeberg.org/readeck/readeck/issues/1373)
    - xcancel, [#1330](https://codeberg.org/readeck/readeck/issues/1330), closed in 1 day
  - Upgrade regressions are fixed fast:
    - 0.23.3 broke OIDC, [#1354](https://codeberg.org/readeck/readeck/issues/1354): fixed the same day
    - TOTP 500 after the 0.23 upgrade, [#1312](https://codeberg.org/readeck/readeck/issues/1312): fixed in 2 days
  - Rare data-integrity reports:
    - username change corrupts data?, [#1036](https://codeberg.org/readeck/readeck/issues/1036) (open)
    - only 23 of 2,849 links imported, [#1370](https://codeberg.org/readeck/readeck/issues/1370) (open)
- **readeck-android:**
  - 40 issues since 2026-06; 10 open.
  - An offline-loss bug, "Bookmark added when offline are lost in some cases" ([#60](https://codeberg.org/readeck/readeck-android/issues/60)), and no feedback when offline ([#59](https://codeberg.org/readeck/readeck-android/issues/59)) were both fixed within about 2 weeks.
  - Releases: v1.0.1 (2026-09-05), v1.0.2 (2026-10-08).
- **ReadeckApp:**
  - 92 issues: 32 open, 60 closed. Bugs: 1 open vs 17 closed. Median time to close 3.7 days. Maintainer reply on 54% of issues, median 44 h. Solo developer (42 of 49 commits in the last 12 months).
  - Share bugs:
    - [#171](https://github.com/jensomato/ReadeckApp/issues/171): a new task per share
    - [#101](https://github.com/jensomato/ReadeckApp/issues/101): "All articles deleted from app upon offline refresh" (fixed)
    - [#174](https://github.com/jensomato/ReadeckApp/issues/174): initial sync killed by Android on large libraries (open)

### Code spot-check
- **Readeck structure.** `pkg/` holds reusable libraries (extract, archiver, annotate, epub, opds, bleach) and `internal/` holds the app. Files carry REUSE/SPDX headers. Lint config enables errcheck, bodyclose, gocognit and dupl ([.golangci.yml](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/.golangci.yml)).
- **Readeck tests.** 86 `_test.go` files (24.7k LOC) against 268 Go files (49.8k LOC), with fixtures for content scripts and a separate e2e repo. A semaphore guards image work and errors are typed.
- **readeck-android.**
  - Compose, Room, Retrofit, Hilt and WorkManager: 268 Kotlin files, 55k LOC, 89 test files (23k LOC).
  - The repo includes `docs/specs`, a `CODE-AUDIT.md` and a `FORK_DIFFERENCES.md`.
  - The README discloses heavy AI-assisted development behind a written spec → plan → test → audit workflow ([README "A note about vibe coding"](https://codeberg.org/readeck/readeck-android/src/commit/e71da31a8586ef89e6b755b73e9beee748631171/README.md)).
  - The code is well commented and links back to spec sections.
- **ReadeckApp.** Similar stack: 102 files, 8.4k LOC, 17 test files.

### License
- **Server:** AGPL-3.0-only ([LICENSE](https://codeberg.org/readeck/readeck/src/commit/32d05365a157ef4140ebbffaba2d045576e236ad/LICENSE)), with exceptions:
  - `pkg/archiver` (7 files), `pkg/base58`, `pkg/csvstruct`, `pkg/xml2map` and `pkg/glob` are MIT or ISC.
  - The site-config JSON is CC0.

  So the archiver and the site configs can be taken under permissive terms. The content-script runtime and scripts are AGPL.
- **Both Android apps:** GPL-3.0. Copying Kotlin code makes the distributed yearn APK GPL-3.0. Reading them and reimplementing the queue and reconcile design creates no obligation.

---

## 3. Omnivore

### Standout features (code)
- **Rules engine.**
  - Rules fire on these events: `PAGE_CREATED`, `PAGE_UPDATED`, `LABEL_CREATED`, `HIGHLIGHT_CREATED` and `HIGHLIGHT_UPDATED`.
  - Available actions: `ADD_LABEL`, `ARCHIVE`, `DELETE`, `MARK_AS_READ`, `SEND_NOTIFICATION`, `WEBHOOK` and `EXPORT` ([entity/rule.ts L12-L28](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/api/src/entity/rule.ts#L12-L28)).
  - The rule filter is written in the same search grammar. It is evaluated in memory against the event when possible, and falls back to a DB search (`includes:<id> AND (<filter>)`) when it needs indexed fields. Bad filters mark the rule as failed rather than crashing ([trigger_rule.ts L218-L283](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/api/src/jobs/trigger_rule.ts#L218-L283)).
  - This is the most transferable idea for yearn's "auto-organize by topic".
- **Search grammar.** Uses liqe, with fields such as `in: is: type: label: sort: has: saved: read: updated: published:`, relative dates (`today`, `last24hrs`, `this week`), `subscription: rss: language: author: title: note: highlight: site: wordscount: readposition:` ([library_item.ts L202-L500](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/api/src/services/library_item.ts#L202-L500)).
- **Content handlers.** One class per site, each with `shouldPreHandle(url)` and `preHandle()` that return HTML, title and URL ([content-handler.ts](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/content/content-handler/src/content-handler.ts)). There are 22 website handlers and 15 newsletter handlers ([websites/](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/content/content-handler/src/websites), [newsletters/](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/content/content-handler/src/newsletters)).
  - **X** uses unauthenticated `publish.twitter.com/oembed` ([twitter-handler.ts L114, L202-L235](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/content/content-handler/src/websites/twitter-handler.ts#L114-L235)). Thread lookup uses the paid v2 API ([L91](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/content/content-handler/src/websites/twitter-handler.ts#L91)).
  - **TikTok** uses oEmbed. **GitHub** has its own handler.
- **YouTube transcript plus LLM cleanup.** Uses the `youtubei` client to fetch the transcript and chapters, then optionally runs it through an OpenAI prompt from `YOUTUBE_TRANSCRIPT_PROMPT`. Results are cached by transcript hash and prompt hash, with a raw-text fallback ([process-youtube-video.ts L80-L141, L261](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/api/src/jobs/process-youtube-video.ts#L80-L141)). This was requested in [#2973](https://github.com/omnivore-app/omnivore/issues/2973) (23 reactions).
- **Newsletter ingest by email.** IMAP and local mail watchers feed `save_newsletter_email` ([packages/mail](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/mail), [services/save_newsletter_email.ts](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/packages/api/src/services/save_newsletter_email.ts)).
- **Android share.** `SaveSheetActivity` (`text/*`) enqueues `SaveURLWorker` with a `CONNECTED` constraint, so it waits for a network ([SaveSheetActivity.kt L136-L144](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/mobile/android/Omnivore/app/src/main/java/app/omnivore/omnivore/feature/save/SaveSheetActivity.kt#L136-L144)). Any server error returns `Result.failure()` with no retry, and the code mints a fresh random `clientRequestId` inside the worker ([SaveURLWorker.kt L49-L59, L85](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/mobile/android/Omnivore/app/src/main/java/app/omnivore/omnivore/feature/save/SaveURLWorker.kt#L49-L85)). Net effect: a save is lost if the server errors.

### What users love and want
- **Requests:**
  - self-host support, [#25](https://github.com/omnivore-app/omnivore/issues/25) (181 reactions; closed 2024-11-18, when self-hosting became the only option)
  - OPML import/export, [#2870](https://github.com/omnivore-app/omnivore/issues/2870) (63)
  - RSS, [#2029](https://github.com/omnivore-app/omnivore/issues/2029) (56)
  - zero-knowledge E2EE, [#1658](https://github.com/omnivore-app/omnivore/issues/1658) (37)
  - Send to Kindle, [#2142](https://github.com/omnivore-app/omnivore/issues/2142) (36)
  - Zotero, [#977](https://github.com/omnivore-app/omnivore/issues/977) (30)
  - EPUB highlighting, [#1955](https://github.com/omnivore-app/omnivore/issues/1955)
  - Reddit handler, [#1807](https://github.com/omnivore-app/omnivore/issues/1807)
  - tagging at Android share time, [Discussion #2986](https://github.com/omnivore-app/omnivore/discussions/2986)
- **Sentiment after the shutdown:**
  - "Seriously sold out to an AI startup?", [#4459](https://github.com/omnivore-app/omnivore/issues/4459) (141 reactions)
  - "What are some open source alternatives?", [#4462](https://github.com/omnivore-app/omnivore/issues/4462) (49 comments)
  - "How can we help continue the project?", [#4538](https://github.com/omnivore-app/omnivore/issues/4538)

### Quality from the tracker
- **Counts.** 1,343 issues: 8 open, 1,335 closed (725 completed, 610 not planned). 338 of those were closed as `NOT_PLANNED` on 2026-08-25 in one bulk sweep. Bug counts after the sweep (0 open vs 41 closed) mean nothing.
- **Time to close.** Median 223 days overall, inflated by the sweep; 11.2 days for bugs.
- **Maintainer reply, by year opened:**

  | Year opened | Median time to first reply |
  |---|---|
  | 2022 | 8.6 h |
  | 2023 | 44 h |
  | 2024 | 586 h |
  | 2025 | 363 h |

- **New issues are rare and unanswered.** Only 27 issues were opened in 2025 and 9 in 2026. Examples: [#4637](https://github.com/omnivore-app/omnivore/issues/4637), self-host API broken (0 comments); [#4639](https://github.com/omnivore-app/omnivore/issues/4639), Android TTS parity.
- **Active maintainers: one.** Podginator wrote 113 of about 150 commits in the last 12 months. His recent commits are Docker images, Node bumps, env fixes and self-host docs; the rest are dependabot. Android releases resumed after a 2024-07 → 2026-08 gap (android-0.227 to 0.230).
- **Recurring themes** (historic titles): mobile and share issues (224 titles match), capture/readability failures (195), import/export (104).

### Code spot-check
- **Structure.** A large polyglot monorepo: Apollo GraphQL API, Next web, Swift iOS, Kotlin Android, content-fetch (Puppeteer), a Go image proxy, TTS, Logseq/Obsidian plugins.
- **API tests.** 55 test files (11k LOC) against 48k LOC, but no test workflow remains in `.github/workflows`, only image builds and a migration lint ([workflows](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/.github/workflows)).
- **Android.** 144 Kotlin files (16.6k LOC) and one test file. It ships the proprietary PSPDFKit for PDFs ([AndroidManifest.xml L44-L46](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/mobile/android/Omnivore/app/src/main/AndroidManifest.xml#L44)).
- **Error handling.** Log-and-continue throughout the rules and handlers.

### License
AGPL-3.0 ([LICENSE](https://github.com/omnivore-app/omnivore/blob/92caa68f511471e048842000bb0eb14e0c00f5a4/LICENSE)). The terms are the same as Linkwarden's. Note the PSPDFKit dependency: a fork of the Android app needs a commercial PSPDFKit license or that viewer removed.

---

## Closing: what yearn should take

**Linkwarden.** Not a wholesale grab. The share flow is online-only and currently drops links ([#1847](https://github.com/linkwarden/linkwarden/pull/1847)), which is the exact failure yearn exists to avoid. Ideas worth taking:
- A per-tag matrix of which formats to capture.
- A "client uploads a raw capture; server renders screenshot, readable text and preview from that blob instead of the live URL" endpoint. This is the right model for logged-in IG/X/LinkedIn.
- AI tagging in EXISTING mode (pick from your top-N tags, filter the output back to that list) to stop topic sprawl.

All of it is AGPL, so reimplement rather than copy unless the home-server is AGPL anyway.

**Readeck.** The best component donor:
- **Server:**
  - Take the CC0 FiveFilters site configs (2,197 files) and the MIT `pkg/archiver` outright.
  - Copy the *design* of goja content scripts with lifecycle hooks: per-site JS with an HTTP client and fallbacks. The YouTube innertube transcript and Reddit `.json` scripts are near drop-in logic for yearn's fetchers.
  - Model storage on one immutable ZIP per capture.
- **Android:** readeck-android's queued-add worker (transient vs permanent errors, KEEP-coalesced drain, reconcile-before-retry by URL plus queued time) is the template for yearn's capture → sync step. It is GPL-3.0, so reimplement unless yearn goes GPL.
- **Gap:** Readeck explicitly refuses video download ([#217](https://codeberg.org/readeck/readeck/issues/217)), which is the gap yearn fills.

**Omnivore.** Ideas only. The project is in maintenance mode with one maintainer, bulk-closed issues, no test CI, and a proprietary PDF SDK in the Android app. Worth taking:
- The rules model: filter in the search grammar, an event, then actions such as label, webhook or export. It suits auto-organizing on the home-server.
- The rich search-field list (`has:`, `is:`, `in:`, relative dates).
- oEmbed-first handlers for X and TikTok as a no-auth fallback.
- Caching LLM-cleaned YouTube transcripts by transcript hash plus prompt hash.

Avoid its Android share worker: it fails on any server error.
