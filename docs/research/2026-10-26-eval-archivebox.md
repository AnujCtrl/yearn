# Evaluation: ArchiveBox, abx-plugins, abx-dl and android-archivebox

Date: 2026-10-26. Code read at these commits: ArchiveBox `dev` [`1b4d552`](https://github.com/ArchiveBox/ArchiveBox/tree/1b4d552116fafab0da2244d7ba349ca03c29c677), abx-plugins `main` [`32843a8`](https://github.com/ArchiveBox/abx-plugins/tree/32843a8043bd68d1ec92ba8c7289a86d42ef1d03), android-archivebox `main` [`ef38094`](https://github.com/ArchiveBox/android-archivebox/tree/ef38094a8a10b394504c8ef39c7408813d45ffa3). The code was cloned and read but never built or run. Issue and commit numbers come from `gh` on 2026-10-26. The GitHub search API hit its rate limit partway through, so the keyword issue counts are from a partial sample (noted where used).

## Verdict: grab specific components

ArchiveBox's best asset is its extractor layer, not the app. Each extractor in **abx-plugins** is a standalone CLI script with a JSONL status contract, and the scripts run without Django through **abx-plugins** or **abx-dl** (both MIT). Its yt-dlp and gallery-dl invocations are well tuned: metadata sidecars, cookie stripping, and workarounds for upstream extractor bugs. Those parts are worth taking or calling directly in phase 2. The Django server is too heavy to fork: 130k lines of Python, one maintainer, and hundreds of release candidates a month. The Android app gives yearn nothing for phase 1 beyond a few small patterns. It has no offline queue, and it reduces every share to a list of URLs.

---

## 1. What ArchiveBox does best (on its own merits)

**1. Saves each URL in many redundant formats.** abx-plugins ships 84 plugins ([plugins dir](https://github.com/ArchiveBox/abx-plugins/tree/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins)). They cover singlefile, wget+WARC, chrome_mhtml, pdf, screenshot, dom, readability, defuddle, trafilatura, mercury, htmltotext, ytdlp, gallerydl, forumdl, papersdl, git, headers, ssl, dns, favicon, title, archivedotorg and opentimestamps. They also cover cloud-folder providers (googledrive, dropbox, notion, figma) and three search backends (sonic, sqlite, ripgrep). No peer in the prior-art report captures this much by default. Karakeep saves the video only, with no info-json.

**2. The extractor contract can be used outside ArchiveBox.** Each hook is a script named `on_Snapshot__NN_<name>.[finite.]bg|fg.py`. Hooks run in lexicographic order. Each one writes into `SNAP_DIR/<plugin>/` and reports by printing JSONL to stdout: `{"type":"ArchiveResult","status":"succeeded|noresults|skipped|failed","output_str":...}`. Exit 0 means success, noresults or skipped; any other exit code means failed ([README contract, L21-52](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/README.md#L21-L52), [lifecycle and records, L164-207](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/README.md#L164-L207), [rules, L261-277](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/README.md#L261-L277)). Hooks must not import archivebox or Django. [abx-dl](https://github.com/ArchiveBox/abx-dl) (MIT, 148★, pushed 2026-10-26) is a one-shot runner for the same scripts: `abx-dl --plugins=ytdlp,gallerydl,title URL` writes `index.jsonl` plus one folder per plugin.

**3. The four outcome statuses are meaningful, and the yt-dlp errors are classified carefully.** The ytdlp hook treats the following as `noresults` (success with nothing to save), not `failed` ([ytdlp hook L330-366](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L330-L366)):
- "Unsupported URL";
- a tweet with no video;
- a Substack text newsletter;
- a Reddit text post.

It reports 403, 404 and "Unable to extract" as real failures. Extractor results are stored with:
- `status`;
- `output_str`;
- structured `output_json`;
- `output_files`;
- `output_size`;
- `output_mimetypes`;
- `start_ts`/`end_ts`;
- `retry_at`.

The statuses also include `backoff` and `paused` ([core/models.py L4075-4083](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L4075-L4083), [L4235-4245](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L4235-L4245)).

**4. The yt-dlp defaults keep metadata.**
- **Default flags:** `--write-info-json --write-description --write-thumbnail --write-sub --write-auto-subs --convert-subs=srt --add-metadata --restrict-filenames --trim-filenames=128` ([ytdlp config.json L119-153](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/config.json#L119-L153)).
- **Size cap:** a format selector limits downloads to 750 MB ([hook L233-241](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L233-L241)).
- **Cookie safety:** the info.json is written to a private temp directory. A sanitized copy with every `cookie`/`cookies`/`set-cookie` key removed is then written next to the media. This happens even when the download fails or times out ([L259-275](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L259-L275), [L372-396](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L372-L396)). Most wrappers miss this: yt-dlp writes request cookies into info.json.
- **Signal handling:** SIGTERM is blocked so ffmpeg merges are not interrupted. The hook kills the whole process tree on timeout ([L25-32](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L25-L32), [L185-207](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L185-L207)).

**5. It patches upstream yt-dlp bugs and documents when to delete each patch.** The patches are yt-dlp `--plugin-dirs` extractor overrides. Each override records the bug, the upstream yt-dlp version, and the condition for removing it:
- [archivebox_reddit.py](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/yt-dlp-plugins/archivebox/yt_dlp_plugins/extractor/archivebox_reddit.py#L1-L47) stops an infinite redirect loop on Reddit gallery posts and on deleted text posts (yt-dlp#15209).
- [archivebox_twitter.py](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/yt-dlp-plugins/archivebox/yt_dlp_plugins/extractor/archivebox_twitter.py#L1-L19) routes `summary_large_image` cards to the summary handler.
- There are also Substack, Amazon and generic-thumbnail fixes.

The test suite pins real Reddit text, gallery and deleted posts and a Twitter summary card as gzipped fixtures ([fixtures](https://github.com/ArchiveBox/abx-plugins/tree/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/tests/fixtures)).

**6. The gallery-dl call is short, and the hook stops instead of waiting on rate limits.** It runs `--write-metadata --write-info-json --retries=0 --http-timeout=5` ([config.json L68-82](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/gallerydl/config.json#L68-L82)) with `-D <dir>` for flat output. It also passes the option `actions={"info:^Waiting .+ (rate limit)$":"exit 1"}`, so gallery-dl exits instead of sleeping for 600 s on a provider rate limit ([hook L95-107](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/gallerydl/on_Snapshot__32_gallerydl.finite.bg.py#L95-L107)).

**7. The on-disk layout is durable and a person can browse it.** The path is `data/archive/users/<user>/snapshots/YYYYMMDD/<domain>/<uuid>/<plugin>/…` ([get_storage_path_for_version, L1433-1457](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L1433-L1457)). Each snapshot folder has an `index.jsonl` with typed records: Snapshot, Crawl, Binary, Process and ArchiveResult. Each record holds the command, binary version and exit details for every extractor ([write_index_jsonl L2025-2070](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L2025-L2070)). `archivebox update --rescan --migrate-only` rebuilds the database from these folders. Snapshot folders can be copied between collections ([README Archive Layout L847-923](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/README.md#L847-L923)).

**8. The work queue runs in the database with leases (Postgres-ready, though SQLite is what ships).** The `ModelWithQueue` mixin stores `status` plus `retry_at`. `retry_at` doubles as a lease: a worker claims a row with a compare-and-swap UPDATE (`WHERE retry_at = old AND retry_at <= now SET retry_at = now+60s`) ([workers/models.py L40, L199-214](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/workers/models.py#L199-L214)). AGENTS.md states the rule: never hold a transaction across network or hook work; use short autocommit or CAS writes ([AGENTS.md L18-23](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/AGENTS.md#L18-L23)).

**9. Login-only capture uses "personas".** Each persona holds a cookies.txt and a Chrome profile, and every crawl picks one. Cookies are passed to yt-dlp (`--cookies`) and gallery-dl (`-C`) only when the file really contains Netscape-format entries ([ytdlp hook L250-251](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/on_Snapshot__31_ytdlp.finite.bg.py#L250-L251)).

**10. Good patterns in the Android app (small, but well made):**
- **Two ways to find the server on the LAN:** mDNS discovery of `_archivebox._tcp.` and a bounded subnet probe that only accepts a host serving a valid ArchiveBox OpenAPI schema ([Discovery.kt L24-30, L73-103](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/Discovery.kt#L73-L103)).
- **Encrypted server settings:** the server list, including API tokens, is AES-GCM encrypted with an AndroidKeyStore key ([ArchiveRepository.kt L35-60](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/ArchiveRepository.kt#L35-L60)).
- **Selected-text action:** `PROCESS_TEXT` puts an entry in the text-selection menu ([manifest L26-30](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/AndroidManifest.xml#L26-L30)).
- **No accidental double submits:** OkHttp is configured with `retryOnConnectionFailure(false)` and an idle pool of zero, so an uncertain POST is never replayed ([ArchiveApi.kt L66-73](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/ArchiveApi.kt#L66-L73)).
- **Undo and tag after sending:** "undo submission" deletes the crawl, and tags are patched onto the crawl and its snapshots after submit ([L173-206](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/ArchiveApi.kt#L173-L206)).

**What users have asked for most** (search API sorted by reactions, all time):
- Recursive or depth crawling, [#191](https://github.com/ArchiveBox/ArchiveBox/issues/191): 120 reactions, closed (implemented).
- Multiple snapshots over time, [#179](https://github.com/ArchiveBox/ArchiveBox/issues/179): 50, closed (implemented).
- A REST API, [#496](https://github.com/ArchiveBox/ArchiveBox/issues/496): 25, still open. The README still labels the REST API "ALPHA" even though the app depends on it ([README L132](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/README.md#L132)).
- WARC import, [#160](https://github.com/ArchiveBox/ArchiveBox/issues/160): 21, open.
- P2P or federated archiving, [#50](https://github.com/ArchiveBox/ArchiveBox/issues/50): 14, open since 2017.
- A Twitter thread archiver, [#345](https://github.com/ArchiveBox/ArchiveBox/issues/345): 9, open since 2020. A Nitter-based X extractor is still only an idea ([abx-plugins#32](https://github.com/ArchiveBox/abx-plugins/issues/32)).

The things users praise and upvote are about archiving breadth (crawl depth, snapshots over time, formats). None of them are about social-media capture quality.

**Versus peers** (from the prior-art report): ArchiveBox captures the most by default and stores it in the most durable format. It has no AI tagging, semantic search or graph. Karakeep has those, but its media capture is thin. ArchiveBox's own README calls it "neither the highest fidelity nor the simplest… a jack-of-all-trades" ([README L1254](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/README.md#L1254)).

---

## 2. Issue-tracker health

### Volume and response time

| Metric | Value |
|---|---|
| Stars and forks | 28,888★ and 1,623 forks; repo created 2017-05-05 |
| Issues, all time | 157 open / 910 closed (search API counts) |
| Last 400 issues (Jan 2023–Oct 2026) | 310 closed / 90 open |
| Issues titled "Bug:" in that window | 151 closed / 36 open (~19% open) |
| Opened in 2026 | 48 closed / 12 open |
| Time to close (closed 2026 issues) | median **4 days** (p25 0, p75 27) |
| Since 2026-07-01 | 32 opened, 55 closed (the backlog is shrinking) |

### Who maintains it: one person

| Repo | Commits in the 90 days before 2026-10-26 |
|---|---|
| ArchiveBox | `pirate` (Nick Sweeting) 1,810; release bot 1,428; two other people 1 each |
| abx-plugins | `pirate` 1,370; release bot 864 |
| android-archivebox | 86 of its 87 commits all-time are `pirate`'s; the other is dependabot |

Since July, 38 of 43 merged ArchiveBox PRs were his. Both repos ship `AGENTS.md` and `skills/`, and the commit rate (about 20 a day) fits agent-assisted development. The bus factor is 1 for all three repos.

### Release cadence: a long gap, then very fast churn

- Stable releases: v0.7.2 (2024-01) → v0.7.3 (2024-12) → v0.7.4 (2026-05) → v0.9.36 (2026-09-22) → v0.9.73 (2026-10-06).
- `dev` is already at **0.9.74rc398**, about 400 release candidates in 20 days ([recent commits](https://github.com/ArchiveBox/ArchiveBox/commits/dev)).
- Feature work stalled for about 2.5 years, then a rewrite landed in September 2026.

### Android app status

- Created 2026-09-20; 8★; 21 releases (v1.13.13 on 2026-10-25).
- The only issue from a person is [#13 (HTTP basic auth)](https://github.com/ArchiveBox/android-archivebox/issues/13), open since 2026-10-02 with 0 replies. All 14 PRs are from dependabot.
- **It is a fresh, single-author experiment, not a proven app.**

### Recurring bug themes (linked examples)

- **Upgrades and migrations break existing archives:**
  - [#1889](https://github.com/ArchiveBox/ArchiveBox/issues/1889): after the 0.9 upgrade, every older snapshot returned 500 "Refusing to load a non-UTC date". It was fixed in 0.9.70, but some snapshots still showed as raw folders. The issue was closed without the reporter confirming because "lots of the related code has been rewritten".
  - [#1891](https://github.com/ArchiveBox/ArchiveBox/issues/1891): the container would not start after a migration by a newer version.
  - [#1899](https://github.com/ArchiveBox/ArchiveBox/issues/1899): legacy index.json files imported without their extractor results.
- **Cookies and logins not applied, often silently:**
  - [#1896](https://github.com/ArchiveBox/ArchiveBox/issues/1896): "Cookies file not found error causing archive runs to silently fail". Duplicate `Default`/`default` personas also appeared. The user had to delete personas by hand; it was fixed in v0.9.73.
  - Still open: [#1645](https://github.com/ArchiveBox/ArchiveBox/issues/1645), [#1654](https://github.com/ArchiveBox/ArchiveBox/issues/1654), [#1662](https://github.com/ArchiveBox/ArchiveBox/issues/1662).
  - Older: [#1445](https://github.com/ArchiveBox/ArchiveBox/issues/1445) (16 comments, open 18 months).
- **Social-site extraction:**
  - [#1670](https://github.com/ArchiveBox/ArchiveBox/issues/1670) "instagram title" has been open since 2025-03. The title came out as "Instagram" or "Update your browser" depending on user-agent. The maintainer replied "Trust me it's that way for good reason".
  - [#1568](https://github.com/ArchiveBox/ArchiveBox/issues/1568): archived YouTube videos are inaccessible (open, 10 comments).
  - [#1779](https://github.com/ArchiveBox/ArchiveBox/issues/1779): "yt-dlp outdated and no longer working" took 2.5 months to close.
  - [#1086](https://github.com/ArchiveBox/ArchiveBox/issues/1086): "Not archiving Twitter correctly".
  - Partial keyword counts before the search API limit: instagram 9 issues (4 open), twitter 23 (4 open), reddit 22 (4 open), yt-dlp 173 (34 open).
- **The runner and processes hang or leak:**
  - [#1892](https://github.com/ArchiveBox/ArchiveBox/issues/1892): the daemon held one CPU core at ~100% with an empty queue.
  - [#1894](https://github.com/ArchiveBox/ArchiveBox/issues/1894): the runner hung after a hook timeout.
  - Still open: [#1830](https://github.com/ArchiveBox/ArchiveBox/issues/1830) and [#1831](https://github.com/ArchiveBox/ArchiveBox/issues/1831) (crawls seal on their own, or will not resume or stop).
  - Historical: [#746](https://github.com/ArchiveBox/ArchiveBox/issues/746), zombie Chromium processes (44 comments).
- **Security in the web UI:**
  - [#1918](https://github.com/ArchiveBox/ArchiveBox/issues/1918): arbitrary local file read through the `/add/` textarea. It was fixed. The maintainer's position is that all UI users are "assumed to be trusted / already have root".
  - [#1929](https://github.com/ArchiveBox/ArchiveBox/issues/1929): a UUID-prefix leak exposes unlisted snapshots (open, 0 replies).
  - [#1930](https://github.com/ArchiveBox/ArchiveBox/issues/1930) and [#1844](https://github.com/ArchiveBox/ArchiveBox/issues/1844): other access-control holes.
- **Install pain:**
  - [#539](https://github.com/ArchiveBox/ArchiveBox/issues/539) `/data` permissions, 32 comments.
  - [#621](https://github.com/ArchiveBox/ArchiveBox/issues/621), [#1825](https://github.com/ArchiveBox/ArchiveBox/issues/1825), [#1828](https://github.com/ArchiveBox/ArchiveBox/issues/1828), [#1927](https://github.com/ArchiveBox/ArchiveBox/issues/1927).

**Overall:** the maintainer responds quickly and in depth. Fixes usually come with real regression tests, as in #1896. But one person is rewriting a very large system in place, and regressions in upgrades, cookies and the runner keep coming back.

---

## 3. Code quality spot-check

### Scale and tests

- **Server:** 130k lines of Python in `archivebox/`, including 219 test files (~61k lines).
- **abx-plugins:** 157 test files (~55k lines).
- **AGENTS.md test policy:** "no mocks, monkeypatches, fakes… real subprocesses, real DB rows" ([AGENTS.md](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/AGENTS.md)).
- **Android tests:** two JVM unit-test files plus six instrumented journeys. CI runs these on an emulator against a real server ([ci.yml](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/.github/workflows/ci.yml)). The share journey sends a single `EXTRA_TEXT` URL ([ArchiveBoxJourneyTest.kt L236-255](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/androidTest/java/io/archivebox/app/ArchiveBoxJourneyTest.kt#L236-L255)).

### Complexity

- `core/models.py` is 4,932 lines.
- Snapshot storage paths branch on `fs_version` across 0.7.0 to 1.0.0, with symlink fallbacks and fallbacks for hyphenated UUID names ([L2846-2870](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L2846-L2870)).
- `ArchiveResult` keeps compatibility properties because process fields moved to a `Process` table midway through a migration.
- This is migration debt, not something to copy.

### Android share parsing ([IncomingRequest.kt L17-36](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/ui/IncomingRequest.kt#L17-L36))

What it reads:
- `EXTRA_TEXT`, or the text ArrayList for `SEND_MULTIPLE`;
- ClipData `.text`;
- ClipData `.uri`, **only when the scheme is http or https**.

It joins them with newlines and caps the result at 200k characters.

What it drops:
- `EXTRA_SUBJECT` and `EXTRA_TITLE`;
- `EXTRA_STREAM` and `content://` URIs;
- the MIME type;
- `getReferrer()`.

`extractUrls` then keeps only regex-matched http(s) URLs ([ArchiveApi.kt L45-54](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/ArchiveApi.kt#L45-L54)). Caption text shared alongside a link is lost.

### No offline queue (stated in the code)

- The comment reads: "Shared content and submission receipts remain in memory; there is no offline submission queue" ([AddScreen.kt L42](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/ui/AddScreen.kt#L42)).
- Submitting makes two blocking network calls: it lists personas, then POSTs `/api/v1/cli/add` with a 20 s timeout ([ArchiveApi.kt L160-171](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/data/ArchiveApi.kt#L160-L171)).
- If the server cannot be reached, the user sees an error card, and the share is gone once the sheet is dismissed.

### Share UI

- An opaque Material Light activity opens the full app with a `ModalBottomSheet` on top ([ArchiveBoxApp.kt L163](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/java/io/archivebox/app/ui/ArchiveBoxApp.kt#L163), [styles.xml](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/app/src/main/res/values/styles.xml)). It is not a translucent overlay.

### Error handling

- **Strengths:**
  - The server's replies are validated strictly: the crawl ID format, that every queued URL appears in the receipt, and that tags were echoed back.
  - Responses are capped at 8 MB.
- **Weakness in Kotlin:** the Kotlin code packs many statements onto very long lines (one Compose line runs past 400 characters), which makes it hard to read.

### Extractor weak spots

- **gallery-dl hides auth failures:** gallery-dl `AuthRequired` and "authenticated cookies needed" count as **`noresults`, not `failed`** ([gallerydl hook L204-208](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/gallerydl/on_Snapshot__32_gallerydl.finite.bg.py#L204-L208)). An Instagram post skipped because cookies are missing or expired therefore looks the same as a post with no media. The README rules make this intentional ("unavailable provider authentication → noresults"), but for a social-media capture tool it hides failures.
- **Crude 403/404 detection:** a 403 or 404 is detected by searching the whole output for the substring `"403"`/`"404"` ([L213-216](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/gallerydl/on_Snapshot__32_gallerydl.finite.bg.py#L213-L216)).
- **No automatic retry:** every tool runs with `--retries=0`. Failed results stay failed until someone triggers `retry_failed_archiveresults()` from the admin UI ([core/models.py L3373](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/models.py#L3373), called only from [admin_snapshots.py L683, L1443](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/archivebox/core/admin_snapshots.py#L683)). The `backoff` status exists, but nothing outside the admin and progress views references it.

### Privacy default

- `ARCHIVEDOTORG_ENABLED` defaults to `true` ([archivedotorg config.json L13-15](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/archivedotorg/config.json#L13-L15)), so every saved URL is also submitted to the Wayback Machine.

---

## 4. License

| Repo | License | Copyright line |
|---|---|---|
| [ArchiveBox](https://github.com/ArchiveBox/ArchiveBox/blob/1b4d552116fafab0da2244d7ba349ca03c29c677/LICENSE) | MIT | © 2026 Nick Sweeting |
| [abx-plugins](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/LICENSE) | MIT | © 2024 Nick Sweeting |
| [android-archivebox](https://github.com/ArchiveBox/android-archivebox/blob/ef38094a8a10b394504c8ef39c7408813d45ffa3/LICENSE) | MIT | © 2026 ArchiveBox contributors |
| abx-dl | MIT | — |

**Obligations:** keep the copyright notice and the MIT text in any copied or substantial portion. MIT does not require yearn to be open-sourced, and yearn can use any license.

**Tools run as subprocesses:** yt-dlp is Unlicense; gallery-dl is GPL-2.0. Invoking them as subprocesses creates no obligation for yearn. Bundling or redistributing the gallery-dl binary would carry GPL-2.0 for that component only.

**The yt-dlp override plugins** subclass yt-dlp extractors (Unlicense), so they are fine to copy.

---

## 5. Risks

- **Bus factor 1, with very fast churn.** Pinning ArchiveBox as a phase-2 dependency means tracking about 400 release candidates a month from one maintainer, and upgrade regressions keep recurring (#1889, #1891, #1896).
- **The server is heavy:** Django, Chrome, Node, supervisord, sonic and 84 plugins. Users report CPU spin and runner hangs (#1892, #1894, #1830).
- **Failures can be silent:** gallery-dl auth failures count as `noresults` (§3), and cookie or persona misconfiguration fails silently (#1896).
- **Privacy:** archive.org submission is on by default.
- **Social-media coverage is weak:** there is no dedicated X or Instagram extractor; capture relies on yt-dlp/gallery-dl defaults. Instagram title extraction is a known open bug (#1670), and the Twitter thread archiver has been open since 2020 (#345).
- **The Android app is 5 weeks old** with no community.

---

## 6. Worth grabbing wholesale for yearn?

No, not as a fork. Take these specific pieces:

1. **Phase 2: call `abx-dl`, or the ytdlp/gallerydl hooks directly, as the fetch layer.** Copy the yt-dlp flag set ([config.json L119-153](https://github.com/ArchiveBox/abx-plugins/blob/32843a8043bd68d1ec92ba8c7289a86d42ef1d03/abx_plugins/plugins/ytdlp/config.json#L119-L153)), the info.json cookie scrub, the gallery-dl rate-limit `actions` option and the `--plugin-dirs` overrides for Reddit and Twitter. Change gallery-dl auth errors to `failed`, or better to a separate `needs_auth` status, so missing Instagram cookies are visible.
2. **Phase 2: copy the result model.** Use per-item, per-extractor rows with `status ∈ {queued, started, succeeded, noresults, skipped, failed, backoff}` plus `output_json`, `output_files` and `cmd`/`version`. Mirror them into a per-item `index.jsonl` so the database can be rebuilt from disk. Add the automatic backoff that ArchiveBox lacks.
3. **Phase 2: copy the `retry_at`-as-lease compare-and-swap claim** for a SQLite work queue.
4. **Phase 1, small patterns only:**
   - mDNS (`NsdManager`) discovery of the home server;
   - AndroidKeyStore AES-GCM for the upload token;
   - a non-retrying OkHttp client for uploads that must not run twice. Yearn's own idempotency key per capture would make retries safe; ArchiveBox lacks one.
5. **Do not copy:**
   - the Android share parser, which drops subject, title, streams, MIME type and referrer;
   - its online-only submit flow;
   - the opaque share activity;
   - the Django server.

The share parser and online-only submit are the opposite of yearn phase 1's capture-everything, offline-first design.
