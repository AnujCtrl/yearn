# Eval: AI search / auto-linking / memory graph (Khoj, Supermemory, Reor)

Research date: 2026-10-26. This report builds on [2026-10-26-share-capture-prior-art.md](2026-10-26-share-capture-prior-art.md) and does not repeat its survey. Code was read from shallow clones pinned at these commits:

- Khoj [`ae229ca`](https://github.com/khoj-ai/khoj/tree/ae229ca894c0b80ad84664afcfdde523b5e87057) (2026-08-02)
- Supermemory [`f355dc8`](https://github.com/supermemoryai/supermemory/tree/f355dc80396ff8e5fccc36b774ddd22b0e19cd05) (2026-10-24)
- Reor [`9b47fca`](https://github.com/reorproject/reor/tree/9b47fcaf1158cedda1f0160392de25588efe4c31) (2025-05-13, archived)

Issue statistics cover the 300 most recent issues per repo (218 for Reor), excluding PRs, pulled with `gh issue list` on 2026-10-26. "Maintainer reply" means a comment with `authorAssociation` of MEMBER, OWNER or COLLABORATOR. Note: the `sort:reactions-+1` inside `q=` form in the brief is silently ignored by the GitHub search API. Upvote rankings below use `-f sort=reactions -f order=desc` instead.

| Project | Best at | Quality | License | Grab verdict |
|---|---|---|---|---|
| **Khoj** (37.8k★) | Chat over your own docs. An LLM rewrites the question into several sub-queries with date filters, pgvector retrieves, a cross-encoder reranks, and an agentic "research" loop chains semantic search, regex grep, file reads and web search. | **B−**: clean Django structure, tests, evals CI, and published security advisories. But it is now effectively one maintainer, 2.0 has been in beta for 15 months, `:latest` is frozen at 1.42.10 (2025-07), and fixes are not backported. | AGPL-3.0 | **Components and ideas.** Don't use it as yearn's brain: no clustering or graph, and image indexing is OCR text only. Copy the query-planner, rerank and date-index patterns. |
| **Supermemory** (31.4k★) | A memory *model*: atomic facts with `updates`/`extends`/`derives` edges, temporal "latest" truth, decay and forgetting. Also a polished MIT React canvas graph viewer. | **C**: the open repo is active, well-tested and quick to triage. But the engine is a closed binary, and that binary has open data-loss, OOM and silent non-indexing bugs. | MIT (SDKs, UI). Server binary is proprietary under a "lite license limit". | **One component (the graph viewer) plus ideas.** Not a brain: the server is closed source, license-capped and unstable, and self-hosted media understanding needs a Gemini key. |
| **Reor** (8.5k★, archived) | Zero-effort "related notes": embed the open note, run kNN, exclude itself. All local (LanceDB + Transformers.js + Ollama). | **D**: archived. Two test files, a "hybrid" search that is really vector-only, a reranker that is never called, and 113 of 218 issues open, many of them install crashes. | AGPL-3.0 | **Ideas only.** The related-notes pattern and the mistakes to avoid. |

---

## 1. Khoj (`khoj-ai/khoj`)

Stack: Django, Postgres with pgvector, sentence-transformers, and a Next.js web UI. The [docker-compose](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/docker-compose.yml) runs 5 services: pgvector/pg15, a terrarium code sandbox, SearxNG, `khoj-computer` and the server.

### Standout features (in code)

- **LLM query planner with date filters.** This is Khoj's best idea. Before retrieval, [`extract_questions`](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/routers/helpers.py#L1398) asks a fast model for up to 5 search queries. The prompt is few-shot trained to emit inline date filters, for example `"Fun social events dt>='{current_month}-01' dt<'{current_date}'"` ([prompts.py L469–548](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/conversation/prompts.py#L469-L548)). A vague question becomes several concrete, time-bounded searches. That maps directly onto yearn's "search by vibe, roughly when, roughly who".
- **Retrieval pipeline.**
  - Embedding is bi-encoder only, defaulting to `thenlper/gte-small` ([embeddings.py L29](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/embeddings.py#L29)). An OpenAI-compatible embedding endpoint can replace it.
  - Search is pgvector `CosineDistance` with a confidence cutoff (`bi_encoder_confidence_threshold=0.18`, [models L582](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/database/models/__init__.py#L582); [search_with_embeddings](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/database/adapters/__init__.py#L2150-L2178)).
  - Results are reranked by the `mixedbread-ai/mxbai-rerank-xsmall-v1` cross-encoder ([embeddings.py L117](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/embeddings.py#L117); [text_search.py L192–257](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/search_type/text_search.py#L192-L257)). The chat path always reranks (`r=True`, [helpers.py L1369–1374](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/routers/helpers.py#L1369-L1374)).
- **"Hybrid" is filter syntax, not rank fusion.** There is no BM25 or full-text search. Keyword matching is a hard pre-filter written as `+"word"` / `-"word"`, implemented as SQL `icontains` ([word_filter.py](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/search_filter/word_filter.py); [apply_filters L2090–2145](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/database/adapters/__init__.py#L2090-L2145)). File globs and `dt>=` ranges work the same way.
- **Per-entry date index.** At index time, every date mentioned *inside* a chunk is parsed with `dateparser` and stored in an `EntryDates` table. A `dt` filter then finds content *about* a date, not just content created on it ([text_to_entries.py L238–247](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/content/text_to_entries.py#L238-L247)).
- **Incremental indexing.** Chunks are MD5-hashed, and only new hashes are embedded. Chunking is paragraph-aware with a 256-token cap, and each chunk keeps its heading prefix and line-number URI ([text_to_entries.py L36–180](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/content/text_to_entries.py#L36-L180)).
- **Research mode (agentic chat over data).** [`apick_next_tool` / `research`](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/routers/research.py#L277-L476) loops over these tools: `SemanticSearchFiles`, `RegexSearchFiles` ([grep_files](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/routers/helpers.py#L3267)), `ViewFile`, `ListFiles`, `SearchWeb`, `ReadWebpage`, `PythonCoder` and `Operator`. A [`run_evals.yml`](https://github.com/khoj-ai/khoj/tree/ae229ca894c0b80ad84664afcfdde523b5e87057/.github/workflows) CI job backs the README's benchmark claims.
- **Memory.** This is a flat list, not a graph. `UserMemory` rows hold embedded facts ([models L855](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/database/models/__init__.py#L855)). After each chat turn, [`ai_update_memories`](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/routers/helpers.py#L1038) asks the LLM which facts to create or delete. There are no edges and no versioning.
- **Multimodal is weak.**
  - Images are indexed as RapidOCR text only, with no visual embedding. The OCR step also writes temp files into the current working directory ([image_to_entries.py L60–90](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/processor/content/images/image_to_entries.py#L60-L90)).
  - Accepted index types are md, org, pdf, docx, plaintext, image, GitHub and Notion. There is no audio or video indexing.
  - Vision only applies to *chat input* (`vision_enabled`, [models L233](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/database/models/__init__.py#L233)).
  - There is no clustering, topic grouping or link graph anywhere in the codebase.

### What users love and want (top by reactions)

- Logseq plugin, 24 👍, open since 2023 ([#141](https://github.com/khoj-ai/khoj/issues/141)).
- MCP support ([#1023](https://github.com/khoj-ai/khoj/issues/1023)) and custom OAuth2 multi-user auth ([#1184](https://github.com/khoj-ai/khoj/issues/1184)), 7 each.
- Password auth ([#1092](https://github.com/khoj-ai/khoj/issues/1092)) and on-prem SMTP ([#1093](https://github.com/khoj-ai/khoj/issues/1093)), 4–5 each.
- Earlier asks that shipped: custom OpenAI URL ([#407](https://github.com/khoj-ai/khoj/issues/407)), offline models ([#201](https://github.com/khoj-ai/khoj/issues/201)), vision ([#889](https://github.com/khoj-ai/khoj/pull/889)).
- The pattern: users want Khoj to plug into their existing notes tool, with self-host auth that works.

### Quality from the tracker

**Volume and response time**
- 300 issues (2024-01 to 2026-10): 96 open, 204 closed (180 completed, 24 not planned).
- Median time to close is 9.0 days (p25 1.1d, p75 46.6d). For the `fix` label: 158 issues, 36 open, median close 6.6 days.
- **Decay.** Maintainers replied to 116 of 150 issues filed in 2024, but only 16 of 63 filed in the last 12 months, and 42 of those 63 are still open.

**Maintainers and releases**
- Of the last 100 commits, 80 are by `debanjum` and 2 by `sabaimran`. Commits by month: 25 in 2026-03, 10 in 2026-06, 2 in 2026-08.
- The README now promotes their new project, Pipali.
- The last stable release is 1.42.10 (2025-07-15). Since then, only `2.0.0-beta.1…28` (the last on 2026-03-26).
- Docker `:latest` users hit bugs that are fixed on main but never backported: [#1407](https://github.com/khoj-ai/khoj/issues/1407) (crash on OpenAI-compatible backends) and [#1377](https://github.com/khoj-ai/khoj/issues/1377) (GPT-5.6 temperature).

**Recurring themes**
- Self-host auth and admin pain: [#1187](https://github.com/khoj-ai/khoj/issues/1187) admin login and CSRF, open 17 months.
- Indexing hangs and OOM on large corpora: [#1105](https://github.com/khoj-ai/khoj/issues/1105) ("killed by OOM then was restarting"), [#1437](https://github.com/khoj-ai/khoj/issues/1437) (markdown indexing never finishes), [#573](https://github.com/khoj-ai/khoj/issues/573) (1000-file limit).
- Model churn breaking providers: #1377, #1407.

**Security**
- 6 published GHSAs, including a 2026-06 unauthenticated path traversal and CVE-2025-69207 (a Notion IDOR leading to index poisoning) ([advisories](https://github.com/khoj-ai/khoj/security/advisories)). Disclosure is handled properly.

**Open source vs cloud**
- Open source is first-class. Billing only switches on if Stripe environment variables are set ([state.py L37–41](https://github.com/khoj-ai/khoj/blob/ae229ca894c0b80ad84664afcfdde523b5e87057/src/khoj/utils/state.py#L37-L41)), so self-host gets every feature.

### Code quality spot-check

- **Strengths:**
  - Readable layering: content processors, then `TextToEntries`, then adapters, then routers.
  - 24 `tests/test_*.py` modules, including the date and word filters, text search and per-format parsers.
  - An evals pipeline and pre-commit.
- **Weaknesses:**
  - God-files: `routers/helpers.py` is 3,458 lines and `database/adapters/__init__.py` is 2,380.
  - Prompts are a 1,371-line module.
  - OCR writes temp files into the current working directory (above).
  - Total: about 34.7k lines of Python.

### License

AGPL-3.0. Running Khoj unmodified on a home server for yourself carries no obligations. Modifying it and exposing it over a network obliges you to publish the modified source to its users (AGPL §13). Copying its code into yearn makes yearn's server AGPL. Re-implementing the *ideas* is free.

---

## 2. Supermemory (`supermemoryai/supermemory`)

### What is actually open

- **The engine is not open.** The repo holds SDKs (TS and Python), an MCP server, AI-SDK tools, docs and a graph-viz component. The docs say plainly that "the downloadable self-hosted server binary is built from a separate, non-public codebase" and that it is "free within its lite license limit" ([self-hosting/overview.mdx L20, L65](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/self-hosting/overview.mdx#L20); [local-vs-enterprise.mdx L8](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/self-hosting/local-vs-enterprise.mdx#L8)). The limit itself is not specified anywhere in the repo docs.
- **The consumer app is gone.** The formerly open web app (`apps/web`, 179 lines of code) is now only a redirect to the hosted console ([apps/web/README.md](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/web/README.md)).
- **What the binary is made of.** Issue reports show it is Bun 1.3.4 with embedded PGlite and pgvector, a fixed `vector(768)` column, and a corpus held in RAM ([#1704](https://github.com/supermemoryai/supermemory/issues/1704), [#1104](https://github.com/supermemoryai/supermemory/issues/1104), [#1366](https://github.com/supermemoryai/supermemory/issues/1366), [#1826](https://github.com/supermemoryai/supermemory/issues/1826)).

### Standout features

- **Memory model.** This is the real contribution, documented in [concepts/graph-memory.mdx](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/graph-memory.mdx):
  - **Documents and memories are separate.** Raw input is kept for RAG chunks. LLM-extracted atomic facts become graph nodes.
  - **Facts are linked by three relation types** ([L67–116](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/graph-memory.mdx#L67-L116)):
    - `updates`: supersedes an earlier fact, with `isLatest` keeping the history.
    - `extends`: enriches a fact.
    - `derives`: an inferred fact.
  - **"Dreaming"** ([L118](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/graph-memory.mdx#L118)) is batch consolidation that groups related documents before extraction.
  - **Typed decay** ([L136](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/graph-memory.mdx#L136)): facts persist, preferences strengthen, episodes decay, and time-bound facts expire.
- **"Hybrid search" means memories plus chunks, not keyword plus vector.** `searchMode: "hybrid"` returns fact hits and document-chunk hits together, with optional `rerank` ([recall/search.mdx L11, L106–109](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/recall/search.mdx#L11)). The engine is described as having "Vector, FTS, and graph built in" ([how-it-works.mdx L15](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/how-it-works.mdx#L15)), but this cannot be verified because the source is closed.
- **Multimodal on paper.** Docs list images, PDFs and audio/video with "transcription, speaker detection, topic segmentation" ([content-types.mdx](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/concepts/content-types.mdx)). On self-host, image, video and PDF understanding requires a Gemini key, URL ingestion requires Firecrawl ([providers.mdx L48](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/apps/docs/self-hosting/providers.mdx#L48); overview), and connectors and MCP are platform-only.
- **`@supermemory/memory-graph` (MIT): the best grabbable artifact.** It is a React + Canvas 2D graph viewer, about 3.2k lines in `canvas/`.
  - Layout uses d3-force with per-edge-type link strengths, and `extends` edges are excluded from layout forces ([simulation.ts](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/packages/memory-graph/src/canvas/simulation.ts)). Above 6,000 nodes it switches to a static layout.
  - Hit-testing uses a spatial index ([hit-test.ts](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/packages/memory-graph/src/canvas/hit-test.ts)). It also has touch input, paginated "load more", and version-chain rendering.
  - Clusters are BFS connected components over doc→memory and relation edges, coloured by a hash of the cluster key ([use-graph-data.ts L115–192](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/packages/memory-graph/src/hooks/use-graph-data.ts#L115-L192)). Edges come only from backend relations ([computeEdges L407](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/packages/memory-graph/src/hooks/use-graph-data.ts#L407-L456)).
  - The README's claim of "edges show document similarity" ([README L53](https://github.com/supermemoryai/supermemory/blob/f355dc80396ff8e5fccc36b774ddd22b0e19cd05/packages/memory-graph/README.md#L53)) is not implemented. No similarity edges are computed.
  - There are 12 test files.

### What users love and want

The top items by reactions are almost all about openness:

- "why there is no self hosted option" ([#463](https://github.com/supermemoryai/supermemory/issues/463), 10 👍). The maintainer answered "self hosting via enterprise".
- "Would you consider true open source license?" ([#349](https://github.com/supermemoryai/supermemory/issues/349)). The maintainer answered "you can always use our API".
- Local LLM support ([#256](https://github.com/supermemoryai/supermemory/issues/256)).
- "Where is the source for supermemory-server?" ([#1299](https://github.com/supermemoryai/supermemory/issues/1299), open). A commenter wrote: "its closed source with a distributable compiled binary. Trying to say its open source just creates distrust".
- Configurable embedding model, because hard-coded English `bge-base-en-v1.5` broke German and Dutch recall ([#1104](https://github.com/supermemoryai/supermemory/issues/1104), fixed in server v0.0.5).
- X bookmarks import returning 0 tweets in the old consumer app ([#319](https://github.com/supermemoryai/supermemory/issues/319)).

### Quality from the tracker

**Volume and response time**
- 300 issues: 20 open and 280 closed, but **85 of the closures are "not planned"**, including a batch of 12 on 2026-09-29.
- Median time to close is 19.6 days.
- Maintainers commented on 90 of 204 issues filed in the last 12 months. Every issue is auto-linked to Linear.

**Maintainers**
- The last 100 commits are by Dhravya (42), MaheshtheDev (25) and sohamd22 (18), so the team is active.
- Issue volume spiked to 52 in July and 44 in August 2026, after the self-host binary shipped.

**Open self-host bugs of the data-loss class**
- A failed ingestion permanently blanks the document, and every document over roughly 10k characters fails ([#1827](https://github.com/supermemoryai/supermemory/issues/1827)).
- Pure-CJK memories are never indexed, with no error ([#1740](https://github.com/supermemoryai/supermemory/issues/1740)).
- Unbounded RAM growth leads to OOM every 4–22 hours, and the DB can get stuck after a restart ([#1826](https://github.com/supermemoryai/supermemory/issues/1826)).
- Search returns 0 results when the optional `containerTag` is omitted ([#1704](https://github.com/supermemoryai/supermemory/issues/1704)).

**Self-host performance asks closed as not planned**
- [#1366](https://github.com/supermemoryai/supermemory/issues/1366): PGlite memFS holds the whole corpus in RAM.
- [#1413](https://github.com/supermemoryai/supermemory/issues/1413): the full DB is re-serialized every 10 seconds.

**Benchmarks**
- Benchmark claims cannot be reproduced locally, and the benchmark code is not open ([#1787](https://github.com/supermemoryai/supermemory/issues/1787)).

**Verdict:** open source is a funnel to the cloud. The docs themselves say "the hosted platform is where the full product lives."

### Code quality spot-check

- The open code (TS, Bun, Turbo, Biome) is tidy and tested, with 58 test files across `packages/tools`, `apps/mcp`, `memory-graph` and the Python SDKs.
- `claude-auto-fix-ci.yml` and `claude.yml` workflows show AI-driven maintenance.
- The quality that matters for a self-host brain cannot be assessed, because that code is closed.

### License

- The repo is MIT: SDKs, MCP, tools and memory-graph can be copied freely with attribution.
- The server binary is proprietary, "free within its lite license limit". You cannot fork it, build it, or fix bugs like #1827 yourself.

---

## 3. Reor (`reorproject/reor`), archived

Stack: an Electron desktop app, LanceDB (embedded), Transformers.js embeddings, Ollama or an OpenAI-compatible LLM, and a BlockNote editor.

### Standout features

- **Auto-linking that needs no user action.** When a note opens, the "related notes" sidebar takes its first 500 characters (stripped of markdown), runs a vector search for the top 20, and excludes the note itself ([SimilarFilesSidebar.tsx L32–56](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/src/components/Sidebars/SimilarFilesSidebar.tsx#L32-L56)). That is the whole "auto-link" feature: no stored edges and no graph. It is what users loved, as "a RAG app with two generators: the LLM and the human" ([README](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/README.md)).
- **Fully local stack.**
  - LanceDB table with cosine metric and prefiltered `where` ([lanceTableWrapper.ts L104–114](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/lanceTableWrapper.ts#L104-L114)).
  - A user-selectable embedding model, including multilingual-e5-large, UAE-Large and jina zh/de ([embeddings.ts L19–45](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/embeddings.ts#L19-L45)).
  - Chunking by markdown heading first, then recursive splitting ([chunking.ts](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/common/chunking.ts)).
  - Re-indexing by `filemodified` comparison ([tableHelperFunctions.ts L123](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/tableHelperFunctions.ts#L123)).
- **The "hybrid search" is vector-only.** `keywordSearch` first *calls the vector search* and then counts regex keyword hits inside those same results ([db.ts L59–92](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/src/lib/db.ts#L59-L92)). `combineAndRankResults` mixes the two scores 0.7/0.3 ([L94–161](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/src/lib/db.ts#L94-L161)). As a result, a note that matches the keyword but is not semantically close can never be found.
- **The reranker is never used.** A `bge-reranker-base` reranker exists ([embeddings.ts L177](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/embeddings.ts#L177)) behind a `search-with-reranking` IPC handler ([ipcHandlers.ts L66](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/ipcHandlers.ts#L66-L77)). The handler is never exposed in the [preload bridge](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/preload/index.ts#L26), so it is dead code.
- **No multimodal.** `ImageStore` and `VideoStore` only deduplicate media files embedded in notes. Nothing gets indexed.

### What users love and want

- Use an Obsidian vault without Reor altering it, 13 👍 ([#246](https://github.com/reorproject/reor/issues/246)). Reor rewrote markdown and stripped image tags ([#217](https://github.com/reorproject/reor/issues/217)).
- "is this project dead?" ([#531](https://github.com/reorproject/reor/issues/531)).
- Delete chats ([#245](https://github.com/reorproject/reor/issues/245)), container build ([#46](https://github.com/reorproject/reor/issues/46)), flashcards ([#96](https://github.com/reorproject/reor/issues/96)), chat history ([#120](https://github.com/reorproject/reor/issues/120)).

### Quality from the tracker, and why it died

**Volume and response time**
- 218 issues: 113 open and 105 closed. Median time to close was 10.9 days while the project was alive.
- 40 issue titles are install or crash reports, 25 of them still open: macOS JS dependency [#36](https://github.com/reorproject/reor/issues/36), a Windows Defender Trojan flag [#58](https://github.com/reorproject/reor/issues/58), AppImage [#501](https://github.com/reorproject/reor/issues/501).
- Indexing failures, e.g. `index-files-in-directory` errors ([#464](https://github.com/reorproject/reor/issues/464), [#500](https://github.com/reorproject/reor/issues/500)).
- Bounties (12 posted, 10 rewarded) stood in for maintainers.

**Why it died**
1. The founder (`samlhuillier`) made his last commit on 2024-11-23. One contributor (`milaiwi`, 81 of the last 100 commits) carried it until 2025-05-13. The founder's GitHub profile now lists company `ai.justice.gov.uk` (an inference that he moved on).
2. A chat-breaking bug ([#473](https://github.com/reorproject/reor/issues/473), "Last Message is not an assistant message") stayed unfixed for over a year.
3. The domain reorproject.org lapsed and was reported hijacked ([#533](https://github.com/reorproject/reor/issues/533)).
4. Structurally, the problem was a desktop Electron app bundling native LLM, vector-DB and embedding stacks across three OSes, with a two-person team. Most of the tracker is packaging pain, not product pain.

### Code quality spot-check

- Two test files; `database.test.ts` is 17 lines.
- Filter strings are built from raw file paths in one place (`NOTE_PATH != '${fileToBeExcluded}'`, [SimilarFilesSidebar.tsx L41](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/src/components/Sidebars/SimilarFilesSidebar.tsx#L41)) but sanitized in another ([lanceTableWrapper.ts L87](https://github.com/reorproject/reor/blob/9b47fcaf1158cedda1f0160392de25588efe4c31/electron/main/vector-database/lanceTableWrapper.ts#L87)).
- The "hybrid" search and the reranker are mislabeled or dead, as shown above.
- The vector-DB module is small and readable, about 890 lines.

### License

AGPL-3.0, archived. A fork is allowed but must stay AGPL. There is nothing worth forking for a server.

---

## Closing: what yearn should take

**Khoj: components and ideas, not the brain.**
- It has no clustering, no graph, OCR-only images and no audio or video, so it can't do "auto-organize by topic" or a mind map.
- Re-implement these four things on yearn's server:
  1. The LLM query planner that emits several sub-queries with `dt>=`/`dt<` filters (and add an author filter for "roughly who posted it").
  2. A pgvector top-k search followed by a small cross-encoder rerank.
  3. The `EntryDates` index of dates mentioned inside content.
  4. Hash-based incremental re-embedding.
- The research-mode tool loop (semantic search, then regex, then view item) is a good model for a later "ask my captures" chat.
- You could run Khoj unmodified next to yearn and feed it markdown exports over `PUT /api/content` for chat. Copying its code means AGPL.

**Supermemory: grab the MIT graph viewer and the memory model; do not run it as the brain.**
- `@supermemory/memory-graph` is a ready MIT canvas and d3-force mind-map renderer for yearn's web UI. Feed it your own nodes and edges. Note that it only draws edges the backend supplies; it computes no similarity edges.
- Steal the model: raw document vs extracted atomic facts, `updates`/`extends`/`derives` edges with `isLatest`, a batch "dreaming" consolidation pass, and decay by memory type.
- The engine is closed and license-capped, loses documents over roughly 10k characters (#1827), skips non-ASCII text (#1740), OOMs (#1826), and needs Gemini or Firecrawl for media and URLs. That is disqualifying for an offline-first archive.

**Reor: ideas only.**
- Its loved feature, "related items" via kNN on the item's own embedding excluding itself, is about 30 lines of code and is the cheapest first version of yearn's auto-linking. Persist those neighbors as edges, and the mind map falls out of it.
- Learn from its mistakes: do real full-text search (BM25 or Postgres FTS) fused with vector search, for example with RRF, not keyword reranking of vector hits; wire in the reranker you ship; and pick a multilingual embedding model from day one (Supermemory's #1104 and #1740 show the cost of English-only).
- Its death argues for keeping yearn's heavy ML on one Linux home server rather than shipping it in a cross-platform client.
