# Evaluation: Karakeep (formerly Hoarder)

Date: 2026-10-26. Source read at commit [`ba2acc3`](https://github.com/karakeep-app/karakeep/tree/ba2acc3cb9f89973df0ed74a3c6b0681c910503a) (2026-10-26), shallow clone, nothing executed. Issue numbers come from `gh` pulled the same day. Permalinks below use `B` = `https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a`.

## Verdict: grab specific components (and keep "run it as a backend" as a fallback, not the plan)

Karakeep is the most complete self-hosted "save anything, AI organizes it" server available. Its AI tagging pipeline, worker and queue design, and hybrid search are well built and worth copying in design. But it is a bookmark manager keyed on **one URL per item**, and it does the two things yearn exists for badly:
- **Capture fidelity.** The mobile share handler keeps one URL or one file, ignores the shared title, has no offline queue, and loses the share on failure.
- **Social-media extraction.** Only Reddit gets special handling, and that is currently blocked. Instagram, X and LinkedIn are not handled at all.

Bus factor is 1, and memory and upgrade bugs recur. Take the ideas: neighbour-tag reuse in AI tagging, the domain rate limit with retry-after, the post-crawl fan-out, the embedding "profile" text, and RRF hybrid search. Do not build yearn phase 2 *as* Karakeep.

---

## 1. What Karakeep does best (on its own merits)

### Standout features
- **Breadth, all in one place.** The README feature list ([README.md L21-44](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/README.md#L21-L44)) covers:
  - links, notes, images and PDFs
  - automatic metadata
  - lists with collaborators
  - full-text and semantic search
  - LLM tagging and summaries, including local Ollama
  - a rule engine and OCR
  - Chrome, Firefox and Safari extensions; iOS and Android apps
  - RSS auto-hoarding, a REST API, CLI, MCP server and agent "skills"
  - highlights, monolith full-page archive, yt-dlp video archive
  - importers (Pocket, Omnivore, Linkwarden, Chrome)
  - floccus sync and SSO

  No single peer covers all of this. Linkwarden (AGPL, 20.2k stars) and wallabag (MIT, 13.0k stars) each cover a subset. Karakeep has 29,989 stars and 1,599 forks (`gh api repos/karakeep-app/karakeep`, 2026-10-26).
- **AI tagging that converges instead of sprawling.** This is the most-upvoted completed request: [#111](https://github.com/karakeep-app/karakeep/issues/111), "Force AI to use existing tags", 54 👍, closed 2026-05-31. It is solved two ways:
  - **Curated-tags mode** forces the model onto a fixed tag list ([utils/tag.ts L32-37](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/utils/tag.ts#L32-L37)).
  - **By default it reuses neighbours' tags.** It embeds the new item, finds up to 10 similar bookmarks above a 0.75 similarity score, collects their tags (shortest first, capped at 1,000 characters), and injects "Similar bookmarks were tagged with… reuse if possible" ([tagging.ts L537-627](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L537-L627), [utils/tag.ts L39-46](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/utils/tag.ts#L39-L46)).

  Tags are deduplicated through a normalized name (lowercase, no space, `-` or `_`). AI tags are tracked separately from human tags (`attachedBy: "ai"`) and replaced atomically on re-tag, so re-tagging never touches the user's own tags ([tagging.ts L82-91, L414-523](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L414-L523)).
- **Prompts hardened against junk pages.** The tagging prompts tell the model to emit *no* tags for Cloudflare and CAPTCHA pages, login walls, cookie banners and error pages. They also ask for short, durable subject tags ([prompts.ts L15-91](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/prompts.ts#L15-L91)).
- **Structured output across providers.** One `InferenceClient` serves both OpenAI-compatible APIs and Ollama, with `outputSchema: structured | json | plain` (zod → `response_format`, or the Ollama `format` JSON schema) ([inference.ts L144-182, L424-470](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/inference.ts#L144-L182)). A fallback parser handles models that wrap their JSON in markdown fences or surrounding prose ([tagging.ts L46-80](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L46-L80)).
- **Hybrid search done properly.**
  - Each bookmark gets one embedding of a labelled "profile" text: title, summary, note, domain, URL, author, publisher, date, then a budgeted content excerpt ([embeddingsWorker.ts L232-345](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/embeddingsWorker.ts#L256-L345)).
  - Vectors are stored in Meilisearch as a `userProvided` embedder ([vectorstore-meilisearch L82-84, L261-283](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/plugins/vectorstore-meilisearch/src/index.ts#L261-L283)).
  - Results are merged with full-text search by reciprocal rank fusion ([searchRanking.ts](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/trpc/lib/searchRanking.ts#L13-L47)).
  - If the semantic side fails, search falls back to full-text ([trpc bookmarks.ts ~L1074-1115](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/trpc/routers/bookmarks.ts#L1074-L1115)).
  - Search has a query language: `is:`, `url:`, `title:`, `has:`, `tag:`, `list:`, `feed:`, `source:`, `after:`, `before:`, `age:` ([searchQueryParser.ts L126-334](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/searchQueryParser.ts#L126-L334)).
- **A crawler built for anti-bot pages** (v0.34.0, 2026-10-17, [release notes](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/release-notes/2026-10-17-v0.34.0.md)):
  - real Google Chrome driven through patchright
  - waits for Cloudflare and DataDome challenges to clear ([botChallenge.ts L17-41](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/utils/botChallenge.ts#L17-L41))
  - DuckDuckGo autoconsent to click through cookie and GDPR banners ([autoconsent.ts](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/utils/autoconsent.ts); request [#414](https://github.com/karakeep-app/karakeep/issues/414), 36 👍)
  - a versioned heuristic that decides whether a page has a usable reader view ([readerViewAssessment.ts](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/utils/readerViewAssessment.ts))
  - monolith single-file archives with a size cap ([crawlerWorker.ts L2136-2245](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/crawlerWorker.ts#L2136-L2245))
- **"Precrawled archive" ingestion.** A client can upload its own HTML capture (SingleFile, or client-side crawling) and create the link with `precrawledArchiveId`. The crawler then parses that file instead of fetching the URL ([crawlerWorker.ts L930-945](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/crawlerWorker.ts#L907-L945), [bookmarks types L260-265](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/types/bookmarks.ts#L242-L279)). This is the cleanest way to feed it content that only a logged-in client can see.
- **OCR with a quality gate.** Tesseract.js output is dropped below a confidence threshold. LLM-vision OCR is an option, with fallback to Tesseract when no client is configured ([assetPreprocessingWorker.ts L119-160](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/assetPreprocessingWorker.ts#L119-L160), prompt [prompts.ts L146-157](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/prompts.ts#L146-L157)).
- **Automation hooks.**
  - **Rule engine:** events (tag added, added to list, …), and/or conditions up to depth 10, then actions ([types/rules.ts L140-320](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/types/rules.ts#L140-L320)).
  - **Webhooks** on `created`, `edited`, `crawled`, `ai tagged` and `deleted` ([webhooks.ts L6-12](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/types/webhooks.ts#L6-L12)).

### What users ask for most (`search/issues … sort:reactions-+1`)
- **Delivered:**
  - shared lists ([#37](https://github.com/karakeep-app/karakeep/issues/37), 57 👍)
  - existing-tag reuse ([#111](https://github.com/karakeep-app/karakeep/issues/111), 54)
  - Safari ([#10](https://github.com/karakeep-app/karakeep/issues/10), 52)
  - mobile offline *reading* ([#1077](https://github.com/karakeep-app/karakeep/issues/1077), 52, closed 2026-07-25)
  - cookie-banner bypass ([#414](https://github.com/karakeep-app/karakeep/issues/414), 36)
  - client-side scraping with browser auth ([#172](https://github.com/karakeep-app/karakeep/issues/172), 35)
- **Still open:**
  - Postgres support ([#1782](https://github.com/karakeep-app/karakeep/issues/1782), 35)
  - richer tagging-prompt customization ([#2189](https://github.com/karakeep-app/karakeep/issues/2189), 26)
  - caching shares while away from the server ([#274](https://github.com/karakeep-app/karakeep/issues/274), 18, open since 2024-07)
  - an offline mobile share queue ([#2401](https://github.com/karakeep-app/karakeep/issues/2401), 17)
  - chat/RAG over bookmarks ([#863](https://github.com/karakeep-app/karakeep/issues/863), 15)
  - Reddit archiving ([#739](https://github.com/karakeep-app/karakeep/issues/739), 15)
  - saving X threads ([#1468](https://github.com/karakeep-app/karakeep/issues/1468), 12)
  - video subtitles as content ([#1442](https://github.com/karakeep-app/karakeep/issues/1442), 11)
- **Top discussions** (446 total): F-Droid build (#345, 10 upvotes) and chat/RAG with bookmarks (#709, 9).

---

## 2. Components worth taking (ranked)

| # | What | Where | Why it's good |
|---|---|---|---|
| 1 | Neighbour-tag reuse and atomic AI-tag replacement | [tagging.ts L414-627](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L414-L627) | A simple, proven way to keep auto-tags consistent without a fixed taxonomy. It uses the fresh embedding, so it does not wait for the index ([L544-549](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L541-L549)). |
| 2 | Tagging and OCR prompts | [prompts.ts L15-157](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/prompts.ts#L15-L157) | Rules for skipping junk pages and keeping tags short and durable, learned from real failures. Rewrite them rather than copy them verbatim (license, §5). |
| 3 | Queue contract | [queueing.ts L10-104](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/queueing.ts#L10-L104) and [liteque](https://github.com/karakeep-app/liteque) (**MIT**, same author) | Covers per-queue retries, `priority`, `groupId` fairness (one group per user), `idempotencyKey`, `delayMs`, timeouts and an abort signal. `QueueRetryAfterError` re-queues *without spending a retry*, used for rate limits. liteque is a SQLite-backed queue that needs no Redis. |
| 4 | Per-domain rate limit with jittered retry-after | [crawlerWorker.ts L503-541](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/crawlerWorker.ts#L503-L541) | Exactly what a scraper hitting instagram.com or x.com needs. |
| 5 | Post-crawl fan-out | [crawlerWorker.ts L1767-1836](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/crawlerWorker.ts#L1767-L1836) | Crawl → embed → tag; summarize; reindex; video; webhook. Each is a separate retryable job and inherits the parent's priority. |
| 6 | Embedding profile text plus RRF hybrid search | [embeddingsWorker.ts L256-345](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/embeddingsWorker.ts#L256-L345), [searchRanking.ts](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/trpc/lib/searchRanking.ts) | Labelled metadata (author, domain) in the embedded text makes "roughly who posted it" searchable semantically. RRF is about 30 lines. |
| 7 | Per-stage status columns | [schema.ts L262-270, L342-345](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/db/schema.ts#L262-L270) | Separate `pending/failure/success` for crawl, tagging, summary and embedding, plus `crawlStatusCode`, power a "broken links" retry page (v0.34.0). Worth copying *and* extending with a failure reason. |
| 8 | Reddit `.json` metadata extractor | [metascraper-reddit.ts L108-402](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/metascraper-plugins/metascraper-reddit.ts#L108-L402) | Clean zod-validated parsing of `media_metadata`, `selftext_html` and subreddit. The endpoint is currently blocked, though ([#2885](https://github.com/karakeep-app/karakeep/issues/2885), pri/high, open). |

Not worth taking:
- **Video worker.** It runs `yt-dlp` and keeps one mp4: no info-json, captions or thumbnails. Every failure path `return`s, so a failed download is recorded as a *completed* job with no retry and no per-bookmark status ([videoWorker.ts L156-186](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/videoWorker.ts#L151-L186)). L162 has a plain-quoted string containing `${jobId}`, so it logs the literal text.
- **The mobile share screen** (§6).

---

## 3. Issue-tracker health

Data: 1,799 issues (597 open, 1,202 closed: 1,096 completed, 61 duplicate, 45 not planned); 300 most recent PRs; 60 releases.

| Metric | Value |
|---|---|
| Bugs open / closed | 166 / 395 (30% of all `bug`-labelled issues are open) |
| Open issues still `status/untriaged` | 231 of 597 |
| Median time to close (completed) | 5.7 days overall; 6.2 days for bugs |
| Issues filed in 2026 | 388; **211 still open (54%)**. Among the 160 completed, p25 0.8 days, median 5.7 days, p75 29 days |
| 2026 bugs | 77 still open; of 93 closed, 48 closed within 7 days |
| Last 90 days | 92 issues filed, 56 open |
| Recent issues (since 2026-02-22) | 164 open, **77 of them with zero comments** |
| Commits since 2026-04-26 | **355 of 411 by MohamedBassem** (all time: 1,944 commits; next contributor 53) |
| PRs (since 2026-04-07) | 300 opened: 106 by MohamedBassem; 166 merged; 96 open |
| Releases | Still 0.x. v0.32.0 2026-05-08 → v0.33.1 08-01 → v0.34.0 10-17 ("sorry for the long wait") → v0.34.1 10-22. Roughly monthly in 2025, every 2-3 months in 2026 |
| Funding | Paid managed tier [cloud.karakeep.app](https://cloud.karakeep.app) ([README L100-102](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/README.md#L100-L102)), RevenueCat subscriptions in code, sponsors |
| AI-assisted development | `CLAUDE.md`, `AGENTS.md` and `GEMINI.md` in the repo root; 8 of the last 100 commits carry Claude co-author trailers; a `pullfrog` bot workflow |

The single maintainer is responsive: half of fixed issues close within about 6 days. But he is the funnel. Untriaged and zero-comment issues are piling up, and over half of 2026's issues are open.

### Recurring themes (keyword counts over titles, open/closed)
- **Crawler / browser**, 50 open / 125 closed. Social sites:
  - Reddit is blocked by the platform ([#2885](https://github.com/karakeep-app/karakeep/issues/2885), 10 comments, pri/high, open), and "Reddit doesn't really work at all?" ([#1883](https://github.com/karakeep-app/karakeep/issues/1883), open since 2025-08).
  - X reader view pulls in unrelated replies ([#2651](https://github.com/karakeep-app/karakeep/issues/2651)); X media is not downloaded ([#1331](https://github.com/karakeep-app/karakeep/issues/1331), open since 2025-04); X posts fail with a "privacy" error ([#2003](https://github.com/karakeep-app/karakeep/issues/2003)).
  - TikTok views are broken ([#2834](https://github.com/karakeep-app/karakeep/issues/2834), [#1983](https://github.com/karakeep-app/karakeep/issues/1983)).
  - Instagram has one closed feature request ([#2835](https://github.com/karakeep-app/karakeep/issues/2835)). **LinkedIn: zero issues.**
- **yt-dlp drift**, 12 open / 23 closed: an outdated yt-dlp returning 403 ([#2448](https://github.com/karakeep-app/karakeep/issues/2448), [#2015](https://github.com/karakeep-app/karakeep/issues/2015)); missing JS runtime ([#2632](https://github.com/karakeep-app/karakeep/issues/2632), [#2758](https://github.com/karakeep-app/karakeep/issues/2758)); Reddit video saved as audio only because ffmpeg was missing ([#2638](https://github.com/karakeep-app/karakeep/issues/2638)).
- **Resource use.**
  - Memory leak since 0.30: [#2344](https://github.com/karakeep-app/karakeep/issues/2344), **55 comments, open since 2026-01-04**.
  - Worker OOM on large imports: [#1748](https://github.com/karakeep-app/karakeep/issues/1748), pri/high, open since 2025-07. The workaround is Docker memory limits.
  - Chromium idle CPU: [#2369](https://github.com/karakeep-app/karakeep/issues/2369).
  - Parser OOM on SingleFile archives: [#3050](https://github.com/karakeep-app/karakeep/issues/3050).
- **AI**, 38 open / 86 closed:
  - With local Ollama, prompt text leaks into tags and some bookmarks get 90+ tags ([#3107](https://github.com/karakeep-app/karakeep/issues/3107), 0 comments).
  - Upgrade left inference jobs stuck pending ([#1605](https://github.com/karakeep-app/karakeep/issues/1605)).
  - Probable leak during tagging ([#2269](https://github.com/karakeep-app/karakeep/issues/2269)).
- **Upgrade pain:**
  - failed DB migrations at 0.28→0.29, 0.31.0 and 0.26→0.27 ([#2194](https://github.com/karakeep-app/karakeep/issues/2194), [#2513](https://github.com/karakeep-app/karakeep/issues/2513), [#1932](https://github.com/karakeep-app/karakeep/issues/1932))
  - "Upgrade handling seems fragile": SQLite busy is not retried ([#2368](https://github.com/karakeep-app/karakeep/issues/2368), open)
  - after a CLI migration, AI tags became user tags ([#2273](https://github.com/karakeep-app/karakeep/issues/2273))
- **Mobile share**, 16 open / 26 closed:
  - Android ignores the shared link title ([#3223](https://github.com/karakeep-app/karakeep/issues/3223), 2026-10-23, approved)
  - sharing several Chrome tabs saves only one ([#2192](https://github.com/karakeep-app/karakeep/issues/2192), open)
  - iOS share extension freezes the originating app ([#3018](https://github.com/karakeep-app/karakeep/issues/3018), pri/high, open)
  - no offline share queue ([#2401](https://github.com/karakeep-app/karakeep/issues/2401) and [#274](https://github.com/karakeep-app/karakeep/issues/274), both open)
- **Data loss:** few reports (7 matching titles, all closed), for example [#1659](https://github.com/karakeep-app/karakeep/issues/1659). The real loss path is the share flow (§6), which users experience as "it just spins" ([#2401](https://github.com/karakeep-app/karakeep/issues/2401)).

---

## 4. Code quality spot-check

**Shape.** About 154k lines of TypeScript in a Turborepo:
- `apps/web` (Next.js)
- `apps/workers`: 12 workers, with `crawlerWorker.ts` alone at 2,282 lines
- `apps/mobile` (Expo)
- `apps/cli`, `apps/mcp`, `apps/browser-extension`
- `packages/trpc` (business logic), `packages/api` (Hono REST plus OpenAPI), `packages/db` (Drizzle on SQLite)

**Good:**
- zod at every boundary: API, job payloads, LLM output.
- `crawlerWorker.ts` opens with a top-down map of the pipeline ([L1-10](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/crawlerWorker.ts#L1-L10)).
- OpenTelemetry tracing and structured log fields on every worker.
- Comments explain *why*. Examples: the dedup race note, and the offline-mutation comment quoted in §6.
- Writes that read first are guarded with SQLite `immediate` transactions ([tagging.ts L423-510](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/inference/tagging.ts#L423-L510)).
- SSRF-aware redirect validation and proxy selection before yt-dlp runs ([videoWorker.ts L81-83, L122-138](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/workers/workers/videoWorker.ts#L122-L138)).
- Only 63 `as any`, `ts-ignore` or lint-disable markers in the whole codebase.

**Weak:**
- **Thin test coverage for the size.** 90 test files in total: trpc 24, e2e 23, shared 11, workers 7, mobile 3.
  - The worker tests cover only utilities (crawler utils, feed parser, metadata resolver, reader-view assessment). Nothing tests the crawler flow, tagging, embeddings or the video worker.
  - CI runs shared, trpc, workers and e2e. **The plugins tests are commented out** ([ci.yml L79-81](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/.github/workflows/ci.yml#L79-L81)), and the mobile tests are not run.
- **"Plugins" are not an extension point.** They are hard-coded `import()`s of in-tree packages for queue, search, asset store and rate limit ([shared-server/plugins.ts L16-44](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared-server/src/plugins.ts#L16-L44)). Any new extractor means a fork, or an external process talking to the API.
- **Errors are swallowed in the video path**, and it has no status column (§2).
- **One job per stage, not per URL type.** No per-site extractor registry exists beyond metascraper rules.
- **Doc drift.** The README stack still says Puppeteer ([L75](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/README.md#L75)); the code uses patchright.

---

## 5. License (AGPL-3.0, [LICENSE](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/LICENSE))

- **Running Karakeep unmodified, for yourself, on your home box:** no obligations.
- **Running a modified fork for yourself:** AGPL §13 requires offering the source to *users who interact with it over a network*. You are the only user and you already have the source, so this is satisfied trivially. It only bites if friends or the public get accounts.
- **The yearn Android app calling Karakeep's REST API:** these are separate programs communicating at arm's length over HTTP, so the AGPL does not reach yearn. yearn can use any license.
- **Copying Karakeep code or prompt text into yearn:** that part of yearn becomes a derivative work. If yearn is ever *conveyed* (public GitHub repo, APK given to anyone), yearn must then be AGPL-3.0 as a whole.

  Reimplementing the *ideas* (neighbour-tag reuse, RRF, retry-after) is not copying. yearn is Kotlin/Python, not TypeScript, so a clean rewrite is natural. Rewrite the prompts rather than pasting them.
- **liteque** (the queue) is **MIT** ([npm metadata](https://www.npmjs.com/package/liteque): repo karakeep-app/liteque, author Mohamed Bassem), so it is free to reuse.

---

## 6. Risks

1. **Bus factor 1** (87% of commits in the last 6 months). Release gaps are growing, and the paid cloud tier sets priorities: "Plan upgrade CTA" ([#2491](https://github.com/karakeep-app/karakeep/issues/2491)), and "Hosted:"-prefixed bugs get pri labels.
2. **Social extraction is structurally weak and getting worse.** Platforms are actively blocking (Reddit [#2885](https://github.com/karakeep-app/karakeep/issues/2885); Reddit RSS reportedly ending, per the last comment on #2885). The project's answer is generic anti-bot browser work, not per-platform extractors with cookies.
3. **Operational weight.** Separate web, workers, Chrome (now full Google Chrome, larger image) and Meilisearch containers. A memory leak has been open for 10 months ([#2344](https://github.com/karakeep-app/karakeep/issues/2344)). Upgrades have broken migrations several times.
4. **One URL = one bookmark.** Dedup is an exact URL match per user. A re-share bumps `lastSavedAt` and **overwrites the note** when one is supplied ([trpc bookmarks.ts L131-146, L275-330](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/trpc/routers/bookmarks.ts#L275-L330)). Share history is not kept.

---

## 7. Mapping to yearn (closing)

### The prior-art report's share-screen claim: confirmed, and worse than stated
- **One item only.** [sharing.tsx L47-83](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/mobile/app/sharing.tsx#L47-L83) creates a LINK from `shareIntent.webUrl` (the first URL), else a LINK or TEXT from `text`, else uploads **only `files[0]`** (L73-79). Subject, title, extra text and other files are dropped. The Android title loss is now a filed bug ([#3223](https://github.com/karakeep-app/karakeep/issues/3223)).
- **Share registration.** Android registers `text/*`, `image/*` and `application/pdf` only, and the config sets no multi-share filter ([app.config.js L77-92](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/mobile/app.config.js#L77-L92)). This is consistent with [#2192](https://github.com/karakeep-app/karakeep/issues/2192), where several shared tabs saved as one.
- **New finding: no offline capture, and a failed share is lost.**
  - The share intent is reset immediately (L80-82), whatever the outcome.
  - Mutations run with `networkMode: "always"` so they fail fast. The code comment says real offline queuing is unimplemented ([offlineCache.ts L27-37](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/mobile/lib/offlineCache.ts#L27-L37)).
  - The upload goes straight to `POST /api/assets` with no local copy ([upload.ts L54-100](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/apps/mobile/lib/upload.ts#L54-L100)).
  - The result: off-network, or when the server is down, the share is gone. This is the exact opposite of yearn phase 1.

### Should yearn phase 2 *be* a Karakeep instance?

| yearn need | Karakeep today | How to fill | Cost |
|---|---|---|---|
| Raw share record: all extras, every file, referrer, note, per-share event | `note`, `title`, a fixed `source` enum and TEXT/ASSET `sourceUrl` ([types L242-279](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/types/bookmarks.ts#L242-L279)). No JSON metadata field. JSON uploads are not allowed ([assetdb.ts L30-42](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/shared/assetdb.ts#L30-L42)). Re-shares collapse into one bookmark | yearn keeps its own capture store (needed anyway for the offline queue) and only *projects* into Karakeep | Low if yearn owns it; a fork if it must live in Karakeep |
| Real media and caption for Instagram, X, Reddit, LinkedIn | Reddit metadata only, and blocked. yt-dlp single mp4. No gallery-dl, carousels, captions, info-json or cookies-per-site | A sidecar (gallery-dl, yt-dlp, instaloader with cookies) triggered by the `created` webhook. It builds an HTML "post page" (caption, author, media) and creates the link with `precrawledArchiveId`, so Karakeep's tagging, embeddings and search index the caption. It then attaches media via `POST /bookmarks/{id}/assets`, where `video`, `userUploaded` and `bannerImage` are allowed ([attachments.ts L50-66](https://github.com/karakeep-app/karakeep/blob/ba2acc3cb9f89973df0ed74a3c6b0681c910503a/packages/trpc/lib/attachments.ts#L50-L66)) | Medium: no fork, but the sidecar *is* most of phase 2. Carousel images are not seen by link tagging, which is text-only, so that part needs a fork |
| Topic organization with no user tagging | Strong: neighbour-reuse AI tags and summaries | Use as is | Free |
| Search by topic or vibe, and roughly who posted it | Hybrid FTS and semantic search (experimental flag). Author is in the embedding profile and FTS, but there is no `author:` qualifier | Good enough at first. Adding a qualifier needs a fork | Low / fork |
| Mind map / graph | None. Embeddings live inside Meilisearch, and similar-item lookup is internal, not exposed in the REST API | A separate graph service with its own embeddings, reading the API | High: a separate project either way |
| Per-item extraction status | Crawl, tagging, summary and embedding as `pending/failure/success` plus HTTP code. No failure reason. Video has no status | The sidecar tracks its own per-source status | Medium |

**Bottom line.** Running Karakeep would hand yearn tagging, search, a web UI and an archive for free. But yearn would still need:
- its own capture store
- its own social extractor sidecar, which is the hard part of phase 2
- its own graph

All of that sits on top of a single-maintainer, memory-leaky, 0.x stack with a different data model (URL-deduped bookmarks, not share events).

It is a reasonable **stopgap viewer**. The phone could post a projection via `POST /api/v1/bookmarks` with `note` set to the user's note while yearn keeps the raw record. It is the wrong foundation for phase 2. Build yearn's server around its own event store and queue (liteque-style), and port the tagging, rate-limit, fan-out and RRF ideas listed in §2.
