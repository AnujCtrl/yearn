# Evaluation: graph-capable knowledge apps (Logseq, SiYuan, Trilium, Anytype)

Research date: 2026-10-26. Companion to [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md); that file already covers license, stack, release status and share-intent handling, so none of it is repeated here.

**Method.** Each repo was shallow, sparse, blobless cloned at the commits below. Only graph, linking and share code was checked out and read; nothing was built or run. Issue data comes from `gh issue list --state all --limit 300` (PRs excluded) and from `search/issues?...&sort=reactions-+1`. Raw JSON is in the session scratchpad (`kg-issues/`). "First response" means the first comment by someone other than the reporter, excluding bots. Each project's 300-issue window covers a different span of time, so the windows are stated per project. Anything not read from a primary source is marked **[unverified]**.

Pinned commits: logseq `a37eff1`, siyuan `bfe3610`, siyuan-android `5eadc5b`, Trilium `b4aefdd`, TriliumDroid `11c2af1`, anytype-heart `92f654a`, anytype-kotlin `ca3fd13`, anytype-ts `3a3ecd1` (desktop client, read because Anytype's graph *renderer* lives there).

---

## Summary table

| Project | Best at | Graph view quality | Quality grade | License | Grab verdict |
|---|---|---|---|---|---|
| **Logseq** (45.4k★) | Block-level outliner over a Datascript DB. In the DB version, typed tags/classes become graph clusters. | **Good, recently rebuilt.** PixiJS 8 + d3-force, a precomputed layout, a "tags-and-objects" cluster mode with convex hulls, and explicit level-of-detail budgets (labels, edges, nodes). Still 50👍 on an alias-duplication bug and an old "unusable at ~3.5k nodes" report from the file-graph era. | **C**: 99 of 181 closed issues in the sample were closed by the stale bot. Maintainers commented on 9 of 300 issues. The DB version is beta and the README warns "data loss is possible". | AGPL-3.0 | Ideas only. Its graph LOD constants and two view modes are the best spec to copy. The code is ClojureScript tied to Datascript. |
| **SiYuan** (46.9k★) | Block-level refs plus **virtual refs**: auto-linking with Aho-Corasick over doc titles, aliases and names. Also an editable **list-as-mind-map** view. | **Best engineered.** Custom Barnes-Hut force layout on typed arrays in a Web Worker, a WebGL renderer with Canvas2D fallback, and a kernel cap of 16,384 blocks plus a min-refs filter. vis-network was replaced in Aug 2026. Nodes can be blocks (headings, paragraphs), not only documents. | **A−**: median maintainer reply about 42 min and 295/300 closed. However, about a third of the tracker is the lead dev filing his own changelog. The Android shell has issues disabled. | AGPL-3.0 | Ideas only (Go kernel + TS). Take the **virtual-ref algorithm**, the **worker + WebGL pattern** and **list→mind-map**. A port of the Barnes-Hut core is small and self-contained, but it is AGPL. |
| **Trilium** (38.6k★) + TriliumDroid (409★) | Typed, inheritable **relations as attributes**. Three different map types: link/tree note map, hand-placed relation map, mind map. Also **Similar Notes**, a non-ML relatedness scorer. | **Solid but small-scale.** force-graph (Canvas2D, d3 forces) with a hard 1,000-note guard. Link-map neighbour depth is 3 hops. Users ask for a better default layout. The relation map is manual and not auto-laid-out. | **B+**: median close 1.7 days. One lead (eliandoran) gives most replies; first human reply median 5.6 h. TriliumDroid is a solo developer (median 6 h) on a sync-version treadmill. | AGPL-3.0 | Ideas only. The **Similar Notes scorer** (title, ancestor, attribute and heading word rewards plus creation-time proximity) is about 490 lines and maps almost directly onto yearn's "related captures". Its underlying libraries are MIT. |
| **Anytype** (kotlin 984★, heart 425★, ts 9.0k★) | **Typed objects and named relations**, where the graph is just *a query result* (sets/collections). **Timeline playback** grows the graph by creation date. Graph clusters by type. | **Desktop only.** OffscreenCanvas worker, PixiJS 8, d3-force and d3-force-cluster, with edge types Link vs Relation. **No graph view in the Android app.** Users ask for graph filters and a local-graph sidebar. | **C−**: in anytype-kotlin 81/98 issues are open and maintainers replied on 5/98. heart issues get bulk "housekeeping" closes after about a year. The real tracker is the community forum. | Any Source Available License 1.0 (non-OSI, non-commercial / "Allowed Networks" only) | Ideas only. Do not copy code into a project you may ever sell or relicense. The **graph = filtered query + edge-type schema** model and the **timeline** idea fit yearn best. |

**Single best feature per project.**
- **Logseq:** the graph view's two modes, "tags-and-objects" clusters vs "all pages", with explicit LOD budgets.
- **SiYuan:** virtual refs, i.e. automatic, unstored links from any known title or alias found in text.
- **Trilium:** Similar Notes, deterministic related-items scoring with no embeddings.
- **Anytype:** the graph as a view over any filtered set, plus creation-date timeline playback.

---

## Logseq ([logseq/logseq](https://github.com/logseq/logseq))

### Standout features
- **Graph data is built in the DB worker and comes in three scopes.** `build-graph` dispatches to `:global`, `:page` and `:block` ([graph_view.cljs#L1059](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L1059)).
  - A **page graph** contains refs out, pages that mention it, tags and class-extends edges, plus the links among those neighbours (one ring of second-order edges) ([#L995](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L995)).
  - A **block graph** gives block-level citations ([#L1038](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L1038)).
- **Two global modes.** The default is `:tags-and-objects`: DB-version classes/tags act as hubs and objects attach to them ([build-tags-and-objects-graph#L420](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L420)). The other is `:all-pages`. Above 10,000 pages the worker switches to a datom-scan builder that caps links at 20,000 ([#L55-L56](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L55), [bounded-visible-page-links#L628](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/worker/graph_view.cljs#L628)).
- **The renderer was rewritten on PixiJS 8 + d3-force 3.** Dependencies are in [package.json](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/package.json); the code is [pixi.cljs](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi.cljs) (2,677 lines) and [pixi/logic.cljs](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi/logic.cljs) (1,473 lines). Commit history shows May–Jul 2026 work by tiensonqin, RCmerci and megayu.
  - **The layout is precomputed, not live.** The simulation is built, stopped, ticked N times synchronously, and then drawn. Forces are link, charge (distanceMax 420), collide, y, and per-tag cluster-x/y ([layout-nodes#L1378](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi/logic.cljs#L1378)).
  - **The page graph uses a deterministic ring layout** by BFS depth with golden-angle jitter ([page-layout-nodes#L1083](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi/logic.cljs#L1083)). Tag clusters get convex hulls ([convex-hull#L746](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi/logic.cljs#L746)).
  - **Explicit LOD budgets** ([#L664-L678](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi/logic.cljs#L664)):
    - "fast" layout at ≥10k nodes (≥2.5k in all-pages mode);
    - draw-edge limits 8,000, 3,600 and 28,000;
    - render-node limits 12,000 and 2,200;
    - tag-force subset of 900;
    - label candidates ranked tags-first then by degree, capped at 1,400/900 ([pixi.cljs#L123](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi.cljs#L123)), with 170–240 labels per viewport.
- **Auto-linking means "Unlinked references" only.** It is a text-match suggestion panel per page ([reference.cljs#L64](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/components/reference.cljs#L64)), and nothing is written to the graph. The DB-version alias gap ([#12438](https://github.com/logseq/logseq/issues/12438)) was fixed in 2026.

### What users love and want
- **Most-upvoted requests:**
  - XDG config dirs: 79👍 [#3462](https://github.com/logseq/logseq/issues/3462).
  - Self-host/docker: 51👍 [#4107](https://github.com/logseq/logseq/issues/4107).
  - Reminders: 43👍 [#5064](https://github.com/logseq/logseq/issues/5064).
  - Android Nextcloud folders: 36👍 [#9403](https://github.com/logseq/logseq/issues/9403).
  - F-Droid: 19👍 [#10449](https://github.com/logseq/logseq/issues/10449).
- **Graph-specific complaints** (72 open+closed issues have "graph view" in the title):
  - aliases duplicating nodes: 50👍, open since 2022 [#4709](https://github.com/logseq/logseq/issues/4709);
  - `exclude-from-graph-view` ignored in the page graph: 20👍 [#6817](https://github.com/logseq/logseq/issues/6817);
  - "unusably slow in a garden with ~3500 nodes … hangs … when trying to use graph view" [#2089](https://github.com/logseq/logseq/issues/2089) (file-graph era, still open);
  - nodes teleporting after drag [#11409](https://github.com/logseq/logseq/issues/11409).
  - The Pixi rewrite targets exactly these, but there are no post-rewrite user reports in the sample **[unverified whether fixed]**.

### Quality from the tracker
- **Sample:** 300 issues, 2025-10-01 → 2026-10-25. 119 open, 181 closed.
- **The stale bot does most of the closing.** 99 closed issues carry `:status/automatic-closing` and 111 carry `automatic-stale`. Excluding those, the median close is 2.8 days (n=78). The overall 200-day median is the bot's timer.
- **Maintainers rarely appear.** Members or collaborators commented on only **9/300** (benjypng, logseq-cldwalker, xyhp915). The top "responder" is github-actions (106).
- **Recurring themes:**
  - sync: 15 titles, e.g. [#13362](https://github.com/logseq/logseq/issues/13362), [#12775](https://github.com/logseq/logseq/issues/12775);
  - data loss: 10, e.g. "Loss of graphs" [#13602](https://github.com/logseq/logseq/issues/13602), "DB-graph sync completes … but graph renders empty on iOS (data loss …)" [#12905](https://github.com/logseq/logseq/issues/12905);
  - mobile: 24, e.g. "Update on Android?" [#13189](https://github.com/logseq/logseq/issues/13189), DB-vs-OG APK collision [#12924](https://github.com/logseq/logseq/issues/12924);
  - security: arbitrary file write → RCE [#13596](https://github.com/logseq/logseq/issues/13596), open.
- **Android status:**
  - The only DB-version Android build is `Logseq-android-2.0.2-alpha+nightly.20261025.apk` in the nightly release.
  - 2.0.2 itself ships iOS TestFlight only (release notes via `gh api repos/logseq/logseq/releases`).
  - The README still says "Android coming soon" and "**data loss is possible**" ([README#L71-L73](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/README.md#L71)).

### Code spot-check
- **Graph code:** pure functions, separated into `logic` and a Pixi shell, with named constants. Tests exist (408 test files repo-wide; `clj-e2e/test/logseq/e2e/graph_test.clj`).
- **Weak spot:** functions are flagged `^:large-vars/cleanup-todo` (e.g. `create-label-manager!`, [pixi.cljs#L373](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/src/main/frontend/extensions/graph/pixi.cljs#L373)).
- **Process:** 64 files under `.agents/` show heavy agent-driven development. Recent PR titles ("test: tier-1 cases the suite let through …") point the same way.

### License
AGPL-3.0 ([LICENSE.md](https://github.com/logseq/logseq/blob/a37eff1d36aa7d0802b0b526f70aab0113191d53/LICENSE.md)). Copying code makes the combined work AGPL. The yearn home-server would then have to offer its source to every user who reaches it over the network (§13). The libraries underneath are permissive: pixi.js MIT, d3-force ISC, graphology MIT (checked via `gh api repos/...`).

---

## SiYuan ([siyuan-note/siyuan](https://github.com/siyuan-note/siyuan) + [siyuan-android](https://github.com/siyuan-note/siyuan-android))

### Standout features
- **Virtual references (auto-linking).** This is the strongest auto-linking of the four.
  - The kernel collects keywords from block names, aliases, anchors and doc titles via SQL, plus user include/exclude lists ([virutalref.go#L295-L341](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/model/virutalref.go#L295)).
  - It builds an **Aho-Corasick** matcher (`ClarkThan/ahocorasick`, MIT) and scans each block's text ([#L87-L121](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/model/virutalref.go#L87)).
  - Hits are cached per box+doc for 10 minutes, rendered as link-like spans, and never written to the document.
  - It is one of the top-voted features ([#2249](https://github.com/siyuan-note/siyuan/issues/2249), 23👍).
- **Block-granular graph.** Nodes can be headings, paragraphs, math, lists and tags, not just documents (type filter in [conf/graph.go](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/conf/graph.go)).
  - The kernel caps the graph at `defaultGraphMaxBlocks = 1024 * 16` ([#L20](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/conf/graph.go#L20)) and prunes by `MinRefs` ([graph.go#L482](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/model/graph.go#L482)).
  - The local graph grows forward and back links with configurable depth ([growLinkedNodes#L304](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/model/graph.go#L304)).
  - Global graphs can be filtered by query ([BuildGraph#L170](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/kernel/model/graph.go#L170)).
- **A custom graph engine replaced vis-network in Aug 2026** ([#18642](https://github.com/siyuan-note/siyuan/issues/18642), [#18890](https://github.com/siyuan-note/siyuan/issues/18890)). All 9 commits are by 88250.
  - `GraphForceLayout` stores positions, velocities, sources and targets in `Float32Array`/`Uint32Array` and uses a **Barnes-Hut quadtree** with θ²=0.25 ([layoutCore.ts#L3](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/layoutCore.ts#L3), [#L202](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/layoutCore.ts#L202)).
  - The timestep adapts to size: 0.12 above 2,048 nodes; the iteration budget is 96 above 4,096 nodes and 320 below ([#L117](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/layoutCore.ts#L117), [#L156](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/layoutCore.ts#L156)).
  - It runs in a **Web Worker** that posts transferable position buffers ([layoutWorker.ts](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/layoutWorker.ts), spawned at [GraphEngine.ts#L374](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/GraphEngine.ts#L374)).
  - Drawing uses a **WebGL renderer** with automatic Canvas2D fallback on init failure or context loss ([GraphEngine.ts#L330-L359](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/layout/dock/graph/GraphEngine.ts#L330)), with a separate label layer.
  - Each module has a sibling `*.test.ts`.
- **List → mind map.** Any list block can be shown and edited as a mind map.
  - It supports layout, drag-move, add/delete nodes, cross-branch "relations" with labels and styles, and summaries.
  - The list stays the source of truth: metadata is stored as attributes on the list ([model.ts#L90-L193](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/protyle/render/listMindmap/model.ts#L90), [layoutListMindmap#L487](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/app/src/protyle/render/listMindmap/model.ts#L487)).
  - It is 19 source files with a 4,953-line model test.
- **Android shell.**
  - It embeds the Go kernel as `libs/kernel.aar` ([build.gradle#L119](https://github.com/siyuan-note/siyuan-android/blob/5eadc5be8ec07c888e5cfe99468e58b221635590/app/build.gradle#L119)), so the phone runs the full kernel offline, graph included.
  - The share target writes "shorthand" inbox entries ([ShortcutActivity.java#L154-L200](https://github.com/siyuan-note/siyuan-android/blob/5eadc5be8ec07c888e5cfe99468e58b221635590/app/src/main/java/org/b3log/siyuan/ShortcutActivity.java#L154)).
  - It has a crash-safe draft store with a debounced 250 ms save ([ShorthandDraftStore.java](https://github.com/siyuan-note/siyuan-android/blob/5eadc5be8ec07c888e5cfe99468e58b221635590/app/src/main/java/org/b3log/siyuan/ShorthandDraftStore.java)) plus recovery tests.

### What users love and want
- **Top requests:**
  - card whiteboard: 110👍, open [#2024](https://github.com/siyuan-note/siyuan/issues/2024);
  - database table, gallery and kanban views: 97/52/45👍 [#2829](https://github.com/siyuan-note/siyuan/issues/2829), [#10414](https://github.com/siyuan-note/siyuan/issues/10414), [#8873](https://github.com/siyuan-note/siyuan/issues/8873);
  - PDF annotation backlinks: 65👍 [#2828](https://github.com/siyuan-note/siyuan/issues/2828);
  - cloud inbox: 17👍 [#3718](https://github.com/siyuan-note/siyuan/issues/3718), a capture feature;
  - global attributes: 15👍, open [#13121](https://github.com/siyuan-note/siyuan/issues/13121).
- **Graph feedback** is mostly performance and usability:
  - "开启 `段落` 后关系图 getGraph 耗时太长" (graph slow with paragraphs enabled) [#13040](https://github.com/siyuan-note/siyuan/issues/13040);
  - depth support [#11513](https://github.com/siyuan-note/siyuan/issues/11513);
  - a nested-scan perf fix [#20248](https://github.com/siyuan-note/siyuan/issues/20248);
  - graph not resizing with the sidebar, fixed the same day [#20881](https://github.com/siyuan-note/siyuan/issues/20881).
  - Graph-titled issues are few and closed. The graph is not a top love/hate item; databases are.

### Quality from the tracker
- **Sample:** 300 issues in only **12 days** (2026-10-14 → 10-26). 295 closed, 5 open, 94 labelled Bug. Repo-wide, only 12 issues are open.
- **Speed:** median close 0.03 days. Median first human reply 0.52 h; median maintainer reply 0.71 h.
- **Concentration:** 88250 (D) made 416 of the maintainer comments. 106/300 issues were authored by 88250 or TCOTC and work as a changelog. Excluding them, the median close is still about 42 min (n=190). This is one-person-dominated (bus factor about 1) but extremely responsive.
- **Recurring themes:**
  - sync/WebDAV robustness, e.g. exit sync holding the workspace lock [#20599](https://github.com/siyuan-note/siyuan/issues/20599), [#20826](https://github.com/siyuan-note/siyuan/issues/20826); missed auto-sync on asset rename [#20863](https://github.com/siyuan-note/siyuan/issues/20863);
  - Android file traversal [#20874](https://github.com/siyuan-note/siyuan/issues/20874).
  - No share-intent bugs in the sample.
- **siyuan-android has issues disabled** (`has_issues: false`), so all reports go to the main repo.

### Code spot-check
- **Graph engine:** small modules (core, layoutCore, renderer, webglRenderer, canvasRenderer, labelRenderer, types). Typed-array data, transferables, renderer interface with fallback, and tests next to each file. 1,990 test files repo-wide.
- **Kernel:** Go, readable, with Chinese comments. One file name is misspelled (`virutalref.go`).

### License
- AGPL-3.0 for both the core and the Android shell ([LICENSE](https://github.com/siyuan-note/siyuan/blob/bfe36100477b7bbd66db1a8b8cff5547b2ccdb59/LICENSE)), with the same obligations as Logseq.
- The Aho-Corasick library it uses is MIT, so yearn can use that library directly and re-implement the virtual-ref idea.

---

## Trilium ([TriliumNext/Trilium](https://github.com/TriliumNext/Trilium)) + TriliumDroid ([FliegendeWurst/TriliumDroid](https://github.com/FliegendeWurst/TriliumDroid))

### Standout features
- **Relations are first-class typed attributes** (`~relName`). They can be inheritable, applied via `template`/`inherit`, and promoted to form fields ([Relations.md](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/docs/User%20Guide/User%20Guide/Advanced%20Usage/Attributes/Relations.md)). Internal links are stored as relations too, so one edge model serves every map.
- **Note Map: link map and tree map.**
  - **Server-side builders** live in [note_map.ts](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/routes/api/note_map.ts). The link map walks forward and back relations to depth 3 ([#L55](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/routes/api/note_map.ts#L55), [#L155](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/routes/api/note_map.ts#L155)) and takes include/exclude relation filters ([#L119](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/routes/api/note_map.ts#L119)). The tree map sizes nodes by descendant count ([#L208](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/routes/api/note_map.ts#L208)).
  - **Per-map configuration** uses labels `#mapIncludeRelation`, `#mapExcludeRelation` and `#mapRootNoteId` ([Note Map.md](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/docs/User%20Guide/User%20Guide/Note%20Types/Note%20Map.md)).
  - **Rendering** uses `force-graph` 1.52 (MIT, Canvas2D). It has custom node, link and arrow painting, hover-neighbourhood fading, zoom-gated labels and icons, alphaDecay 0.01 and warmupTicks 30 ([rendering.ts#L134](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/note_map/rendering.ts#L134), [#L451-L509](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/note_map/rendering.ts#L451)).
  - **There is a hard guard** `MAX_NOTES_THRESHOLD = 1_000` "which would freeze the browser" ([NoteMap.tsx#L27](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/note_map/NoteMap.tsx#L27), [#L111](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/note_map/NoteMap.tsx#L111)).
- **Relation Map note type.** A hand-placed canvas of note cards where you drag a handle to create a *named* relation. Positions and transform are stored in the note ([RelationMap.tsx](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/type_widgets/relation_map/RelationMap.tsx), [api.ts](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/type_widgets/relation_map/api.ts), [docs](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/docs/User%20Guide/User%20Guide/Note%20Types/Relation%20Map.md)).
- **Mind Map note type** on `mind-elixir` 5.15 (MIT) ([MindMap.tsx](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/src/widgets/type_widgets/mind_map/MindMap.tsx), [apps/client/package.json](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/client/package.json)). Its export is still buggy: HTML/MD exports come out as JSON ([#12060](https://github.com/TriliumNext/Trilium/issues/12060)), and SVG export has no text ([#11230](https://github.com/TriliumNext/Trilium/issues/11230)).
- **Similar Notes: relatedness without ML** ([similarity.ts](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/becca/similarity.ts)).
  - `buildRewardMap` gives each word of the base note a weight ([#L47](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/becca/similarity.ts#L47)). The weight grows as `(len−0.9)^0.7`. The weight factor is ×1 for the title, ×0.3 for ancestor titles, ×0.8 for own attributes and ×0.5 for inherited ones, halved for URL values (domains stripped), and 0.9→0.5 for headings h1–h6.
  - Every candidate is scored with a length penalty `1/len^0.3`, +0.2 for the same note type and **+1 for creation within ±1 h**. The ±1 h bonus excludes near-simultaneous bulk imports ([#L401-L410](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/becca/similarity.ts#L401)).
  - Already-related notes are skipped, archived notes are penalised, the threshold is 1.5, the top 200 are kept, and the loop yields to the event loop every 1,000 notes ([findSimilarNotes#L253](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/becca/similarity.ts#L253), [#L436](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/packages/trilium-core/src/becca/similarity.ts#L436)).
- **Official mobile app.** It is a Capacitor wrapper of the `standalone` PWA, so the core runs on-device ([apps/mobile/README.md](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/mobile/README.md)). Its manifest has **no SEND intent filter** ([AndroidManifest.xml](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/apps/mobile/android/app/src/main/AndroidManifest.xml)). The README still sends Android users to TriliumDroid ([README#L114-L128](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/README.md#L114)).
- **TriliumDroid** is a native Kotlin client that syncs the Trilium SQLite DB.
  - It has its **own native graph view**: a hand-rolled d3-style force sim on an Android `Canvas` `View`. The charge pass is O(n²) and commented "crappy charge simulation"; the run is capped at 200 iterations ([GraphView.kt#L151-L270](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/app/src/main/kotlin/eu/fliegendewurst/triliumdroid/view/GraphView.kt#L151), [#L237](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/app/src/main/kotlin/eu/fliegendewurst/triliumdroid/view/GraphView.kt#L237)).
  - The graph shows the one-hop relations of a note ([NoteMapFragment.kt#L53-L63](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/app/src/main/kotlin/eu/fliegendewurst/triliumdroid/fragment/NoteMapFragment.kt#L53)).
  - Relation and mind maps are WebView templates ([mindMap_TPL.html](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/app/src/main/assets/mindMap_TPL.html)).
  - The share target accepts only `text/plain` and `text/html` ([AndroidManifest.xml#L52-L56](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/app/src/main/AndroidManifest.xml#L52)). Image sharing is requested in [#145](https://github.com/FliegendeWurst/TriliumDroid/issues/145).

### What users love and want
- **Most-upvoted** (excluding the 121👍 maintenance-mode announcement [#4620](https://github.com/TriliumNext/Trilium/issues/4620)):
  - official mobile app: 54👍 [#7447](https://github.com/TriliumNext/Trilium/issues/7447);
  - multi-user: 39👍, open [#4956](https://github.com/TriliumNext/Trilium/issues/4956);
  - mind-map note type: 12👍, delivered [#2150](https://github.com/TriliumNext/Trilium/issues/2150).
- **Map requests:**
  - better default link-map layout ("random layout … every time … very messy") [#2064](https://github.com/TriliumNext/Trilium/issues/2064);
  - enhance link map [#1142](https://github.com/TriliumNext/Trilium/issues/1142);
  - upgrade relation map [#3037](https://github.com/TriliumNext/Trilium/issues/3037);
  - transclusion as a map edge [#3840](https://github.com/TriliumNext/Trilium/issues/3840);
  - the map of Hidden Notes froze the app unrecoverably [#8916](https://github.com/TriliumNext/Trilium/issues/8916) (fixed).
- **TriliumDroid:** most upvoted is MFA (7👍 [#97](https://github.com/FliegendeWurst/TriliumDroid/issues/97)). Its share target is still open [#14](https://github.com/FliegendeWurst/TriliumDroid/issues/14), even though basic sharing exists.

### Quality from the tracker
- **Trilium sample:** 300 issues, 2026-07-19 → 10-26 (about 100 per month). 90 open, 210 closed.
  - Median close 1.7 days (p75 9.8 days). Median first human reply 5.6 h.
  - eliandoran gave 89 first replies, followed by dosubot (73, an AI triage bot).
  - Labels are rich: Triage, UI, Difficulty, BE, llm, mobile.
- **Trilium themes:**
  - mobile/tablet polish: [#12216](https://github.com/TriliumNext/Trilium/issues/12216), [#11106](https://github.com/TriliumNext/Trilium/issues/11106);
  - sync data loss: "Note modifications lost when syncing" [#12173](https://github.com/TriliumNext/Trilium/issues/12173), overwritten journal entries [#10819](https://github.com/TriliumNext/Trilium/issues/10819);
  - Electron/V8 crashes: [#11435](https://github.com/TriliumNext/Trilium/issues/11435), [#11726](https://github.com/TriliumNext/Trilium/issues/11726);
  - new LLM features crashing renderers [#12021](https://github.com/TriliumNext/Trilium/issues/12021).
- **TriliumDroid:** 125 issues, 48 open. It is solo (FliegendeWurst wrote 143 maintainer comments, median reply 6.4 h).
  - The dominant theme is **sync** (26 titles). Every Trilium release needs a sync-protocol bump: [#92](https://github.com/FliegendeWurst/TriliumDroid/issues/92), [#108](https://github.com/FliegendeWurst/TriliumDroid/issues/108), [#148](https://github.com/FliegendeWurst/TriliumDroid/issues/148), [#158](https://github.com/FliegendeWurst/TriliumDroid/issues/158).
  - Other sync issues: OOM on large DBs [#3](https://github.com/FliegendeWurst/TriliumDroid/issues/3), and "Sync overwirte notes" [#106](https://github.com/FliegendeWurst/TriliumDroid/issues/106).

### Code spot-check
- **Trilium:** TypeScript/Preact. Every map widget has a `.spec.ts(x)` sibling (1,487 test files). The similarity code carries `v8 ignore` markers for coverage. There are 111 agent/skill files, so it is heavily agent-assisted.
- **TriliumDroid:** compact (293 files) but only 4 test files. Its graph is a toy.

### License
- Trilium and TriliumDroid are both AGPL-3.0 ([Trilium LICENSE](https://github.com/TriliumNext/Trilium/blob/b4aefdd6b29cba5b0cde3f352f1ca887033ffbdd/LICENSE)).
- TriliumDroid also bundles GPL-2.0+ CKEditor and MIT Excalidraw ([README#L94-L97](https://github.com/FliegendeWurst/TriliumDroid/blob/11c2af1abf3a98753c217607d8e401ee8b002a1d/README.md#L94)).
- force-graph and mind-elixir are MIT, so yearn can use them directly.

---

## Anytype ([anytype-kotlin](https://github.com/anyproto/anytype-kotlin) + [anytype-heart](https://github.com/anyproto/anytype-heart); renderer in [anytype-ts](https://github.com/anyproto/anytype-ts))

### Standout features
- **The graph is a query.**
  - `ObjectGraph` takes `Filters`, `ObjectTypeFilter`, `CollectionId`, `SetSource` and `Limit`, runs a subscription search, and turns results into nodes ([graph.go#L92](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/core/block/object/objectgraph/graph.go#L92)).
  - Edges come from **every relation whose format is `object` or `file`** (named, typed edges) plus in-text links. A skip-list drops noise: creator, backlinks, coverId and similar ([#L38](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/core/block/object/objectgraph/graph.go#L38), [#L135](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/core/block/object/objectgraph/graph.go#L135)).
  - So the same graph renders for the whole space, one set, or one collection. anytype-ts has a dataview graph view and a widget graph ([graph README](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/docs/src/ts/component/graph/README.md)).
- **The backlinks index is maintained asynchronously** by a watcher that batches link updates into `backlinks` relations ([watcher.go](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/core/block/backlinks/watcher.go)).
- **Desktop renderer** ([dist/workers/graph.js](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js)). It has 100+ commits since 2023, 89 of them by ra3orblade.
  - It runs on an **OffscreenCanvas in a worker**, with PixiJS 8 and d3-force ([#L7-L15](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L7), [#L256](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L256)).
  - It has **cluster-by-type** via d3-force-cluster (BSD-3) ([#L533](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L533)).
  - A short "createdIn" link distance pulls objects toward where they were created ([#L57](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L57)).
  - **Timeline playback** reveals nodes in `createdDate` order over 15 s at 1/2/4×, with a scrubber ([#L26](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L26), [#L596](https://github.com/anyproto/anytype-ts/blob/3a3ecd110f6730e42515e9badfd30fb04ab821f0/dist/workers/graph.js#L596)).
  - Visible labels are capped at 100.
- **Android has no graph view.** No graph component exists in anytype-kotlin (a tree search for "graph" finds only the nav graph and an icon).
  - Its share flow is the strongest of the four. It has three flows: chat space, data space, and hybrid. It can create a **Bookmark object** for a URL ([SharingViewModel.kt#L68-L84](https://github.com/anyproto/anytype-kotlin/blob/ca3fd139c315131f42b5dee31cfce42dad696dd5/presentation/src/main/java/com/anytypeio/anytype/presentation/sharing/SharingViewModel.kt#L68)).
  - Heart then fetches OpenGraph metadata. That fetch is capped at a 10 MB read and **honours `X-Robots-Tag` and CSP** ([linkpreview.go#L35](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/util/linkpreview/linkpreview.go#L35), [#L297](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/util/linkpreview/linkpreview.go#L297)).

### What users love and want
- **anytype-kotlin:**
  - F-Droid: 34👍 [#386](https://github.com/anyproto/anytype-kotlin/issues/386); blocked by the license **[unverified as the stated reason]**;
  - home-screen widget: 17👍 [#285](https://github.com/anyproto/anytype-kotlin/issues/285);
  - tablet UI: 10👍 [#2281](https://github.com/anyproto/anytype-kotlin/issues/2281);
  - "Share to Anytype": 9👍 [#248](https://github.com/anyproto/anytype-kotlin/issues/248);
  - "Enhance share" [#2711](https://github.com/anyproto/anytype-kotlin/issues/2711): sharing "only allows a URL … to be captured as either a brand-new Bookmark or Note".
- **anytype-heart:** OR filters in sets: 26👍 [#860](https://github.com/anyproto/anytype-heart/issues/860); edit relation type: 16👍 [#1281](https://github.com/anyproto/anytype-heart/issues/1281); vector search requested [#2365](https://github.com/anyproto/anytype-heart/issues/2365).
- **Graph (anytype-ts):** apply filters to the graph [#777](https://github.com/anyproto/anytype-ts/issues/777); an "Obsidian-style" local graph sidebar [#2203](https://github.com/anyproto/anytype-ts/issues/2203); pin dragged nodes [#2142](https://github.com/anyproto/anytype-ts/issues/2142); graph "full of Untitled nodes" on self-hosted networks [#2537](https://github.com/anyproto/anytype-heart/issues/2537).

### Quality from the tracker
- **anytype-kotlin:** only 98 issues ever, 81 open. Maintainers replied on 5/98 (median about 27 days); 38 open issues have zero comments. Login failures are still open from 2024: [#787](https://github.com/anyproto/anytype-kotlin/issues/787), [#836](https://github.com/anyproto/anytype-kotlin/issues/836).
- **anytype-heart:** 203 issues over three years, 150 closed. The median close is **569 days**. Most closes are sashagood's templated housekeeping, posted dozens of times each: "logged in our internal tracker. Closing for housekeeping" (24×) and "reported against a much older version …" (23×). Development happens on the forum and internal tracker (repo `has_discussions: false`).
- **Themes:**
  - sync (19 titles), e.g. "Sync never completes" [#1735](https://github.com/anyproto/anytype-heart/issues/1735), local-only pairing never syncs [#3254](https://github.com/anyproto/anytype-heart/issues/3254);
  - data loss: "Workspace lost after update – data deleted" [#2458](https://github.com/anyproto/anytype-heart/issues/2458), "Notes content unexpectedly lost" [#2891](https://github.com/anyproto/anytype-heart/issues/2891), "Risk of data loss, sync failure causing data conflicts" [#3082](https://github.com/anyproto/anytype-kotlin/issues/3082);
  - offline attach failure [#3322](https://github.com/anyproto/anytype-kotlin/issues/3322).

### Code spot-check
- **heart:** Go with components wired through `any-sync/app`; 840 test files. `graph.go` opens with an "AI generated" header block (86 Go files in heart carry it).
- **Kotlin app:** large (8k files), clean domain/presentation layering, 440 test files.
- **Desktop graph worker:** a single 2,242-line plain-JS file kept in `dist/`. Functional but monolithic.

### License: Any Source Available License 1.0
From [LICENSE.md](https://github.com/anyproto/anytype-heart/blob/92f654aa8eefd1abcca4166025c4d782275fb853/LICENSE.md):
- **Grant:** use, modify and redistribute "only (a) for Non-Commercial Use, or (b) for Commercial Use in Allowed Networks". Allowed Networks are networks that Any Association authorises and can remove "upon reasonable notice".
- **Copies:** they must stay under ASAL. **Your changes** can be licensed as you like, but "cannot change the license for the Software". No sublicensing is allowed. There is a patent-retaliation termination clause, and termination is immediate on any violation.
- **What this means for yearn:** personal or R&D use and study are fine. Any copied heart or kotlin code would keep yearn's copy ASAL-bound and non-commercial. It is not OSI-approved, cannot be combined with AGPL code, and F-Droid inclusion is doubtful. Treat it as **read-for-ideas only**. anytype-ts reports `NOASSERTION` on GitHub too **[license text not re-read]**.

---

## Closing: what yearn should take

**Logseq**
- Not a wholesale grab: ClojureScript and Datascript, AGPL, a beta DB, and no shipping Android DB app.
- Take its **graph spec**: the two global modes (topic/tag hubs vs everything), a deterministic ring layout for one item's neighbourhood, a precomputed (not live) layout, and **hard LOD budgets** (≈900–1,400 label candidates, ≈170–240 visible labels, edge caps of 3.6k–28k, and a "fast" layout above 2.5k–10k nodes).
- Implement it with MIT pixi.js and ISC d3-force in a WebView, or port it to Compose.

**SiYuan**
- Not a wholesale grab: AGPL, a Go kernel, and a doc-centric model.
- Take three ideas:
  - **(1) Virtual refs.** Aho-Corasick over known entity names (topics, authors, handles, repo names) across every capture's caption, transcript and OCR. Show the hits as unstored edges with include/exclude lists. This is the cheapest auto-linking with no ML.
  - **(2) The engine shape.** Typed-array Barnes-Hut layout in a worker, a WebGL renderer with Canvas fallback, a server-side node cap (16k) and a min-refs prune.
  - **(3) List→mind-map as a *view* over hierarchical data.** It fits auto-organized topic trees without a separate mind-map store.

**Trilium**
- Not a wholesale grab: AGPL, and a single-user tree DB.
- Take **Similar Notes** almost verbatim *as an algorithm*. Weighted word rewards from title, ancestors, attributes and headings, URL de-weighting, and +1 for creation within ±1 h make a strong "related captures" signal for yearn, because captures shared together are related. Use it until embeddings arrive.
- Also take: one edge model where links are relations, per-map relation include/exclude filters, and the 1k-node guard. force-graph and mind-elixir (both MIT) are usable as-is for the home-server UI.
- TriliumDroid's Kotlin Canvas graph proves a native Android graph is easy, but its O(n²) sim does not scale. Replace it with Barnes-Hut à la SiYuan.

**Anytype**
- Not grabbable: the ASAL license is non-commercial and incompatible with AGPL.
- Take the **data model**: typed objects with named, typed relations; the graph as the result of any saved filter; edges typed Link vs Relation; a skip-list for noisy relations.
- Take the **timeline playback** by creation date, which suits a capture stream.
- Take **cluster-by-type**: in yearn, by source platform or topic.
- Its Android share flow (choose destination, link to an existing object, OpenGraph bookmark fetch that honours X-Robots/CSP) is a good UX reference. Users still complain that it cannot add a URL to an existing object ([#2711](https://github.com/anyproto/anytype-kotlin/issues/2711)), which is a gap yearn can fill.
