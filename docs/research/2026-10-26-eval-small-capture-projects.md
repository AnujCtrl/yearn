# Evaluation: three small capture projects (Sharewerk, MemGallery, memex)

Research date: 2026-10-26. Companion to [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md); this doc does not repeat its landscape.

Method: shallow clones at the commits below, read but never built or run. Repo metadata, issues, PRs, releases and commit history came from `gh` on 2026-10-26. Issue "praise" was ranked by reactions (`reactionGroups`, plus `search/issues?...sort:reactions-+1`). One release APK (MemGallery v0.1.0) was downloaded, unzipped, and its `classes*.dex` string tables grepped for a token prefix. The APK was then deleted and nothing was executed.

Pinned commits, which all permalinks use:
- Sharewerk `59be293` = https://github.com/Azentrius/sharewerk/tree/59be293f1538c5ae4179249d6ec513cf7a1de768 (shortened below to **SW/**)
- MemGallery `ba9a258` = https://github.com/Past-da-king/MemGallery/tree/ba9a2581a70479f27618deb27e66416ad94de382 (**MG/**; the app lives under `MemGallery/app/src/main/java/com/example/memgallery/`, shortened to **MG/…/**)
- memex `56cda94` = https://github.com/hasmeni/memex/tree/56cda949fd2517dd86ecd9b196ca82bbf4f92245 (**MX/**)

---

## Verdicts

| Project | What it is | License | Health | Verdict |
|---|---|---|---|---|
| **Sharewerk** (Azentrius) | Self-hosted FastAPI and PWA. Takes a shared Instagram or Facebook link and returns all images, video key frames, a Whisper transcript and a caption/author/date text file | Apache-2.0. The name and icon are reserved trademarks | 7 days old, 20 commits, 19 self-merged PRs, 3 releases, 162 tests and CI. 0 stars, 0 issues | **Grab specific components.** Take the downloader orchestration, the cookie handling, URL canonicalisation and Whisper/key-frame code into the phase 2 server. Do not fork the app: it is IG/FB only, a pass-through with 24 h retention, and has no store |
| **MemGallery** (Past-da-king) | Kotlin/Compose "memory bank": share/overlay/tile/widget capture, then one structured LLM call per memory (title/summary/tags/actions/collections), a chat agent with tool calls, and a substring search | MIT | 103 stars, 9 forks, 16 releases. History was rewritten in May 2026. Only template tests. A GitHub PAT shipped inside a release APK | **Ideas only.** The licence would allow copying, but the capture path is the wrong shape for yearn (network before persist, URIs not fully copied). "Semantic search" does not exist in the code. Code quality is low |
| **memex** (hasmeni) | FastAPI+SQLite link board rendered as a D3 force-directed "mind map" (root → category → link), plus two small Kotlin share apps | **None.** No LICENSE file and the API reports `license: null`. The README just says "MIT" | 12 commits, of which 11 are README/doc edits, over 6 days, then silent since 2026-07-12. 7 stars, 0 issues, no tests. Generated with Kiro | **Ideas only, and avoid the code.** It has no licence grant and the mind map is just a two-level category tree |

---

## 1. Sharewerk: Azentrius/sharewerk

### What this project does best

1. **A downloader cascade that picks tool order by post type and keeps partial results.** Instagram posts and Facebook photo posts go to gallery-dl first, because it handles carousels. Facebook reels and videos go to yt-dlp first. A Facebook photo post sent to yt-dlp first "took a short video from the page… and the pack had that video instead of the picture" ([SW/app/fetch.py#L1-L12](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L1-L12), [#L335-L336](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L335-L336)).
   - If both tools fail after fetching some files, the larger partial set is returned, marked `incomplete` with the error code ([fetch.py#L321-L382](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L321-L382)).
   - A non-zero exit after some files is still treated as failure, which triggers the other tool ([#L127-L141](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L127-L141)).
   - The final error is chosen by priority: auth_required, then rate_limited, then not_found, then timeout. With no cookies, an Instagram "not found" is re-labelled as auth ([#L372-L382](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L372-L382)).
2. **Exact downloader invocations, with the fields mapped out of their JSON.**
   - gallery-dl runs with `--write-metadata --range 1-N --filesize-max --no-part`, an optional config and `--cookies`. Items are ordered by gallery-dl's `num`. Metadata is mapped as follows ([fetch.py#L201-L248](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L201-L248)):
     - author = `username|owner.username|user.name|author`
     - name = `fullname|owner.full_name|user.full_name`
     - caption = `description|caption|content|message`
     - date = `date|post_date|timestamp`
   - yt-dlp runs with `--use-extractors instagram.*,facebook.*` (stops the URL drifting into the generic extractor), `--write-info-json`, a 1080p-capped format, `--merge-output-format mp4`, `--restrict-filenames`, `--socket-timeout 20 --retries 2`. Fields are mapped from `uploader_id`, `uploader`, `description`, `upload_date` and `title` ([#L253-L300](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L253-L300)).
   - A Facebook `story.php?story_fbid=&id=` link is rewritten to `/<id>/posts/<fbid>` for gallery-dl, which otherwise misreads it as a profile ([#L184-L198](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L184-L198)).
3. **Careful cookie handling.**
   - Each platform's cookies are filtered by domain. They are **copied per job (mode 600) and deleted afterwards**, "because both tools write updated cookies back into the file they were given" ([SW/app/cookies.py#L181-L193](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/cookies.py#L181-L193), [fetch.py#L333](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L333), [#L358-L360](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L358-L360)).
   - The cookie file goes through a validated Netscape serializer. It rejects control characters and foreign domains, and requires the login cookies (`sessionid` for IG, `c_user`+`xs` for FB) ([cookies.py#L38-L104](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/cookies.py#L38-L104)). Writes are atomic.
   - A **MV3 browser extension pushes fresh cookies when you log in**. It has `cookies` permission, host permissions on IG/FB only, and a required `X-Sharewerk-Client` header ([SW/extension/manifest.json](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/extension/manifest.json), [SW/extension/sync.js#L1-L60](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/extension/sync.js#L1-L60), [SW/app/main.py#L290-L324](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/main.py#L290-L324)).
   - **Cookie health is judged by the downloads themselves.** The states are "ok", "expired", "missing", or "unknown" (new cookies not yet tried). This is exposed at `/healthz` ([SW/app/jobs.py#L252-L286](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/jobs.py#L252-L286)).
4. **Account-protection pacing.** Starts of consecutive Instagram downloads are spaced by `SHAREWERK_INSTAGRAM_PAUSE` (default 20 s) to avoid "bursts of requests… [that get] the account… throttled or blocked" ([jobs.py#L416-L424](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/jobs.py#L416-L424), [.env.example](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/.env.example)).
5. **URL extraction and canonicalisation with SSRF protection** ([SW/app/urls.py](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py)).
   - The first URL is pulled from the free share text, with trailing punctuation stripped ([#L62-L70](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py#L62-L70)).
   - Hosts must be on an exact-match allowlist.
   - Share and short links are resolved **hop by hop, with the allowlist re-checked on every hop**, stopping at login or checkpoint redirects ([#L109-L139](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py#L109-L139)).
   - IG `p/reel/tv/stories` links are canonicalised and the FB tracking-parameter list is stripped ([#L22-L29](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py#L22-L29), [#L142-L178](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py#L142-L178)).
   - A field note: Facebook `/share/*` returns 400 to a fake browser user agent but redirects an honest one ([#L36-L43](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/urls.py#L36-L43); PR [#2](https://github.com/Azentrius/sharewerk/pull/2)).
6. **Video into text and stills** ([SW/app/process.py](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/process.py), [SW/app/transcribe.py](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/transcribe.py)).
   - Key frames are chosen from ffmpeg `select='gt(scene,0.3)',showinfo` scene changes, plus the opening frame, plus evenly spaced fill, with a minimum gap. That is roughly one frame per 6 s, capped at 8 ([process.py#L103-L137](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/process.py#L103-L137)).
   - faster-whisper runs on CPU with int8, a lazily loaded model, `vad_filter=True` and `condition_on_previous_text=False`. **Segments with `no_speech_prob > 0.6` are dropped** to stop music hallucinations ([transcribe.py#L24](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/transcribe.py#L24), [#L45-L77](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/transcribe.py#L45-L77)).
   - Transcription progress is weighted by the length of each video ([process.py#L292-L317](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/process.py#L292-L317)).
7. **Output shaped for the Android 10-file share limit.** When a post has more than 9 visuals, it builds numbered 2-up or 4-up "contact sheets", and the text file maps each badge number back to "item N (video), frame at m:ss" ([process.py#L191-L240](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/process.py#L191-L240), [#L341-L407](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/process.py#L341-L407)). No peer in the prior-art set does this. Karakeep's video worker keeps only the video file, and ArchiveBox keeps everything but makes no transcript or stills (see prior-art report §1 and Summary #2).
8. **Ops hygiene.**
   - The container runs read-only with `cap_drop: ALL`, `no-new-privileges`, binds to 127.0.0.1, and has memory/CPU limits ([SW/compose.yaml](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/compose.yaml)).
   - The downloaders are **deliberately left unpinned upward**, and CI rebuilds the image weekly "because only the latest releases keep working" ([SW/requirements.txt](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/requirements.txt), [SW/.github/workflows/test.yml](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/.github/workflows/test.yml)).
   - Job state is mirrored to `job.json` so it survives restarts ([jobs.py#L1-L6](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/jobs.py#L1-L6)).

**User praise and upvotes:** none exist. The repo has 0 stars, 0 forks, 0 issues and no discussions (`gh api repos/Azentrius/sharewerk`, `gh issue list`, 2026-10-26).

**Limits, on its own terms:**
- **Instagram and Facebook only.** This is hard-coded in the allowlist, `--use-extractors` and `PLATFORMS` ([fetch.py#L257](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L257), [cookies.py#L38-L41](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/cookies.py#L38-L41)).
- **It keeps only 7 metadata fields** (platform, url, author, author_name, caption, date, title) out of the much richer `info.json` ([jobs.py#L83-L147](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/jobs.py#L83-L147)). It drops likes, hashtags, mentions, location, comments and per-item metadata, and the raw JSON is purged with the job after 24 h.
- `classify_error` is substring matching: any output containing "cookies", "log in" or "401" counts as auth ([fetch.py#L76-L92](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L76-L92)).
- One worker thread runs jobs strictly in series.
- The share target is a PWA GET `share_target` (`title`/`text`/`url` only) ([SW/app/static/manifest.webmanifest](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/static/manifest.webmanifest)), so it cannot receive files.

### Issue, commit and release health
- Created 2026-10-19. Releases [v0.1.0](https://github.com/Azentrius/sharewerk/releases/tag/v0.1.0) (10-19), v0.1.1 (10-20) and v0.1.2 (10-26).
- 20 commits. All after the initial one landed through PRs #1–#19, authored and merged by `stefan-azentrius` with no outside reviewers ([PR list](https://github.com/Azentrius/sharewerk/pulls?q=is%3Apr)).
- The PRs are small and well scoped, and each PR body has a test summary. For example, [#6](https://github.com/Azentrius/sharewerk/pull/6) says "ruff clean, pytest 72 passed and 2 skipped (no FFmpeg), `node --test` 5 of 5".
- The PR titles track real breakage found in use:
  - "[Pin PyAV below 19 so transcription works](https://github.com/Azentrius/sharewerk/pull/1)"
  - "[Report expired Instagram cookies in the health check](https://github.com/Azentrius/sharewerk/pull/7)"
  - "[Pause between Instagram downloads to protect the account](https://github.com/Azentrius/sharewerk/pull/8)"
  - "[Hand out partial downloads as packs marked incomplete](https://github.com/Azentrius/sharewerk/pull/9)"
  - "[Ask gallery-dl first for Facebook posts](https://github.com/Azentrius/sharewerk/pull/18)"
- The initial commit `e15f9ab` lands 61 files and 5,567 lines at once, so the pre-0.1.0 history is not visible.
- No AI-attribution trailers appear in any commit body. The very uniform prose style of the comments and commits suggests heavy AI assistance **[unverified]**.
- It is too young to judge abandonment. Bus factor is 1.

### Code quality
- **Tests:** 125 pytest functions across 16 files, plus 37 `node --test` cases for the PWA and extension. There is a fake downloader ([SW/tests/fake_downloader.py](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/tests/fake_downloader.py)).
- **CI** runs ruff, pytest, `node --check` and a gitleaks secret scan, with SHA-pinned actions ([test.yml](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/.github/workflows/test.yml)).
- Subprocesses use argument lists and never a shell. `--cookies` paths are redacted in logs ([fetch.py#L107-L117](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/app/fetch.py#L107-L117)).
- Comments explain *why* and cite observed platform behaviour with dates. This is the best-engineered of the three by a wide margin.
- Weak spots:
  - The error taxonomy is heuristic.
  - `except Exception` around transcription is intentional and documented.
  - Everything is CPU-only.

### License
- Apache-2.0 ([SW/LICENSE](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/LICENSE)). Copying or forking is allowed. You must keep the licence text, carry the NOTICE contents into derivative distributions, and mark modified files.
- **The name "Sharewerk", "Azentrius" and the icon are excluded** ("not licensed under the Apache License; all rights reserved") ([SW/NOTICE](https://github.com/Azentrius/sharewerk/blob/59be293f1538c5ae4179249d6ec513cf7a1de768/NOTICE)).
- Bundled fonts are OFL and icons are CC-BY-4.0/MIT ([SW/LICENSES/](https://github.com/Azentrius/sharewerk/tree/59be293f1538c5ae4179249d6ec513cf7a1de768/LICENSES)). Avoid them, or carry their notices.
- gallery-dl is GPL-2.0-only but is invoked as a separate program. Sharewerk's NOTICE takes the position that this does not affect its licence (same file). A private, unpublished yearn server has no distribution obligations at all.

---

## 2. MemGallery: Past-da-king/MemGallery

### What this project does best

1. **Many capture surfaces in one app.**
   - Two share-sheet entries via `activity-alias`: "Quick Save" (headless) and "Open in App", each for `text/plain` and `image/*` ([MG/…/AndroidManifest.xml#L104-L144](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/AndroidManifest.xml#L104-L144)).
   - A `PROCESS_TEXT` action for selected text ([#L146-L158](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/AndroidManifest.xml#L146-L158)).
   - A `text/calendar` share target.
   - A Quick Settings tile and two Glance widgets (Capture Bar, Memory Pulse).
   - An edge-swipe overlay service.
   - A MediaStore `ContentObserver` that auto-ingests new screenshots and skips `IS_PENDING`/`IS_TRASHED` rows ([MG/…/service/ScreenshotObserver.kt#L94-L180](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/service/ScreenshotObserver.kt#L94-L180)).
2. **Compose inside a system overlay window.** A hand-rolled `LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner` lets a `ComposeView` run in a `TYPE_APPLICATION_OVERLAY` window from a Service ([MG/…/service/OverlayLifecycleOwner.kt#L12-L43](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/service/OverlayLifecycleOwner.kt#L12-L43), [OverlayService.kt#L293-L310](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/service/OverlayService.kt#L293-L310)). This is a known community pattern, but a working, small reference.
3. **One structured-output indexing call per memory.**
   - It uses a JSON schema with `title`, `summary`, `tags[]`, `image_analysis`, `audio_transcription`, `actions[{type EVENT|TODO|REMINDER, description, date, time}]` and `suggested_collections[]` ([MG/…/data/remote/GeminiService.kt#L34-L66](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/GeminiService.kt#L34-L66)).
   - The schema is enforced through Gemini `responseMimeType=application/json` + `responseSchema` ([GeminiProvider.kt#L143-L201](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ai/GeminiProvider.kt#L143-L201)).
   - **Existing collection names and descriptions are fed into the prompt**, and the model's suggested collections auto-file the memory, but only into collections that already exist ([MG/…/data/repository/MemoryRepository.kt#L230-L275](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/MemoryRepository.kt#L230-L275)).
   - Extracted actions become tasks and notifications.
4. **Pluggable AI providers** behind one interface ([MG/…/data/remote/ai/AIProvider.kt#L11-L53](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ai/AIProvider.kt#L11-L53)): Gemini (the default, [SettingsRepository.kt#L133-L135](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/SettingsRepository.kt#L133-L135)), Groq, any OpenAI-compatible endpoint, and an on-device MediaPipe `LlmInference` model loaded from a user-picked `.task`/`.tflite`/`.literlm` (sic) file ([LocalAIProvider.kt#L44-L104](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ai/LocalAIProvider.kt#L44-L104)). API keys sit in `EncryptedSharedPreferences` ([SettingsRepository.kt#L29-L34](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/SettingsRepository.kt#L29-L34)).
5. **A chat agent over your own data.**
   - Tool calls `queryDatabase(table, filters, fields)` (read-only, results capped at 3,000 chars), `webSearch` (Gemini Google Search grounding) and `displayMemories` ([MG/…/data/remote/ChatTools.kt#L46-L110](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ChatTools.kt#L46-L110), [ChatToolsExecutor.kt](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ai/ChatToolsExecutor.kt)).
   - Tool start/finish events stream to the UI.
6. **Background pipeline idiom.** Items are written to the DB as `PENDING`, and a WorkManager worker claims each one with an **atomic `UPDATE … SET status='PROCESSING' WHERE id=? AND status IN (PENDING, FAILED)`** ([MG/…/data/local/dao/MemoryDao.kt#L43-L44](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/local/dao/MemoryDao.kt#L43-L44), [MemoryProcessingWorker.kt#L59-L122](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/service/MemoryProcessingWorker.kt#L59-L122)). Network errors revert the item to PENDING and return `Result.retry()` with exponential backoff.
7. Polish: lossless backup/restore (v0.3.1), Material You plus AMOLED themes, and 8 locales (releases [v0.3.1–v0.3.3](https://github.com/Past-da-king/MemGallery/releases)).

**User praise and upvotes.** Ranked by reactions, the top items are:
- [#3 "An option to add multiple images at once"](https://github.com/Past-da-king/MemGallery/issues/3) (1 reaction)
- [#4 "False App version number"](https://github.com/Past-da-king/MemGallery/issues/4) (1)
- [#11 "Ghost update… Version 0.0.10 persists after installing 0.0.11"](https://github.com/Past-da-king/MemGallery/issues/11) (1, with a second user confirming: "I have the same issue")

No issue carries praise. The most substantive user report is [#10](https://github.com/Past-da-king/MemGallery/issues/10): "OpenAI-compatible API fails to execute Tool Calls (outputs raw JSON instead of calling functions)… tool calling is totally broken compared to Groq". The maintainer replied "All reported issues… have been addressed and released". The repo has no Discussions. The 103 stars are the only popularity signal.

**What it claims but does not do:**
- The README promises "**Semantic Search**: Find memories by describing them" ([MG/README.md#L112](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/README.md#L112)). There are **no embeddings, no vectors and no FTS anywhere in the code**. Gallery search is an in-memory, case-insensitive `contains` over `aiTitle`, `aiSummary` and `aiTags` only ([MG/…/ui/viewmodels/GalleryViewModel.kt#L78-L85](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/ui/viewmodels/GalleryViewModel.kt#L78-L85)). The chat tool does the same substring match, also over userText, image analysis and transcription ([ChatTools.kt#L100-L110](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ChatTools.kt#L100-L110)). The "semantic" part is just whatever the LLM wrote into the summary and tags.
- "No Cloud" and "Your data stays on your device" ([README#L40-L41](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/README.md#L40-L41), [#L78](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/README.md#L78)) contradict the defaults: the default provider is Gemini, and the README's own privacy section says content "is sent to Google's Gemini API" ([#L234](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/README.md#L234)).

### Share handling, examined in detail
[MG/…/QuickSaveActivity.kt#L44-L90](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/QuickSaveActivity.kt#L44-L90):
- It reads only `EXTRA_STREAM` (single image) and `EXTRA_TEXT`, and splits off the first `https?://\S+` as the bookmark.
- `EXTRA_SUBJECT`/`EXTRA_TITLE`, ClipData, `SEND_MULTIPLE`, the referrer and non-image files are all ignored.
- `savePendingMemory` **fetches the page with jsoup (10 s timeout) and downloads the og:image *before* inserting the row** ([MemoryRepository.kt#L85-L187](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/MemoryRepository.kt#L85-L187), [UrlMetadataExtractor.kt#L21-L80](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/utils/UrlMetadataExtractor.kt#L21-L80)). A share made while offline therefore waits for the network before anything is persisted.
- The image copy hard-codes a `.jpg` extension regardless of type, and also accepts `file://` URIs from callers ([FileUtils.kt#L25-L60](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/utils/FileUtils.kt#L25-L60)).
- Any item the worker leaves in `PROCESSING` (process killed mid-call) is never retried, because the claim only accepts PENDING/FAILED ([MemoryDao.kt#L43-L44](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/local/dao/MemoryDao.kt#L43-L44)).

### Issue, commit and release health
- 16 releases from [v0.0.3](https://github.com/Past-da-king/MemGallery/releases/tag/v0.0.3) (2025-11-20) to [v0.3.3](https://github.com/Past-da-king/MemGallery/releases/tag/v0.3.3) (2026-05-09). The last code change was 2026-05-09. The only later activity is a bot README-translation PR ([#15](https://github.com/Past-da-king/MemGallery/pull/15), merged 2026-08-23). The project is dormant.
- **History was rewritten.** `master` starts at `862598c` (v0.3.0, 2026-05-08), a **root commit with 0 parents adding 31,903 lines** (`gh api repos/Past-da-king/MemGallery/commits/862598c`). Releases v0.0.3 through v0.2.0 point to commits that are no longer on master, and are reachable only through tags.
- Tags `v0.0.10` and `v0.0.11` both point to the same commit `37bab58` ("finished"), and `v0.0.7`/`v0.0.8` share `491d127`. This fits the "ghost update" reports [#11](https://github.com/Past-da-king/MemGallery/issues/11)/[#12](https://github.com/Past-da-king/MemGallery/issues/12).
- **The issue tracker is mostly in-app telemetry.** 7 of 10 issues are titled "Feedback: …". They are created by the app via the GitHub API under the account `kkronas952-crypto` and contain device info plus a logcat dump (e.g. [#13](https://github.com/Past-da-king/MemGallery/issues/13), with a `FATAL EXCEPTION` stack). The mechanism, from the source:
  - `FeedbackRepository` posts issues with `Bearer ${BuildConfig.GITHUB_TOKEN}` ([FeedbackRepository.kt#L16-L47](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/FeedbackRepository.kt#L16-L47)).
  - That token is injected into release builds from `local.properties`, with `isMinifyEnabled = false` ([build.gradle.kts#L49-L61](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/build.gradle.kts#L49-L61)).
  - **The published [v0.1.0 APK](https://github.com/Past-da-king/MemGallery/releases/tag/v0.1.0) contains a classic `ghp_…` token string in `classes4.dex`.** I verified this by string-grepping the dex; the value was not printed or tested. v0.3.3's dex shows no token-shaped string.
  - "Include logs" defaults to **on** ([FeedbackScreen.kt#L38](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/ui/screens/FeedbackScreen.kt#L38)). The dump keeps all error lines plus the ChatViewModel/AIProvider/MemoryWorker tags ([LogUtils.kt#L14-L30](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/utils/LogUtils.kt#L14-L30)), and goes into a **public** issue.
- **AI-generated provenance is explicit.** [MG/DOCS/task.md](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/DOCS/task.md) is a dictated prompt to a coding agent ("write me a detailed uh plan… very premium we're looking for very premium"). `DOCS/implementation_plan.md` and `project_analysis.md` are agent artifacts. The maintainer answers issues promptly ("fixed update should be out on monday").

### Code quality
- **Tests:** only the Android Studio templates `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt`. There are no tests at all.
- The Room DB is at **version 17 with `exportSchema = false`** and hand-written migrations ([AppDatabase.kt#L25-L26](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/local/database/AppDatabase.kt#L25-L26)).
- 24.6k lines of Kotlin, with god files: `SettingsScreen.kt` is 1,954 lines and `ChatScreen.kt` is 1,406.
- `runBlocking` inside tool calls ([ChatTools.kt#L55](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/remote/ChatTools.kt#L55)) and inside `GeminiService.initialize`.
- Whole entities, including user text, are logged at debug level ([MemoryRepository.kt#L177](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/MemGallery/app/src/main/java/com/example/memgallery/data/repository/MemoryRepository.kt#L177)).
- The package is still `com.example.memgallery`.
- Status values are stringly typed (`"PENDING"`, …).
- Overall it reads as unsupervised agent output that compiles and demos well.

### License
MIT, © 2025 Ayanda Phaketsi ([MG/LICENSE](https://github.com/Past-da-king/MemGallery/blob/ba9a2581a70479f27618deb27e66416ad94de382/LICENSE)), added by outside PR [#1](https://github.com/Past-da-king/MemGallery/pull/1). Copying is allowed provided the copyright and permission notice are kept.

---

## 3. memex: hasmeni/memex

### What this project does best
1. **A D3 v7 force-directed "mind map" that stays usable as it grows** ([MX/frontend/static/js/mindmap.js#L237-L330](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/frontend/static/js/mindmap.js#L237-L330)).
   - The graph is a root node, then one node per category, then up to `MAX_LINKS_PER_CAT = 10` links each. A "+N" badge shows hidden links, and **clicking a category collapses or expands it** ([#L17-L19](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/frontend/static/js/mindmap.js#L17-L19), [#L403-L426](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/frontend/static/js/mindmap.js#L403-L426)).
   - **Force parameters scale with node count:** charge −320/−400/−500, link distance and collision radii change past 50 nodes.
   - A `PERF_THRESHOLD = 50` switches off glow filters, grid and pulse animations.
   - There is zoom/pan (0.3–4×), node dragging and a reset view.
2. **Several views of the same data:** mind map, pinboard masonry, weighted tag cloud, a mobile card grid with alpha-nav, a link-of-the-day, and a screensaver page ([mindmap.js#L671-L760](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/frontend/static/js/mindmap.js#L671-L760)).
3. **Federation-lite.** A share token exposes all links, one category or one collection read-only at `/api/shared/{token}`. "Friends" are other instances whose shared feed the server proxies ([MX/backend/main.py#L144-L160](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L144-L160), [#L1541-L1560](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L1541-L1560), [#L1620-L1635](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L1620-L1635)).
4. **A broad link model**, with dead-link health checks, Wayback, private links, read status, expiry and click analytics ([main.py#L52-L80](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L52-L80)).
5. **A companion share app with an offline queue.** It reads `EXTRA_TEXT` + `EXTRA_SUBJECT`, regexes the first URL, and shows an edit form. If a save fails, it queues the link as JSON in SharedPreferences and syncs when the app is next opened ([MX/android/…/ShareReceiverActivity.kt#L37-L75](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/android/app/src/main/java/com/ironylabs/linkshare/ShareReceiverActivity.kt#L37-L75), [LinkQueue.kt#L12-L74](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/android/app/src/main/java/com/ironylabs/linkshare/LinkQueue.kt#L12-L74)).

**User praise and upvotes:** none. There are 0 issues, 0 PRs, no Discussions, 7 stars and 1 fork.

**Limits:**
- **The "mind map" is a two-level category tree.** Nodes link only root→category→link; there are no link-to-link, tag or similarity edges. `parent_category` exists in the schema, but the renderer ignores it ([mindmap.js#L250-L270](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/frontend/static/js/mindmap.js#L250-L270)).
- **Categorisation is manual.** "Auto-tag" means regexing the page's `<meta name="keywords">` ([main.py#L1053-L1088](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L1053-L1088)).
- Thumbnails send every URL to the third-party `image.thum.io` ([#L1263-L1270](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L1263-L1270)).

### Issue and commit health
- Created 2026-07-06. 12 commits ([history](https://github.com/hasmeni/memex/commits/main)): an initial dump, then 11 README edits and doc deletions over 6 days. Last push 2026-07-12. One "release" tagged `apk`.
- `FINAL-FEATURES-20260704-193500.md` names the project "ironyLabs-links-memex-kiro-mod" ([MX/FINAL-FEATURES…md#L4](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/FINAL-FEATURES-20260704-193500.md#L4)), which means it was generated with Kiro. The README links to guides that were deleted in later commits (VPS-DEPLOY, FRIEND-CONNECT).
- It is effectively abandoned.

### Code quality
- The backend is a single 1,686-line `main.py`. Schema migrations are ad hoc `ALTER TABLE` statements.
- **Hard-coded fallback secrets:** `ADMIN_PASSWORD="ironyLabs2024!"` and `SECRET_KEY="supersecretkey_change_me"` ([main.py#L22-L23](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L22-L23)).
- `CORS allow_origins=["*"]` ([#L337](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/backend/main.py#L337)).
- Anonymous public upvote endpoint.
- There are no tests.
- **Offline-queue sync is racy.** It fires all OkHttp callbacks concurrently and calls `queue.remove(i)` by index from each one, a read-modify-write of one SharedPreferences JSON blob. The "reverse so index removal is safe" comment is wrong for async callbacks: items can be removed wrongly or duplicated ([MX/android/…/MainActivity.kt#L88-L120](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/android/app/src/main/java/com/ironylabs/linkshare/MainActivity.kt#L88-L120)).
- There are two divergent Android apps (`android/` and `memex-android/`), and the second has no queue ([MX/memex-android/…/ShareReceiverActivity.kt](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/memex-android/app/src/main/java/com/ironylabs/memex/ui/ShareReceiverActivity.kt)).

### License
- **There is no LICENSE file.** `gh api repos/hasmeni/memex/license` returns 404 and the repo `license` field is null.
- The README has a "License: MIT" badge and a "## License / MIT" heading ([MX/README.md#L107-L109](https://github.com/hasmeni/memex/blob/56cda949fd2517dd86ecd9b196ca82bbf4f92245/README.md#L107-L109)), but no licence text or copyright line. Without the actual grant, the default is all rights reserved.
- **Do not copy code.** Ask the author to add a LICENSE if anything were ever needed. The ideas (collapse, per-category pagination, force tuning) are not protected and are trivial to re-implement.

---

## Worth grabbing wholesale?

None of the three should be forked whole for yearn.

- **Sharewerk:** take components into the phase 2 server under Apache-2.0 attribution. It is the only one with code worth copying.
  - `fetch.py`: the tool-order cascade, partial results and the error taxonomy.
  - `cookies.py`: per-job cookie copies and the validated Netscape writer.
  - `urls.py`: allowlisted hop-by-hop resolution and canonicalisation.
  - `transcribe.py`: VAD plus the `no_speech_prob` filter.
  - Scene-change key-frame picking from `process.py`.
  - The extension's "push cookies on login" design.
  - Yearn would need to add X, Reddit, LinkedIn and GitHub hosts, and keep the full gallery-dl/yt-dlp JSON instead of the 7-field `PostMeta`.
- **MemGallery:** ideas only.
  - The structured indexing schema and "feed existing collections into the prompt" fit phase 2 auto-organisation, but run on the server.
  - The `OverlayLifecycleOwner` pattern is useful if the liquid-drop capture UI needs a system overlay.
  - The atomic `PENDING→PROCESSING` claim is worth reusing, but add a timeout for stale `PROCESSING` rows.
  - Its share path (network before persist, single stream, no subject or referrer) is the opposite of yearn's "record raw instantly offline". Its search is not semantic.
- **memex:** ideas only, for mind-map UX: collapsible cluster nodes, a "+N" overflow badge, force strength scaled to node count, and a node-count threshold that drops effects. Its graph has no semantic edges and the code is unlicensed.
