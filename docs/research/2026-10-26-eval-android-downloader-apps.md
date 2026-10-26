# Evaluation: Android share-and-download apps (NewPipe, Flipper the Ripper, ShareDL, Cobalt)

Date: 2026-10-26. Context and repo list come from [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md). Source was read from shallow clones (read only; nothing was built or run). Every permalink below is pinned to the commit that was read:

| Repo | Commit read | Repo metadata (`gh api repos/…`, 2026-10-26) |
|---|---|---|
| [TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe) | `acb7dfd` (dev, 2026-10-21) | GPL-3.0, 40,299★, 1,477 open issues+PRs, created 2015, pushed 2026-10-23 |
| [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | `711f13b` (dev, 2026-10-23) | GPL-3.0, 2,028★, 179 open, created 2017 |
| [pepperonas/flipper-the-ripper](https://github.com/pepperonas/flipper-the-ripper) | `92f2433` (main, 2026-09-26) | MIT, 5★, 0 issues, 15 open PRs (all Dependabot), created 2026-07-26 |
| [ma-tcha/sharedl](https://github.com/ma-tcha/sharedl) | `2169675` (master, 2026-10-09) | **no license**, 0★, 0 issues, 5 commits, created and last pushed 2026-10-09 |
| [hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt) | `e9ac518` (main, 2026-10-26) | AGPL-3.0, 9★, 2 issues, created 2026-07-12, pushed 2026-10-25 |

## Summary

| Project | Best at | Quality | License | Grab verdict |
|---|---|---|---|---|
| **NewPipe + NewPipeExtractor** | The share router: a transparent activity that resolves the link and then asks "what do you want to do with this?", with *Always / Just once*. Also a download manager that re-extracts expired media URLs to resume, and a clean per-service extractor plugin model. | **A−**: 11 years old, CI with checkstyle, lint and Sonar, triage within hours. Held back by perennial YouTube breakage, 1,477 open, and legacy Java/RxJava with Java-serialized download state. | GPL-3.0 (both); "NewPipe" is a registered trademark | **Ideas, plus small re-implemented patterns.** Copying code makes yearn GPL, and the extractor covers none of yearn's target sites. |
| **Flipper the Ripper** | Getting Instagram, TikTok and Facebook media on-device. A hidden WebView calls Instagram's own media API same-origin, with a per-platform engine chain (on-device yt-dlp → WebView → optional self-hosted server) and a self-healing yt-dlp update. | **B**: 458 tests, an 80% Kover gate, detekt and spotless in CI; reasons for decisions documented in the code. But it is a solo project written almost entirely with Claude, has no outside users or issues, Dependabot PRs fail CI unmerged, and there has been no main commit since 2026-09-26. | MIT | **Best component donor.** Lift the WebView extractor, cookie merge, engine routing and backend SSRF guard, keeping the notice. Do not adopt it wholesale: it is a downloader with no knowledge store. |
| **ShareDL** | Being minimal. Share → yt-dlp → re-share sheet. When yt-dlp's error text says login is needed, it opens an Instagram WebView login and then resumes the same link automatically. | **D**: one 223-line Activity, no tests, French UI strings hardcoded, a download executor tied to the Activity, the output file found by mtime scan. A one-afternoon project. | **None** (all rights reserved) | **Ideas only.** You may read it, not copy it. |
| **Cobalt (hanan-bhatti)** | Breadth of *capture surfaces*: share sheet for text, image and video, an edge-swipe SideDock overlay, Quick Settings OCR tile, widget quick actions, Gemini region-OCR and voice memos. | **C−**: 46.5k lines of Kotlin with 6 test files (mostly templates), `com.example` namespace from a Google AI Studio scaffold, and 2–2.5k-line ViewModels duplicated per flavor. It uses `fallbackToDestructiveMigration` in a knowledge archive, and the README claims "Encrypted" SQLite with no SQLCipher present. The maintainer is responsive. | AGPL-3.0 | **Ideas only.** Take the capture-surface ideas and the item schema; avoid its intake mistakes (it drops share text, and a share is only a draft until Save). |

---

## 1. NewPipe (app) + NewPipeExtractor (library)

### Standout features (from source)

**Share router: "choose action on share."** [`RouterActivity.java`](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java) is the best share-receiver UX in this set.
- **Invisible host window.** The activity makes its window fully transparent and non-touchable (`FLAG_NOT_TOUCHABLE` plus `alpha = 0` to satisfy Android 12's rules) while the network lookup runs. The user stays visually in the source app until a dialog is needed ([L120–141](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L120-L141)).
- **Link resolution.** The URL is resolved off the main thread to `(service, LinkType ∈ {STREAM, CHANNEL, PLAYLIST, NONE})` ([L224–260](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L224-L260)).
- **Choice filtering.** The offered actions are filtered by what that service and link type can actually do (media capabilities, external-player constraints). With 0 or 1 valid choice it skips the dialog entirely ([L298–370](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L298-L370)).
- **Always / Just once.** The dialog has *Always* and *Just once* buttons. *Always* persists the choice as `preferred_open_action`, and tapping the same radio item twice confirms it ([L412–470](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L412-L470)).
- **Heavy work leaves the UI.** It is handed to a foreground `FetcherService`, so the router can `finish()` immediately ([L609–668](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L609-L668), [L921–948](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L921-L948)).
- **Unsupported URLs.** These get "Open in browser / Share / Cancel" rather than a silent failure ([L283–296](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L283-L296)).
- **URL extraction.** It uses AOSP's `WEB_URL_WITH_PROTOCOL` pattern and takes the first match ([`UrlFinder.kt`](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/util/urlfinder/UrlFinder.kt)). It is still lossy: the rest of the shared text is discarded ([L1062–1077](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/RouterActivity.java#L1062-L1077)).

**Extractor plugin architecture.** NewPipeExtractor is a pure-Java library with no Android dependency ([`StreamingService.java`](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/StreamingService.java)).
- **Service contract.** Each service supplies `LinkHandlerFactory`s (`acceptUrl`, `getId`, `getUrl`) for stream, channel, playlist and comments, plus extractors for each kind ([L136–242](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/StreamingService.java#L136-L242)).
- **URL routing.** `NewPipe.getServiceByUrl` asks every registered service in turn ([NewPipe.java L93](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/NewPipe.java#L93-L100)). `getLinkTypeByUrl` first follows Google redirect wrappers ([L323–338](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/StreamingService.java#L323-L338)).
- **Pluggable HTTP.** The app injects its own `Downloader` (OkHttp in the app) ([Downloader.java L19, L244](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/downloader/Downloader.java#L244)).
- **Rich `StreamExtractor` metadata.** It returns description, tags, upload date, uploader, chapters (`getStreamSegments`), subtitles, licence, category and `MetaInfo` ([getter list](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamExtractor.java#L106)). That is the "caption/author" shape yearn wants, but only for 5 services.
- **Service coverage.** The services are YouTube, SoundCloud, media.ccc.de, PeerTube and Bandcamp ([ServiceList.java L38–42](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/main/java/org/schabi/newpipe/extractor/ServiceList.java#L38-L42)). None of yearn's targets (Instagram, X, Reddit, LinkedIn, GitHub) is included.
- **Tests replay recorded HTTP.** `RecordingDownloader` records real request/response pairs to JSON, and `MockDownloader` replays them, selected by `-Ddownloader=` ([RecordingDownloader.java](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/test/java/org/schabi/newpipe/downloader/RecordingDownloader.java), [DownloaderFactory.java](https://github.com/TeamNewPipe/NewPipeExtractor/blob/711f13b15be314286a0e0a019302ad540c8f0ed7/extractor/src/test/java/org/schabi/newpipe/downloader/DownloaderFactory.java)). 129 test classes back this up with 798 recorded fixture files.

**Background download manager (`us.shandian.giga`, about 6.8k lines).**
- **Block-based multi-threaded resume.** It uses `blocks[]` with `threadCount = 3` ([DownloadMission.java L116–135](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/get/DownloadMission.java#L116-L135)).
- **Post-processing.** It muxes and converts after download (WebM/MP4 mux, TTML→SRT).
- **Re-extraction on expired URLs.** When a media URL expires or 403s mid-download, `DownloadMissionRecover` re-runs the extractor on the *source page URL* ([L74–75](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/get/DownloadMissionRecover.java#L74-L75)). It then picks the same rendition again using the saved `MissionRecoveryInfo`, matching format, audio track, bitrate and subtitle language ([MissionRecoveryInfo.kt](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/get/MissionRecoveryInfo.kt), [resolveStream L125](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/get/DownloadMissionRecover.java#L125-L186)). This is the most transferable idea for yearn's server: store the page URL plus a rendition descriptor, never just the CDN URL.
- **Weak point: persistence.** Mission state is persisted with Java `ObjectOutputStream` ([Utility.java L69–72](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/util/Utility.java#L69-L72); `serialVersionUID` last bumped in 2019, [DownloadMission.java L36](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/us/shandian/giga/get/DownloadMission.java#L36)).

**Challenge/session handling.**
- `ReCaptchaActivity` opens the challenge in a WebView, then hands the resulting cookies to the downloader ([ReCaptchaActivity.java L143–157](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/error/ReCaptchaActivity.java#L143-L157)).
- The app also generates YouTube PO tokens in a headless WebView ([`util/potoken/`](https://github.com/TeamNewPipe/NewPipe/tree/acb7dfd658a8478686d0f8a0c389269d0ca8df95/app/src/main/java/org/schabi/newpipe/util/potoken)).

### What users love and want

Sorted by total reactions (`gh api search/issues -f q='repo:TeamNewPipe/NewPipe is:issue' -f sort=reactions`):
- **YouTube breakage dominates.** Top items: [#2723](https://github.com/TeamNewPipe/NewPipe/issues/2723) "My Google account got suspended because of NewPipe" (260 reactions); [#9223](https://github.com/TeamNewPipe/NewPipe/issues/9223) channel pages empty (145 👍); [#7734](https://github.com/TeamNewPipe/NewPipe/issues/7734) "No video will play" (129 👍).
- **Feature requests.**
  - [#1051](https://github.com/TeamNewPipe/NewPipe/issues/1051): ports to other platforms (open, 99 👍).
  - [#1058](https://github.com/TeamNewPipe/NewPipe/issues/1058): download an entire playlist (91 👍).
  - [#5482](https://github.com/TeamNewPipe/NewPipe/issues/5482): bulk/queue downloads (open, 60 👍).
  - [#5325](https://github.com/TeamNewPipe/NewPipe/issues/5325): sync between devices (open, 46 👍).
- **Downloader wishes relevant to yearn.**
  - [#4341](https://github.com/TeamNewPipe/NewPipe/issues/4341): add metadata to downloaded files (open, 32 reactions).
  - [#5534](https://github.com/TeamNewPipe/NewPipe/issues/5534): download captions together with the video (open).
  - [#9962](https://github.com/TeamNewPipe/NewPipe/issues/9962): "don't ask for format every time, just download".
- **Share-menu requests.** [#4779](https://github.com/TeamNewPipe/NewPipe/issues/4779) asked for an Enqueue button in the "Always ask" share menu (closed, shipped). People do use the action chooser.
- **Extractor.** The top requests are new services: [#434](https://github.com/TeamNewPipe/NewPipeExtractor/issues/434) Odysee (73), [#427](https://github.com/TeamNewPipe/NewPipeExtractor/issues/427) Invidious (55, open), [#79](https://github.com/TeamNewPipe/NewPipeExtractor/issues/79) PeerTube.

### Quality from the tracker and history

- **NewPipe, last 300 issues (2026-04-20 → 2026-10-25).**
  - 124 open / 176 closed.
  - Of the 176 closed, 125 are labelled duplicate, "template ignored", "template missing" or invalid. Bots and triagers close those within hours (median close 0.9 days overall).
  - Genuine `bug`-labelled closures: n=22, median 5.7 days.
  - Recurring themes: YouTube extraction (403, SABR, PO tokens: [#13824](https://github.com/TeamNewPipe/NewPipe/issues/13824), [#12248](https://github.com/TeamNewPipe/NewPipe/issues/12248)), player regressions, download-format menu freezes ([#13924](https://github.com/TeamNewPipe/NewPipe/issues/13924)).
- **NewPipeExtractor, last 300 issues (2021-05 → 2026-10).** 88 open / 212 closed; real bugs closed n=45, median 22.7 days.
- **Maintainers (non-merge commits in the last 12 months).**
  - App: Aayush Gupta (159), DustDFG (58), tobigr/TobiGr (101 combined), Ida Delphine (50), Stypox.
  - Extractor: Tobi (64), AudricV (25), Stypox, FineFindus.
- **Release cadence.** Roughly monthly: v0.28.6 (2026-05-01) → v0.29.1 (2026-08-15).
- **Rewrite in progress.** A Compose rewrite is tracked in [#11198](https://github.com/TeamNewPipe/NewPipe/issues/11198) and lives on the `refactor` branch.

### Code spot-check

- The app mixes 280 Java and 154 Kotlin files, with RxJava throughout.
- CI runs `assembleContinuous lintContinuous testDebugUnitTest` plus a Sonar job, and is green on dev as of 2026-10-23 ([ci.yml](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/.github/workflows/ci.yml)).
- Checkstyle config sits in-repo.
- App test coverage is thin (31 test files). The extractor is the well-tested half.
- `RouterActivity` (1,078 lines) is careful but dense, with config-change workarounds via a retained `PersistentFragment`.

### License

GPL-3.0 for both repositories.
- Copying any code into yearn makes yearn's distributed APK GPL-3.0 (source offer, same license). Linking NewPipeExtractor as a library has the same effect.
- The README states "NewPipe" is a registered word mark with a [trademark policy](https://newpipe-ev.de/policy/trademark/), and that putting NewPipe or any fork on Google Play violates Play's terms ([README L30–32](https://github.com/TeamNewPipe/NewPipe/blob/acb7dfd658a8478686d0f8a0c389269d0ca8df95/README.md?plain=1#L30-L32)).
- Re-implementing the *patterns* in your own code carries no obligation.

---

## 2. Flipper the Ripper (pepperonas)

### Standout features (from source)

**WebView extractor for Instagram, TikTok and Facebook** ([`WebViewExtractor.kt`](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt)). The header comment gives the rationale ([L26–43](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L26-L43)):
- Instagram fingerprints TLS and hydrates the media URL with JavaScript.
- A hidden Android WebView is real Chromium on the phone's *residential* IP, so it gets what a datacenter server gets degraded.

How it extracts:
- It loads the `/reel/<code>/embed/captioned/` page. An injected script calls Instagram's own `/api/v1/media/<id>/info/` **same-origin** with `credentials:'include'` ([L193–213](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L193-L213)).
- That fetch carries the signed-in session for free and returns the authorized `video_versions` URL.
- For TikTok it parses `__UNIVERSAL_DATA_FOR_REHYDRATION__` ([L214–232](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L214-L232)).
- For Facebook it falls back to regexes such as `browser_native_hd_url` ([L233–259](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L233-L259)).
- It races this against `shouldInterceptRequest` sniffing of `.mp4` CDN fetches ([L79–89](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L79-L89), timeouts at [L109–113](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L109-L113)).
- It emits `FLIP-DIAG:` console diagnostics so a timeout names its cause ([L261–280](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/WebViewExtractor.kt#L261-L280)).

What it does *not* do (gap for yearn):
- It keeps only `video_url`, a title of the form "Video by <username>" and the thumbnail.
- The same API response contains the full caption, timestamp and carousel items, which it throws away.

**Instagram session done right.**
- Sign-in happens on Instagram's own login page in a WebView. Only the shared `CookieManager` session is kept, and the app never sees a password ([InstagramSession.kt](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/InstagramSession.kt)).
- `InstagramCookies.merge` adds `sessionid/ds_user_id/csrftoken` from the instagram.com jar to CDN requests. Without them, gated CDN fetches 403 ([L19](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/InstagramCookies.kt#L19)).
- The sign-in is offered *at the error* as a wizard that retries the link afterwards (commit [`67f1540`](https://github.com/pepperonas/flipper-the-ripper/commit/67f1540)).

**Engine chain with a self-hosted fallback.**
- `EngineRouting` sends YouTube, X and Dailymotion to on-device yt-dlp, and Instagram, TikTok and Facebook to the WebView. A configured server is the fallback, or goes first if the user prefers it ([EngineRouting.kt L25–51](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/EngineRouting.kt#L25-L51)).
- `RoutingYtDlpEngine` tries each engine in order. It stops on terminal errors (private, unavailable, region-blocked) ([L107–112](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/RoutingYtDlpEngine.kt#L107-L112)).
- Self-healing: a failure that looks like a stale extractor triggers one forced yt-dlp update and one retry, but only if the update actually installed something new ([L63–76](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/RoutingYtDlpEngine.kt#L63-L76), [StaleEngineRetry.kt](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/StaleEngineRetry.kt#L20-L34)).
- Correction to the prior survey: yt-dlp is *checked* on app start and on every share, but throttled to once per 12 h ([UpdateCoordinator.kt L84](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/update/UpdateCoordinator.kt#L84)).

**Server backend** ([backend/app.py](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/backend/app.py)).
- It is about 320 lines of FastAPI: `POST /api/jobs` → poll → `GET /file`, behind an `X-API-Key`, with yt-dlp, `curl_cffi --impersonate chrome` and a JS runtime.
- Its SSRF guard is worth copying for any home server that fetches user-supplied URLs. It matches exact hostnames or subdomains (not substrings), then rejects private, loopback, link-local and reserved IPs after DNS resolution ([L88–112](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/backend/app.py#L88-L112)).

**Share-target polish.**
- It registers as a *direct share target* (`<share-target>` plus a long-lived dynamic shortcut), so it can appear in the sheet's top row ([shortcuts.xml](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/res/xml/shortcuts.xml), [ShareTargets.kt L24–44](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/share/ShareTargets.kt#L24-L44)).
- It accepts both `text/plain` and `text/*` ([manifest L49–60](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/AndroidManifest.xml#L49-L60)).
- `ShareLinkHandler` is application-scoped. Its comment documents a measured 5/5 bug: shares were silently dropped when a ViewModel-scoped consumer never composed after process death ([L21–34](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/ui/ShareLinkHandler.kt#L21-L34)). That is exactly the class of bug yearn must avoid.
- **Still lossy, though.**
  - `resolveUrl(text) ?: return` silently drops any share without a *supported-platform* URL ([L73](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/ui/ShareLinkHandler.kt#L73)).
  - The surrounding text is discarded ([UrlParser.kt L26–34](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/domain/util/UrlParser.kt#L26-L34)).

**Download queue.**
- A single unique foreground (`dataSync`) WorkManager job drains a Room-backed queue in real order ([DownloadQueueWorker.kt L18–27](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/work/DownloadQueueWorker.kt#L18-L27)).
- It requeues work interrupted by a killed process and resumes `.part` files (commit [`4d7df1b`](https://github.com/pepperonas/flipper-the-ripper/commit/4d7df1b)).

### What users love and want

- There is no signal. GitHub search returns 0 issues and there are no discussions.
- All 24 PRs are from Dependabot. 15 are open and the recent ones fail CI ([runs](https://github.com/pepperonas/flipper-the-ripper/actions/workflows/ci.yml): 5 consecutive `failure` on dependabot branches, 2026-09-27 → 10-18).
- With 5 stars, the only "demand" evidence is the author's own CHANGELOG.

### Quality from history

- **Velocity and authorship.** 87 commits, 2026-07-26 → 2026-09-26, with 14 releases (v1.0 → v1.14.0). About 65 commits carry `Co-Authored-By: Claude …` trailers or Claude-Session lines (`git log --format=%B`), so it is essentially AI-written under one author.
- **Discipline is unusually good for its size.**
  - 458 `@Test`s across unit and instrumented tests.
  - CI runs spotless, detekt, unit tests, a ≥80% Kover coverage gate and assembleDebug ([ci.yml](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/.github/workflows/ci.yml)). It is green on main.
  - A design spec sits in [docs/specs](https://github.com/pepperonas/flipper-the-ripper/tree/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/docs/specs).
  - Self-correcting commits such as [`379ce68`](https://github.com/pepperonas/flipper-the-ripper/commit/379ce68) "make the ordering test able to fail" and [`2e023d2`](https://github.com/pepperonas/flipper-the-ripper/commit/2e023d2) "the lint comment claimed a CI pass that does not exist".
- **Risk.** It has a bus factor of 1. Since the last main commit (2026-09-26) the only activity has been unmerged Dependabot PRs. Website and marketing commits are about a third of the late history.

### Code spot-check

- About 10k lines of main Kotlin: Hilt, Room, WorkManager, Compose and youtubedl-android.
- Engines are layered behind a `YtDlpEngine` interface with typed `DownloadError`s ([ErrorClassifier.kt](https://github.com/pepperonas/flipper-the-ripper/blob/92f2433069a059ccccfd46ffddfefdcdf8edbc3b/app/src/main/kotlin/io/celox/flipperripper/data/engine/ErrorClassifier.kt#L15)).
- Comments explain *why*, often with field evidence.
- The weak spot is brittleness that cannot be removed: the IG and Facebook regexes and the hardcoded `X-IG-App-ID` will rot.

### License

MIT. Copy freely, keeping the copyright and permission notice in the source or in the APK's notices. No copyleft applies.

---

## 3. ShareDL (ma-tcha)

### Standout features (from source)

The entire app is 3 Kotlin files, 346 lines.
- **Flow.** Share → first `https?://\S+` → yt-dlp with `--no-playlist -f best[ext=mp4]/best` → the Android share sheet opens with the downloaded file ([MainActivity.kt L117–126, L128–165](https://github.com/ma-tcha/sharedl/blob/21696754e5e995b5dcebdb08f00e8ec082fc2df6/app/src/main/java/app/sharedl/MainActivity.kt#L117-L165)). It is a pure "share-and-done" relay.
- **Login on demand.** On failure it greps yt-dlp's stderr for `login required` or `rate-limit reached`. On a match it opens the Instagram WebView login, stashes the link, and automatically restarts the download when login returns `RESULT_OK` ([L169–181, L189–198](https://github.com/ma-tcha/sharedl/blob/21696754e5e995b5dcebdb08f00e8ec082fc2df6/app/src/main/java/app/sharedl/MainActivity.kt#L169-L198)). This is the same idea Flipper reached in v1.12, in 30 lines.
- **Cookie export for yt-dlp.** It converts the WebView's instagram.com cookies to a Netscape `cookies.txt` in `noBackupFilesDir` (excluded from backups) and passes it via `--cookies` ([InstagramSession.kt L11–44](https://github.com/ma-tcha/sharedl/blob/21696754e5e995b5dcebdb08f00e8ec082fc2df6/app/src/main/java/app/sharedl/InstagramSession.kt#L11-L44)). This is the bridge from "WebView login" to a cookie file that yt-dlp or gallery-dl can use, on phone or server.
- **Engine library.** It uses `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2`, which per its POM is MIT and Chaquopy/Python 3.13 based, with a paid "Pro" adding curl-cffi. The upstream repo has 3★ and was last pushed 2026-07-12 ([Maven POM](https://repo1.maven.org/maven2/dev/ffmpegkit-maintained/yt-dlp-android/2.0.2/yt-dlp-android-2.0.2.pom)). It is an alternative to youtubedl-android, but immature.

### Users / tracker

None: 0 issues, 0 PRs, 0 stars.

### Quality

- **History.** 5 commits within one hour on 2026-10-09 by "OFF THE FRAME - by Matcha". The initial commit [`577cf1f`](https://github.com/ma-tcha/sharedl/commit/577cf1f) added all 573 lines. The rest were a rename, CI fixes and a README translation. There have been no commits since.
- **Defects.**
  - The download runs on a `newSingleThreadExecutor` owned by the Activity ([L22](https://github.com/ma-tcha/sharedl/blob/21696754e5e995b5dcebdb08f00e8ec082fc2df6/app/src/main/java/app/sharedl/MainActivity.kt#L22)), with no foreground service. Backgrounding risks the process being killed mid-download.
  - The output file is identified by "newest file modified after start" ([L160–161](https://github.com/ma-tcha/sharedl/blob/21696754e5e995b5dcebdb08f00e8ec082fc2df6/app/src/main/java/app/sharedl/MainActivity.kt#L160-L161)).
  - `pendingLinkForLogin` is lost on process death.
  - Cookies are written with expiry 0 (session).
  - UI strings are hardcoded French.
  - There are no tests.
- **Builds.** Release CI exists and signs builds for Obtainium.

### License

**No license file and no SPDX** (GitHub API `license: null`). By default that is "all rights reserved": you may not copy, modify or redistribute its code. GitHub's ToS only lets other users view it and fork it *on GitHub*. Treat it as reference reading only, and re-implement any idea independently. The ideas themselves are trivial and not protectable.

---

## 4. Cobalt (hanan-bhatti), the "universal capture" archive, not imputnet's cobalt

### Standout features (from source)

**Many capture entry points into one item store.**
- Share intents for `text/plain`, `image/*` and `video/*` ([manifest](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/AndroidManifest.xml#L51-L67), handled at [MainActivity.kt L815–825](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/MainActivity.kt#L815-L825)).
- Widget and shortcut actions such as `ACTION_QUICK_TEXT/LINK/IMAGE/CODE/AUDIO/OCR` ([L836–851](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/MainActivity.kt#L836-L851)).
- A Quick Settings OCR tile ([manifest L131–140](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/AndroidManifest.xml#L131-L140)).
- An overlay "SideDock" (`SYSTEM_ALERT_WINDOW`, 791-line [CobaltSideDockService.kt](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/CobaltSideDockService.kt)) for capture without leaving the current app.
- MediaProjection screen grab with a drag-to-select region sent to Gemini OCR ([OcrCaptureActivity.kt](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/OcrCaptureActivity.kt)).
- Gemini voice-memo transcription ([README L109–131](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/README.md?plain=1#L109-L131)).

**Item schema.** One `saved_items` table with a type, content, OCR `extractedText`, OpenGraph `linkTitle/linkDescription/linkImage`, folders as JSON, soft-delete and sync flags, plus TMDB movie fields ([SavedItemEntity.kt L24–58](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/data/local/SavedItemEntity.kt#L24-L58)). Link previews are Jsoup OpenGraph scrapes ([foss CobaltRepository.kt L800–824](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/foss/kotlin/com/example/data/repository/CobaltRepository.kt#L800-L824)). There is no media download for social links.

**Flavor split.** FOSS (F-Droid, no Google, manual JSON backup) and Play (Firebase sync) ([README L59–80](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/README.md?plain=1#L59-L80)). It is on [F-Droid](https://f-droid.org/packages/com.hanan_bhatti.cobalt/).

**Anti-patterns for a raw-capture app.**
- *The shared text is thrown away.* If a URL is found, only the URL plus `EXTRA_SUBJECT` (or "Shared Link") is kept ([foss CobaltViewModel.kt L834–849](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt#L834-L849)). "Is it code?" is decided by `contains("{")` ([L852](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt#L852)).
- *A share is only a draft.* It is persisted only when the user taps Save on the CaptureScreen ([CaptureScreen.kt L140–152](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt#L140-L152)). Backing out loses it.
- *Search is in-memory.* It is weighted fuzzy matching over every item, with a hardcoded synonym list. Tokenization splits on `[^a-zA-Z0-9]`, so Urdu and Arabic text (which the README advertises RTL support for) cannot be term-matched ([L1671–1677](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt#L1671-L1677), [L1718–1729](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/foss/kotlin/com/example/ui/viewmodel/CobaltViewModel.kt#L1718-L1729)). There is no FTS.
- *Data-loss risk.* The database is Room v6, built with `fallbackToDestructiveMigration(true)` and no migrations ([AppDatabase.kt L26, L42](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/src/main/kotlin/com/example/data/local/AppDatabase.kt#L26-L43)). A future schema bump wipes the user's archive.
- *Inaccurate claim.* The README advertises "Encrypted Local SQLite" ([L70](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/README.md?plain=1#L70)), but no SQLCipher or `SupportFactory` appears anywhere in the build or source.

### What users love and want

There are 2 issues in total.
- [#19](https://github.com/hanan-bhatti/cobalt/issues/19): a bug where the Dynamic Color toggle was inverted. The owner answered and closed it in 2.9 days.
- [#20](https://github.com/hanan-bhatti/cobalt/issues/20) (open, 2026-10-26): "great application for maintaining your own knowledge archive" plus a request for delete in the edit menu and editable descriptions on link items.

### Quality from history

- **Origin.** 276 commits, 2026-07-13 → 2026-10-26, in bursts (34 commits on 2026-07-21).
  - The first "ci" commit [`761e937`](https://github.com/hanan-bhatti/cobalt/commit/761e937) dumped 9,210 lines across 79 files.
  - `namespace = "com.example"` ([app/build.gradle.kts L20](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/app/build.gradle.kts#L20)) and [`metadata.json`](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/metadata.json) (`MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`) are Google AI Studio app-scaffold markers.
  - Agent artifacts in the repo: [`memory.md`](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/memory.md) and [`plans/`](https://github.com/hanan-bhatti/cobalt/tree/e9ac518590ec961457c5d33b7577538f0cbe3e18/plans).
  - 12 commits are co-authored by `google-labs-jules[bot]`, and PRs #5 and #18 are by Copilot.
- **Releases.** It was renamed from "Second Brain" (v1.0.0, 2026-08-04) to "Cobalt" (v2.0.0, 2026-10-07). Latest is v2.2.1 (2026-10-25), and the Build Release and F-Droid CI runs are green.
- **Open security PR.** [#17](https://github.com/hanan-bhatti/cobalt/pull/17), "Fix overly broad Firebase Storage rule", is still open. Separately, the `feedback_attachments` path allows unauthenticated image uploads up to 10 MB ([storage.rules](https://github.com/hanan-bhatti/cobalt/blob/e9ac518590ec961457c5d33b7577538f0cbe3e18/storage.rules)).

### Code spot-check

- 122 Kotlin files, 46.5k lines, of which 7 files run over 1,500 lines.
- The FOSS and Play flavors each carry a full copy of the repository (911 / 1,813 lines) and ViewModel (1,967 / 2,547 lines) rather than abstracting the flavor-specific parts.
- 6 test files, three of them templates (`ExampleUnitTest`, `GreetingScreenshotTest`, `ExampleRobolectricTest`).
- The UI polish is high: Material 3 Expressive and morph animations. The data layer is the weak half.

### License

AGPL-3.0.
- Copied code makes yearn AGPL. Distributing the APK requires offering source under AGPL.
- If AGPL code ends up in yearn's *home server*, then anyone interacting with the server over a network must be offered its source, and that obligation applies to modified versions.
- Ideas carry no obligation.

---

## Closing: what yearn should take

**NewPipe / NewPipeExtractor: ideas plus re-implemented patterns, no code.**
- Copy the *RouterActivity UX* in your own code:
  - an invisible window while resolving;
  - per-link-type valid actions, auto-skipping the dialog when there is only one;
  - *Always / Just once* defaults.
  - For yearn, the defaults would be along the lines of "capture silently", "capture + fetch now" and "capture + tag".
- Copy the *ReCaptcha → WebView → hand cookies to the HTTP layer* pattern.
- Copy the server-side idea from `DownloadMissionRecover`: persist the *page URL plus a rendition descriptor*, and re-extract when the CDN URL expires.
- Model the server's per-platform fetchers on `LinkHandlerFactory.acceptUrl → LinkType → Extractor`, and test them with record/replay HTTP fixtures.
- Do not adopt the extractor itself: it is GPL and has zero overlap with Instagram, X, Reddit, LinkedIn or GitHub.

**Flipper the Ripper: best component donor (MIT); not a wholesale grab.**
- Take `WebViewExtractor` and its Instagram path (same-origin `/api/v1/media/<id>/info/`), and extend it to keep the caption, owner, timestamp and carousel. This is likely the best on-device way to get Instagram captions with the user's own session.
- Take `InstagramCookies.merge` and the sign-in-at-error wizard flow.
- Take `EngineRouting` / `RoutingYtDlpEngine` / `StaleEngineRetry` as the phone ↔ home-server fallback shape.
- Take the backend's SSRF guard and job API as the starting skeleton for yearn's fetch server.
- Take the direct-share shortcut and the application-scoped share consumer. Fix its lossy intake: never drop unsupported links or the surrounding text.

**ShareDL: ideas only (no license).**
- Two ideas worth re-implementing yourself:
  - trigger login from yt-dlp's error text, then auto-resume the pending share;
  - export WebView cookies as a Netscape `cookies.txt` in `noBackupFilesDir`.
- The second is the simplest bridge from phone login to yt-dlp or gallery-dl on the home server, if cookies are synced there deliberately.
- Note `yt-dlp-android` (Chaquopy, MIT) as an engine to watch, not to depend on yet.

**Cobalt: ideas only (AGPL, low data-layer quality).**
- Borrow the capture-surface breadth: Quick Settings tile, widget quick actions, edge dock, region-OCR via MediaProjection.
- Borrow the item fields (OG title, description and image alongside the raw content).
- Treat it as a checklist of what *not* to do in yearn's intake:
  - persist the raw share immediately, before any UI;
  - keep the full `EXTRA_TEXT`, subject and stream, not just the first URL;
  - write real Room migrations;
  - use FTS with Unicode tokenization.
