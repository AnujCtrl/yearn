# Eval: note/capture apps with Android share targets (Joplin, Memos + MoeMemos, Blinko, Markor)

Research date: 2026-10-26. This doc builds on [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md) and does not repeat its license, stack and status rows. Code was read from shallow clones at these commits. All permalinks below pin to them:

- Joplin `0c9e457` (2026-10-25, sparse checkout of `packages/app-mobile`, `packages/lib`, `packages/app-clipper`)
- Memos `6387991` (2026-10-26)
- MoeMemosAndroid `b29dd4d` (2026-10-17)
- Blinko `b2586d0` (2026-08-27)
- Markor `adacc84` (2026-10-25)

Note that `blinko-space/blinko` redirects to the canonical repo, `blinkospace/blinko`.

**How the issue stats were made.** The tracker numbers come from `gh issue list --state all --limit 300` (PRs excluded). The all-time counts come from the search API (`is:issue is:open|closed`, `label:bug`).

- **"Maintainer reply"** means a comment whose `authorAssociation` is OWNER, MEMBER or COLLABORATOR.
- **"Upvotes"** means GitHub search sorted by `reactions-+1`. The sort was passed as a separate `sort` parameter, not inside `q=`. That result set can include PRs. These are flagged where they show up.
- **The 300-issue window covers a different time span per repo.** For Joplin it is about 2.5 months. For Markor it is about 26 months. Read speed numbers in light of that.

---

## Summary table

| Project | Best at | Quality grade | License | Grab verdict for yearn |
|---|---|---|---|---|
| **Joplin** | The most complete share-intent intake of the group, plus a full capture-to-searchable-archive pipeline: web clipper, OCR, BM25 search, and local-embedding semantic search | **A-**: 3 active core devs and 525 commits in 3 months. Median close is 2.0 days. 5,534 of 5,997 bug-labelled issues are closed. But a share-intent bug sat open for 3.5 years (#8099). | AGPL-3.0-or-later. Joplin Server uses a separate non-free "Personal Use License". | **Ideas plus pattern-level reuse.** Copying code would make yearn AGPL. |
| **Memos** | A small, clean Go server whose `internal/` packages are built to be lifted. The best one is an SSRF-hardened link-metadata fetcher (oEmbed, OpenGraph, Twitter, JSON-LD) | **B**: fast-moving and well tested (201 `_test.go` files out of 521). But the stale bot closes a lot (125 of the last 300 closed issues were "not planned"), and server API breaks keep stranding clients. | MIT | **Straight win for server components**: `linkmeta`, `webhook`, `motionphoto`, and the CEL-to-SQL filter. Not the app itself: it has no share target and no offline mode. |
| **MoeMemosAndroid** | An offline-first Kotlin/Room sync engine: outbox flags, compare-and-set writes, duplicate-on-conflict | **B-**: one maintainer with a 21.6 h median first reply. The 1,066-line sync engine has no dedicated tests. | GPL-3.0 | **Ideas only, unless yearn goes GPL.** Closest to yearn's stack, so the patterns transfer 1:1. |
| **Blinko** | LLM post-processing of each new note: auto-tag against the existing tag list, AI comment, "smart edit". Plus RAG chat over notes. | **D**: 251 of the last 300 issues are open, maintainers replied on 10%, and the median first reply is about 9 days. Only 11 commits in 3 months. Two cross-user IDOR reports are unanswered. The RAG code has real bugs (see below). | GPL-3.0 | **Ideas only.** Take the auto-tag prompt design. Avoid its share bridge and vector code. |
| **Markor** | A "share into…" destination picker that writes to plain files: QuickNote, todo.txt, the most recent file, any file, clipboard, or a calendar event. Plus a URL tracking-parameter scrubber and `PROCESS_TEXT` capture. | **B+**: 2 maintainers reply on 68% of issues, with a 4.2 h median first reply. 1,363 closed vs 192 open. But writes are non-atomic, and there are only 20 test files for about 37k lines of Java. | Apache-2.0. The `net.gsantner.opoc` utilities are Unlicense OR CC0. | **Grab small pieces**: the URL scrubber, the content-URI-to-cache fallback (CC0), and the extra intent filters. |

**Best single feature per project:**
- **Joplin:** its share intake keeps `EXTRA_TEXT`, `EXTRA_SUBJECT` (falling back to `EXTRA_TITLE`), and every `EXTRA_STREAM`, with a guard against Recents re-delivering the same share.
- **Memos:** `internal/linkmeta`, an SSRF-safe metadata fetcher that merges oEmbed, OpenGraph, Twitter, JSON-LD and plain HTML tags.
- **MoeMemos:** the Room outbox sync in `SyncingRepository`.
- **Blinko:** auto-tagging that feeds the model the existing tag list.
- **Markor:** the share-into destination picker plus the tracking-parameter scrubber.

---

## 1. Joplin (laurent22/joplin)

### Standout features (from source)

**Share intake: the most complete of the five, verified.**
- **Filters.** The manifest registers `SEND` and `SEND_MULTIPLE` for `*/*` ([AndroidManifest.xml L107-118](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-mobile/android/app/src/main/AndroidManifest.xml#L107-L118)).
- **Fields kept.** `processIntent` keeps `type` and `EXTRA_TEXT`. The title is `EXTRA_SUBJECT`, falling back to `EXTRA_TITLE`. For `SEND` it keeps the one `EXTRA_STREAM`; for `SEND_MULTIPLE` it keeps every stream. Each stream gets `DISPLAY_NAME` and a MIME type, with an extension-based fallback when the type is null or `image/*` ([SharePackage.java L95-159](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-mobile/android/app/src/main/java/net/cozic/joplin/share/SharePackage.java#L95-L159)).
- **Not kept.** It does not read `EXTRA_HTML_TEXT`, `ClipData`, or the referrer (the source app).
- **Re-delivery guard.** `onHostResume` reads the start intent once per Activity instance. It skips intents flagged `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, so reopening from Recents does not create a duplicate share ([L207-220](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-mobile/android/app/src/main/java/net/cozic/joplin/share/SharePackage.java#L207-L220)). This guard is the fix for [#8099](https://github.com/laurent22/joplin/issues/8099), "Data Share not working after leaving by back button". That issue was filed 2023-04-30 and fixed 2026-10-24 by [PR #16906](https://github.com/laurent22/joplin/pull/16906).
- **Note creation.** A share first creates a *provisional* empty note ([shareHandler.ts L31-33](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-mobile/utils/shareHandler.ts#L31-L33)). Shared data is applied only if that note's title and body are still empty, which prevents overwriting an existing note ([#11479](https://github.com/laurent22/joplin/issues/11479); [note-screen-shared.ts L462-497](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/components/shared/note-screen-shared.ts#L462-L497)). Each stream is then attached as a resource.
- **Provenance fields exist but are unused here.** `NoteEntity` has `source_url`, `author`, `source` and `source_application` ([database/types.ts L264-297](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/database/types.ts#L264-L297)). But `source_application` is set to Joplin's own `appId`, not the app that shared ([Note.ts L980](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/models/Note.ts#L980)), and the share path never fills `source_url`.

**Web clipper pipeline.**
- The browser extension runs Mozilla Readability for "simplified page" clips ([content_scripts/index.js L214-228](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-clipper/content_scripts/index.js#L214-L228)).
- The desktop REST API converts `body_html` to Markdown ([routes/notes.ts L155](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/rest/routes/notes.ts#L155)). It then downloads remote images into local resources and rewrites the URLs ([L330-458](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/rest/routes/notes.ts#L330-L458)).
- This is the "make the capture self-contained" step yearn's server needs.

**Search stack.**
- **Keyword search.** SQLite FTS with a hand-rolled BM25 plus a recency weight ([SearchEngine.ts L377](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/search/SearchEngine.ts#L377)).
- **OCR.** Tesseract text extraction for images and PDFs, so attachments become searchable ([OcrService.ts L18](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/ocr/OcrService.ts#L18)). This closes the requests "Search attached PDF" [#485](https://github.com/laurent22/joplin/issues/485) and OCR [#807](https://github.com/laurent22/joplin/issues/807).
- **Semantic search (new, behind a feature flag).** It is disabled by `featureFlag.enableSemanticSearch` ([L683, L776](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/search/SearchEngine.ts#L776)).
  - It runs a local **multilingual-e5-small** ONNX model ([LocalEmbeddingProvider.ts L14](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/ai/LocalEmbeddingProvider.ts#L14)), downloaded on first use ([EmbeddingModelDownloader.ts L22](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/ai/EmbeddingModelDownloader.ts#L22)), with vectors stored in sqlite-vec.
  - Its chunker picks the chunk size by script: Latin text at 3.5 chars per token, CJK at 1.2 when CJK is 30% or more of the text. It targets 500 tokens per chunk with 10% overlap ([chunker.ts L1-35](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/ai/chunker.ts#L1-L35)).
  - It is desktop-only: builds without ONNX fall back to keyword search.
- **AI and MCP.** There is an MCP server ([services/mcp](https://github.com/laurent22/joplin/tree/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/mcp)) and note-chat providers.

**Sync and E2EE.** Many sync targets with end-to-end encryption, covered by about 20 `Synchronizer.*.test.ts` suites ([services/synchronizer](https://github.com/laurent22/joplin/tree/0c9e457db81108660b96effbc767aea599d6ca90/packages/lib/services/synchronizer)).

### What users love and want

Feature requests now go to the Discourse forum ([ISSUE_TEMPLATE/config.yml](https://github.com/laurent22/joplin/blob/dev/.github/ISSUE_TEMPLATE/config.yml)), so the GitHub upvote leaders are old.

| Request | Upvotes | Status |
|---|---|---|
| Spell check [#275](https://github.com/laurent22/joplin/issues/275) | 361 | closed |
| F-Droid [#15](https://github.com/laurent22/joplin/issues/15) | 129 (250 comments) | still open |
| Hierarchical tags [#375](https://github.com/laurent22/joplin/issues/375) | 121 | open |
| Git as a storage provider [#753](https://github.com/laurent22/joplin/issues/753) | 77 | closed |
| Search attached PDFs [#485](https://github.com/laurent22/joplin/issues/485) | 51 | closed |
| OCR [#807](https://github.com/laurent22/joplin/issues/807) | 47 | closed |

The pattern: users want local, searchable everything (PDF and OCR), and organization richer than flat tags.

### Quality from the tracker

**Last 300 issues (2026-08-13 to 2026-10-25):**
- 176 closed and 124 open.
- Median time to close is 2.0 days; 41% closed within 7 days.
- Maintainers replied on 39%, with a median first reply of 6.6 h.
- Busiest maintainer commenters: laurent22 (82), mrjo118 (79), personalizedrefrigerator (44).

**All time:** 7,874 closed vs 640 open. Bug label: 5,534 closed vs 463 open.

**Commits since 2026-07-26:** 525. Top authors: laurent22 140, renovate 131, personalizedrefrigerator 89, mrjo118 53.

**Recurring themes:**
- **Sync and E2EE share keys:**
  - [#16382](https://github.com/laurent22/joplin/issues/16382) "Note lock key is overwritten on first sync, making locked notes permanently unreadable"
  - [#16301](https://github.com/laurent22/joplin/issues/16301) "E2EE silently fails to generate PPK"
  - [#16304](https://github.com/laurent22/joplin/issues/16304) older clients syncing v3.7-format shares
  - [#16808](https://github.com/laurent22/joplin/issues/16808) sync stuck at a revision
- **Server write churn:** [#16934](https://github.com/laurent22/joplin/issues/16934) and [#16940](https://github.com/laurent22/joplin/issues/16940), about 3-4 GB/day of Postgres writes on idle instances.
- **Android share bugs:**
  - [#16598](https://github.com/laurent22/joplin/issues/16598) "works once, then … stuck" (a duplicate of #8099; a commenter said "Running into this almost every day")
  - [#16241](https://github.com/laurent22/joplin/issues/16241), shared attachments open inside Joplin's task instead of their own app

### Code quality spot-check

- **Strengths.** Typed TypeScript throughout. Test coverage is heavy: 183 `*.test.ts` files in the sparse `packages/lib` checkout and 45 in mobile. Comments explain *why* (for example, the race-condition notes in `note-screen-shared.ts` L465-471).
- **Weak spot: the share path is timer-based.** `shareHandler.ts` navigates, then uses `setTimeout(…, 5)` to open the note ([L44-48](https://github.com/laurent22/joplin/blob/0c9e457db81108660b96effbc767aea599d6ca90/packages/app-mobile/utils/shareHandler.ts#L44-L48)). It is labelled "a bit hacky", which matches the long-lived share bugs.
- **Weak spot: attachments are copied late.** They are copied only when the Note screen mounts, not at intent time.

### License

- **Code:** AGPL-3.0-or-later, unless a subdirectory has its own LICENSE ([LICENSE](https://github.com/laurent22/joplin/blob/dev/LICENSE)).
- **Joplin Server:** a non-free "Joplin Server Personal Use License" ([packages/server/LICENSE.md](https://github.com/laurent22/joplin/blob/dev/packages/server/LICENSE.md)).
- **Branding:** the logos are all rights reserved, and "Joplin" is an EU trademark.
- **What copying means for yearn:** copying any code makes yearn AGPL. Because of the network-use clause, that includes the home server if it serves users. Re-implementing the patterns from the description is fine.

---

## 2. Memos (usememos/memos)

### Standout features (from source)

**`internal/` is built to be published as-is.** Its README states that a package belongs there "only if it could be published tomorrow as a standalone module", and a `depguard` lint rule enforces this ([internal/README.md](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/README.md)).

**`internal/linkmeta`, the link-preview fetcher.** This is the highest-value piece for yearn's server.
- **SSRF-safe connections.** A custom `DialContext` resolves the host and refuses loopback, private, link-local, `0.0.0.0/8` and `100.64/10` addresses. It re-validates every redirect, allows at most 10 redirects, and ignores any proxy ([html_meta.go L44-130](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/linkmeta/html_meta.go#L44-L130)).
- **Bounded cost.** 5 s timeout, 8 concurrent fetches, a 24 h success cache and a 1 min failure cache ([L23-34](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/linkmeta/html_meta.go#L23-L34)).
- **Incremental read.** It reads 512 KB first, then up to 2 MB only if the title or image is still missing.
- **Source merge.** It merges oEmbed, then OpenGraph, Twitter cards, JSON-LD, standard tags and "semantic" tags, in that priority ([L290](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/linkmeta/html_meta.go#L290)).
- **Tests:** a 688-line test file. The API layer adds per-caller rate limits ([memo_service_link_metadata.go](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/server/api/memo_service_link_metadata.go)).

**`internal/motionphoto`.** Detects and extracts the video embedded in Google Motion Photo and MicroVideo JPEGs ([motionphoto.go L13-24](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/motionphoto/motionphoto.go#L13-L24)). This is relevant because Android shares from Google Photos are often Motion Photos.

**`internal/webhook`.** Signed webhook delivery with SSRF protection ([internal/webhook](https://github.com/usememos/memos/tree/6387991fdd1d1ae491b8435708357fa334e19f3a/internal/webhook)).

**`filter/`.** Turns CEL expressions into an intermediate representation, then into dialect-specific SQL for SQLite, MySQL or Postgres ([filter/README.md](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/filter/README.md)). It would give yearn a safe user query language for search.

**Other pieces:**
- **MCP server:** tools are generated from the OpenAPI spec and executed in-process against the real REST handler, so auth logic is not duplicated ([server/mcp/README.md](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/server/mcp/README.md)).
- **Transcription:** audio transcription through a configured speech-to-text or audio-capable LLM provider ([ai_service.go L49](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/server/api/ai_service.go#L49)).
- **Memo links:** memo-to-memo `REFERENCE` and `COMMENT` relations ([store/memo_relation.go L9-15](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/store/memo_relation.go#L9-L15)). This is the raw material for a graph.

**No share target, confirmed by the tracker.** The PWA share-target request [#5837](https://github.com/usememos/memos/issues/5837) was closed as Stale.

### What users love and want

| Request | Upvotes | Status |
|---|---|---|
| Offline Notes [#2690](https://github.com/usememos/memos/issues/2690) | 63 | closed NOT_PLANNED; [#4684](https://github.com/usememos/memos/issues/4684) also closed NOT_PLANNED |
| Multi-tag filter and masonry layout [#5825](https://github.com/usememos/memos/issues/5825) | 18 | closed |
| Unsaved memo text lost after tab inactivity [#5667](https://github.com/usememos/memos/issues/5667) | 16 | closed |
| Link preview [#1528](https://github.com/usememos/memos/issues/1528) | 15 | closed |
| Export [#778](https://github.com/usememos/memos/issues/778) | 13 | closed |

Offline is the loudest request, and the server project explicitly declined it. That gap is what MoeMemos fills. Users praise the "tweet to yourself" timeline and the low footprint (README).

### Quality from the tracker

**Last 300 issues (2026-03-23 to 2026-10-25):**
- 266 closed and 34 open.
- Median time to close is 5.0 days; 48% closed within 7 days.
- Maintainers replied on only 22%, with a median first reply of 41.6 h. boojack wrote 68 of the maintainer comments.

**Of the last 300 closed issues:** 165 completed, 125 not planned, 10 duplicates, and 88 carry the `Stale` label. So "closed" often means auto-closed.

**All time:** 3,029 closed vs 34 open. Issues typed "Bug": 131 closed vs 3 open.

**Commits since 2026-07-26:** 265. Top authors: johnnyjoygh 126, boojack 56, bluedbird 39.

**Recurring themes:**
- **Upgrade fear:**
  - [#5920](https://github.com/usememos/memos/issues/5920) "Upgrade from 26 to 28 deleted all my data" (closed not-planned; the maintainer said the data lives in the host volume)
  - [#6389](https://github.com/usememos/memos/issues/6389) "schema 0.30.1 is too old to upgrade directly"
- **API breaks that strand clients:** MoeMemos tells users on servers newer than 0.31 to use a proxy that converts the API back to 0.21 ([README](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/README.md)).
- **Share and permission bugs:** [#6128](https://github.com/usememos/memos/issues/6128) and [#6131](https://github.com/usememos/memos/issues/6131).

### Code quality spot-check

The best-engineered Go of the group.
- A documented domain glossary ([CONTEXT.md](https://github.com/usememos/memos/blob/6387991fdd1d1ae491b8435708357fa334e19f3a/CONTEXT.md)).
- Lint-enforced package boundaries, and focused files, each covering one thing.
- 201 test files out of 521 `.go` files, plus testcontainers for MySQL and Postgres.

### License

MIT. Copying only requires keeping the copyright and permission notice. `internal/*` can be vendored nearly verbatim if yearn's server is Go.

---

## 3. MoeMemosAndroid (mudkipme/MoeMemosAndroid)

### Standout features (from source)

**Offline-first sync with a local-only mode.** The README promises "Offline-first experience with automatic sync" and "locally on your device (no server required)" ([README](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/README.md)). The implementation:

- **Outbox rows.** `MemoEntity` has a local `identifier` (UUID) plus a nullable `remoteId`, and outbox flags `needsSync`, `isDeleted` (a tombstone), `lastModified` and `lastSyncedAt` ([MemoEntity.kt L13-38](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/local/entity/MemoEntity.kt#L13-L38)).
- **Compare-and-set writes.** Writes are conditional on `lastModified`, so a sync landing in the middle of editing never clobbers autosaved text ([SyncingRepository.kt L161](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/repository/SyncingRepository.kt#L161)). `lastModified` is bumped strictly upward, so two writes in the same millisecond still differ (L979-985).
- **Duplicate on conflict.** On a conflict, the local copy (and its resources) is cloned into a new memo and pushed as a create, then the remote version is applied over the original ([L685-707](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/repository/SyncingRepository.kt#L685-L707)). Nothing is lost.
- **Resources before the memo.** Attachments upload before their memo (`ensureUploadedResources`, L741).
- **One pluggable repository.** A single abstraction covers Local, Memos v0 and Memos v1 backends ([data/repository](https://github.com/mudkipme/MoeMemosAndroid/tree/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/repository)).
- **Coalesced pushes.** `DeferredPushScheduler` debounces pushes per memo ([L9-17](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/repository/DeferredPushScheduler.kt#L9-L17)).
- **Flush on leave.** The editor flushes autosave on `ON_STOP`, "because the deferred push only lives as long as the process" ([MemoInputPage.kt L419-436](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/ui/page/memoinput/MemoInputPage.kt#L419-L436)).

**Share intake: minimal.**
- **Filters.** `SEND` for `image/*` and `text/plain`, and `SEND_MULTIPLE` for `image/*` ([AndroidManifest L37-51](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/AndroidManifest.xml#L37-L51)).
- **Fields kept.** `ShareContent.parseIntent` keeps only `EXTRA_TEXT` and the streams ([ShareContent.kt L12-31](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/data/model/ShareContent.kt#L12-L31)).
- **Dropped.** It drops `EXTRA_SUBJECT`. Videos and PDFs cannot be shared in at all, which matches the open request [#169](https://github.com/mudkipme/MoeMemosAndroid/issues/169).
- **Flow.** A share opens the editor prefilled with the content; autosave then persists it ([Navigation.kt L146-151](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/ui/page/common/Navigation.kt#L146-L151)).

**Quick capture.** Launcher shortcuts for compose and search ([shortcuts.xml](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/res/xml/shortcuts.xml)) and a Glance home-screen widget ([MoeMemosGlanceWidget.kt](https://github.com/mudkipme/MoeMemosAndroid/blob/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/main/java/me/mudkip/moememos/widget/MoeMemosGlanceWidget.kt)).

### What users love and want

| Request | Upvotes | Status |
|---|---|---|
| Support offline use [#50](https://github.com/mudkipme/MoeMemosAndroid/issues/50) | 29 | closed, delivered |
| Cache for mobile [#262](https://github.com/mudkipme/MoeMemosAndroid/issues/262) | 14 | closed |
| Local memos [#4](https://github.com/mudkipme/MoeMemosAndroid/issues/4) | 7 | closed |
| Grid layout [#337](https://github.com/mudkipme/MoeMemosAndroid/issues/337) | 6 | open |
| Widget [#244](https://github.com/mudkipme/MoeMemosAndroid/issues/244) | 3 | open |

[#223](https://github.com/mudkipme/MoeMemosAndroid/issues/223) offered 500 CNY to whoever added a local cache. Offline capture is the reason people pick this client, which validates yearn's core premise.

### Quality from the tracker

**All 247 issues (2022-11 to 2026-10):**
- 149 closed and 98 open; 146 of the closures were "completed".
- Median time to close is 12.9 days.
- Maintainers replied on 41%, with a median first reply of 21.6 h.
- Single maintainer: mudkipme wrote all 138 maintainer comments.

**Commits since 2026-07-26:** 54 (mudkipme 31; 8 are authored by `claude`).

**Recurring themes:**
- **Server-version compatibility:** [#238](https://github.com/mudkipme/MoeMemosAndroid/issues/238), [#264](https://github.com/mudkipme/MoeMemosAndroid/issues/264), [#305](https://github.com/mudkipme/MoeMemosAndroid/issues/305), [#360](https://github.com/mudkipme/MoeMemosAndroid/issues/360), [#372](https://github.com/mudkipme/MoeMemosAndroid/issues/372).
- **Login:** [#20](https://github.com/mudkipme/MoeMemosAndroid/issues/20), [#275](https://github.com/mudkipme/MoeMemosAndroid/issues/275), [#295](https://github.com/mudkipme/MoeMemosAndroid/issues/295).
- **Attachment upload:**
  - [#376](https://github.com/mudkipme/MoeMemosAndroid/issues/376) "very likely to fail sending attachments" (open)
  - [#399](https://github.com/mudkipme/MoeMemosAndroid/issues/399) image uploaded twice (fixed)
  - [#347](https://github.com/mudkipme/MoeMemosAndroid/issues/347) 5 MB upload fails behind Cloudflare
- **Timestamps:** [#333](https://github.com/mudkipme/MoeMemosAndroid/issues/333) offline timestamps overwritten by sync time (fixed), and [#373](https://github.com/mudkipme/MoeMemosAndroid/issues/373) created dates not preserved (open). Both are a warning for yearn: keep capture time as an immutable field.

### Code quality spot-check

- **Strengths.** Idiomatic Kotlin with Compose, Room, Hilt and coroutines. The sync code has unusually good intent comments.
- **Weakness.** About 1.3k lines of tests for about 16.8k lines of app code. There is no test for `SyncingRepository` (1,066 lines); only `DeferredPushSchedulerTest`, backup and location tests exist ([src/test](https://github.com/mudkipme/MoeMemosAndroid/tree/b29dd4d58930581456a0e659a2ab4eb93b24db27/app/src/test)).

### License

GPL-3.0. Copying code makes yearn's Android app GPL-3.0, and requires shipping source with any distributed APK. (The iOS sibling is MPL-2.0, per the prior survey.)

---

## 4. Blinko (blinkospace/blinko)

### Standout features (from source)

**AI post-processing on note save** ([aiServer/index.ts L351-560](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/index.ts#L351)). There are three modes:
- **comment:** writes an AI summary as a comment from "Blinko AI".
- **tags:** a tag agent gets `Existing tags list: [...]` plus the note. It returns at most 5 tags, which are appended to the note as `#tags` ([L481-500](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/index.ts#L481-L500)).
- **smartEdit:** an agent with tools rewrites the note.

The prompts are user-configurable. Feeding the model the existing taxonomy is the idea yearn wants for auto-organizing.

**RAG.**
- Notes are chunked from Markdown (Mastra `MDocument`), embedded with any of about 7 providers, and stored in a libSQL vector index ([L79-137](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/index.ts#L79-L137)).
- PDF and other attachments are embedded too.
- Natural-language search goes through `enhanceQuery` ([note.ts L108-117](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/routerTrpc/note.ts#L108-L117)).
- There is also an MCP *client* bridge, so chat can call external tools ([aiServer/mcp](https://github.com/blinkospace/blinko/tree/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/mcp)).

**Two capture modes.** Quick "blinko" thoughts and long-form notes share one table, split by a `type` column. Notes support references, history, `isReviewed` for the daily review, and public share links with password, expiry and view caps ([schema.prisma L70-97](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/prisma/schema.prisma#L70-L97)).

**Share intake: the weakest of the five.**
- **How it works.** `MainActivity` handles only `ACTION_SEND`; `SEND_MULTIPLE` is not handled. It serializes the intent to JSON, waits 1.5 s, and injects it with `evaluateJavascript` into WebView `localStorage` ([MainActivity.kt L99-185](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/app/src-tauri/gen/android/app/src/main/java/com/blinko/app/MainActivity.kt#L99-L185)). The JS side polls every 800 ms ([hooks.ts L214-263](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/app/src/lib/hooks.ts#L214-L263)).
- **Text wins over the stream.** If both are present, only the text is used, so an image shared with a caption loses the image.
- **Subject collected but unused.** `subject` is read but never used.
- **Raw text is altered.** Surrounding quotes are stripped from the shared text on both the Kotlin and JS sides.
- **A second share can be dropped.** The injector refuses to write if an earlier payload has not been consumed yet.

### What users love and want

Engagement is low: the top issue has 8 👍.

| Issue | Upvotes | Status |
|---|---|---|
| Offline mode for mobile client [#898](https://github.com/blinkospace/blinko/issues/898) | 8 | open, no maintainer reply |
| Network error updating AI models [#611](https://github.com/blinkospace/blinko/issues/611) | 6 | open |
| Docker image not available [#971](https://github.com/blinkospace/blinko/issues/971) | 6 | open |
| `SQLITE_ERROR: no such table: blinko` [#646](https://github.com/blinkospace/blinko/issues/646) | 6 (28 comments) | open |
| Mind-map feature [#616](https://github.com/blinkospace/blinko/issues/616) | 2 | open |

Users praise AI tagging and RAG, but most of the volume is "AI doesn't work" reports.

### Quality from the tracker

**Last 300 issues (2025-08-09 to 2026-10-21):**
- 251 open and 49 closed.
- Maintainers replied on 10%, with a median first reply of 222 h.
- 27% have no comments at all. Triage answers often come from `dosubot` (for example on [#867](https://github.com/blinkospace/blinko/issues/867)).

**All time:** 401 open vs 504 closed. Bug label: 225 open vs 211 closed.

**Activity:** 11 commits since 2026-07-26; the last release is 1.8.8 on 2026-06-20.

**AI and RAG failures:**
- [#1094](https://github.com/blinkospace/blinko/issues/1094) "RAG not work"
- [#988](https://github.com/blinkospace/blinko/issues/988) embedding model "useless, cannot retrieve notes"
- [#1040](https://github.com/blinkospace/blinko/issues/1040) and [#1247](https://github.com/blinkospace/blinko/issues/1247) PDF text indexed but not passed into RAG
- [#1126](https://github.com/blinkospace/blinko/issues/1126) Ollama `/embed` vs `/api/embed`
- [#1163](https://github.com/blinkospace/blinko/issues/1163) embedding size mismatch
- [#1232](https://github.com/blinkospace/blinko/issues/1232) segfault on a forced re-index

**Data loss and upgrades:**
- [#646](https://github.com/blinkospace/blinko/issues/646), [#852](https://github.com/blinkospace/blinko/issues/852) migrations missing
- [#1077](https://github.com/blinkospace/blinko/issues/1077) data lost after `compose down/up`
- [#1159](https://github.com/blinkospace/blinko/issues/1159) "part of the data suddenly lost"
- [#1147](https://github.com/blinkospace/blinko/issues/1147) the scheduled-backup feature disappeared

**Security:**
- [#1217](https://github.com/blinkospace/blinko/issues/1217) cross-user private note disclosure, and [#1218](https://github.com/blinkospace/blinko/issues/1218) cross-user AI conversation read/write. Both are IDORs, both opened 2026-07-06, and both have 0 comments.
- [#1208](https://github.com/blinkospace/blinko/issues/1208) share links allow visitors to edit.

**Performance and mobile:**
- [#1222](https://github.com/blinkospace/blinko/issues/1222) the frontend burns 20-45% of a CPU core while idle
- [#1205](https://github.com/blinkospace/blinko/issues/1205) the APK reports the wrong version

### Code quality spot-check

These are concrete bugs that explain the RAG complaints:

1. **Stale vectors survive updates.** On update, `queryAndDeleteVectorById(id)` is called without `await` ([index.ts L102](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/index.ts#L102)). Its SQL also deletes only `LIMIT 1` vector per note ([aiModelFactory.ts L30-42](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/aiModelFactory.ts#L30-L42)). The other chunks of a multi-chunk note stay in the index, and the unawaited delete can race the new insert.
2. **Timestamps pollute the embeddings.** Every chunk is embedded as `chunk.text + 'Create At: …ISO… Update At: …'` ([L106](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/index.ts#L106)).
3. **One shared index, filtered after the cut.** All users share a single vector index named `blinko`. The query takes the global `topK` (default 3) *before* filtering by `accountId` ([aiModelFactory.ts L65-90](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/aiModelFactory.ts#L65-L90)). On a multi-user instance, other users' chunks can fill the top 3 and the search comes back empty. There is no keyword fallback.
4. **An API key is logged.** `console.log('config', config.tavilyApiKey)` writes the key to the logs ([webExtra.ts L15](https://github.com/blinkospace/blinko/blob/b2586d03fddb796252f050a96922879bf4a41fad/server/aiServer/tools/webExtra.ts#L15)).
5. **Barely tested.** About 8 test files for about 18.8k lines of server TypeScript. `@ts-ignore` appears in the vector code.

### License

GPL-3.0. Copying code makes yearn GPL-3.0. Ideas are free.

---

## 5. Markor (gsantner/markor)

### Standout features (from source)

**A share-into destination picker.**
- **Filters.** One intent filter takes `SEND` for `text/plain`, `text/*` and `*/*`, plus Gmail `AUTO_SEND` and Google Assistant `CREATE_NOTE`/`SELF_NOTE` ("note to self" by voice). A separate alias takes `PROCESS_TEXT`, so you can select text in any app and pick "Markor" from the menu ([AndroidManifest L209-220, L1119-1130](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/AndroidManifest.xml#L209-L220)).
- **Flow.** The share screen shows the text in an editable box. The user then picks a destination: QuickNote, the todo.txt file, the most recent file, choose or create a file, search for a target, clipboard, calendar event, open in browser, or re-share ([DocumentShareIntoFragment.java L561-600](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/markor/activity/DocumentShareIntoFragment.java#L561-L600)).
- **Configurable formatting.** A prefix template with a `{{text}}` token and date and time placeholders controls how the text is written. todo.txt targets get today's date and a single-line task. A "format as link" toggle converts `title url` lines into Markdown links ([L380-440](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/markor/activity/DocumentShareIntoFragment.java#L380-L440)).

**URL tracking-parameter scrubber.**
- `extractShareText` prefixes `EXTRA_SUBJECT` and, when `EXTRA_TEXT` is a bare URL, cleans it.
- The regex drops `utm_*`, `si`, `fbclid`, `gclid`, `msclkid`, `ref`, `__mk_` and others, plus `qid` and `sr` on Amazon ([L641-663](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/markor/activity/DocumentShareIntoFragment.java#L641-L663)).
- yearn should store the raw URL *and* a cleaned key for de-duplication. Note that `si=` is exactly YouTube's and Spotify's share-tracking parameter.

**Content-URI-to-file resolver** ([GsContextUtils.java L1598-1715](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/opoc/util/GsContextUtils.java#L1598)).
- It first tries heuristics that map `content://` URIs from about 8 file managers back to real paths.
- If those fail, it copies the stream into app-private cache ("Proxy file to app-private storage", L1703), which survives the share grant expiring.
- Caveat: the copy reads the whole file into a `byte[]` (L1706), which risks running out of memory on large videos.
- This file is CC0/Unlicense, so it can be copied freely.

**Plain files.** No database, no lock-in. todo.txt, Markdown, AsciiDoc, org-mode, Zim and other formats. Optional per-file encryption.

### What users love and want

| Request | Upvotes | Status |
|---|---|---|
| Org-mode [#182](https://github.com/gsantner/markor/issues/182) | 21 | closed, delivered |
| File templates [#676](https://github.com/gsantner/markor/issues/676) | 20 | closed, delivered |
| Search [#155](https://github.com/gsantner/markor/issues/155) | 15 | closed, delivered |
| todo.txt due-date reminders [#716](https://github.com/gsantner/markor/issues/716) | 15 | open since 2019 |
| Zim [#393](https://github.com/gsantner/markor/issues/393) | 14 | closed, delivered |
| AsciiDoc [#808](https://github.com/gsantner/markor/issues/808) | 12 | closed, delivered |

[#1337](https://github.com/gsantner/markor/issues/1337) is a PR (recursive content search). Users love format breadth and offline plain files; the open asks are reminders and better handling of large files.

### Quality from the tracker

**Last 300 issues (2024-08-21 to 2026-10-21):**
- 163 closed and 137 open.
- Median time to close is 4.7 days.
- Maintainers replied on 68%, with a median first reply of 4.2 h. gsantner wrote 237 comments and harshad1 153.

**All time:** 1,363 closed vs 192 open. The bug label is rarely used (113 closed, 4 open), so a bug ratio is not meaningful here.

**Activity:** 22 commits since 2026-07-26; release v2026.10.0 came out 2026-10-25.

**Recurring themes:**
- **Write safety:**
  - [#2516](https://github.com/gsantner/markor/issues/2516) "Files get wiped if saving when filesystem is full" (open)
  - [#2923](https://github.com/gsantner/markor/issues/2923) an encrypted file that fails to decrypt opens empty, and editing it overwrites the ciphertext (open)
  - [#2593](https://github.com/gsantner/markor/issues/2593) part of a line lost or duplicated
- **Large files:** [#2505](https://github.com/gsantner/markor/issues/2505), and [#2814](https://github.com/gsantner/markor/issues/2814) a crash opening a 306 KB file.
- **Share:** [#2672](https://github.com/gsantner/markor/issues/2672) browser shares failed when Markor was not already running (fixed).

### Code quality spot-check

- **Non-atomic saves.** `Document.saveContent` overwrites the file in place. Its safety net is "Try write 2x" plus a size check, with no temp-file-and-rename ([Document.java L284-350](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/markor/model/Document.java#L335)). This directly explains #2516.
- **Share-into appends rewrite the whole file.** Each append reads the whole file and rewrites it ([DocumentShareIntoFragment.java L311-317](https://github.com/gsantner/markor/blob/adacc842157c41b660340d2ecda624d5b81e5323/app/src/main/java/net/gsantner/markor/activity/DocumentShareIntoFragment.java#L311-L317)).
- **Old-style Java.** Pre-Kotlin Android with large files. 20 test files for about 37k lines.

### License

- **App:** Apache-2.0 ([LICENSE.txt](https://github.com/gsantner/markor/blob/master/LICENSE.txt)). It is permissive: keep the notice and the NOTICE file, and mark changes.
- **`net.gsantner.opoc.*`:** Unlicense OR CC0-1.0, per the file headers. It can be copied with no obligations.

---

## For yearn: what to grab

**Joplin: ideas plus re-implemented patterns, not code (AGPL).**
- Mirror its intake field set, then go further: also capture `EXTRA_HTML_TEXT`, `ClipData` items, the referrer, and the `SEND_MULTIPLE` streams.
- Copy two guards: skip intents launched from history, and only populate an empty provisional record. Copy the bytes at intent time, not when the screen mounts.
- For the server: the clipper pipeline (Readability, then HTML to Markdown, then download images as resources), Tesseract OCR on images and PDFs, and the script-aware chunker with local e5-small embeddings in sqlite-vec plus BM25.

**Memos: straight win, for server components (MIT).**
- If the home server is Go, vendor `internal/linkmeta`: SSRF-safe link metadata with oEmbed > OpenGraph > Twitter > JSON-LD. It is the first-pass enrichment for every captured URL before the platform-specific fetchers run.
- Also take `motionphoto` (shares from Google Photos), `webhook`, and maybe `filter` (a CEL search language).
- The memo `REFERENCE` relation is a model for graph edges.
- Do not adopt the server as a backend: it has no share target, declined offline mode, and has an API-breaking history.

**MoeMemosAndroid: ideas only (GPL-3), but the most transferable to yearn's Kotlin stack.**
- Design yearn's Room outbox the same way: local UUID plus nullable `remoteId`, `needsSync`, an `isDeleted` tombstone, a monotonic `lastModified` used for compare-and-set, duplicate-on-conflict, attachments uploaded before their record, debounced pushes, and a flush on `ON_STOP`.
- Avoid its mistakes: keep an immutable capture timestamp (#333, #373) and keep `EXTRA_SUBJECT`.

**Blinko: ideas only (GPL-3, D quality).**
- Take the auto-tag design: give the LLM the existing tag list, cap the output at about 5 tags, and offer comment / tags / smart-edit modes. Also the daily-review flag.
- Its bugs are a checklist of what to avoid in yearn's server:
  - Delete *all* of a note's chunks on update, and await the delete.
  - Do not embed timestamps into chunk text.
  - Filter by user *inside* the vector query.
  - Run hybrid keyword plus vector search.
  - Never log API keys.
- Do not copy its WebView share bridge.

**Markor: grab small pieces (Apache-2.0 / CC0).**
- Copy the tracking-parameter regex as yearn's URL de-duplication key, alongside the raw URL. Copy the content-URI-to-cache fallback (CC0), but stream the copy instead of buffering it in memory.
- Add the `PROCESS_TEXT` and Google `CREATE_NOTE`/`SELF_NOTE` filters so text selection and "note to self" also land in yearn.
- The destination picker is the opposite of yearn's capture-raw-without-asking flow. At most, take it as an optional "append to…" action.
- Avoid its non-atomic file writes.
