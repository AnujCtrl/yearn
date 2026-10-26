# Read-later and bookmark managers: what wallabag, Shiori and linkding each do best

Research date: 2026-10-26. This builds on the [prior-art survey](2026-10-26-share-capture-prior-art.md), which covers license, stack and status, and does not repeat it.

**Method.** Each repo was shallow-cloned and read only; nothing was built or run. Every code link below is a permalink to the commit that was read:

| Repo | Commit read |
|---|---|
| wallabag | [`091765a`](https://github.com/wallabag/wallabag/tree/091765a99b999cc19aaff568253c72d795c9f93e) |
| wallabag/android-app | [`5b87ad7`](https://github.com/wallabag/android-app/tree/5b87ad77dfaaa42f815bae240e076321a449e996) |
| Shiori | [`8fa2e2b`](https://github.com/go-shiori/shiori/tree/8fa2e2b4fa1f83c534016024209d0053a765f80d) |
| Shiori-Android-Client | [`37ba690`](https://github.com/DesarrolloAntonio/Shiori-Android-Client/tree/37ba690dcf89e64b71cbd52666b99c64b448a8a0) |
| linkding | [`27b7303`](https://github.com/sissbruecker/linkding/tree/27b7303baf41bb28babc610ac8eaa486e1ddfab5) |
| Pinkt (fibelatti/pinboard-kotlin) | [`2fe0c05`](https://github.com/fibelatti/pinboard-kotlin/tree/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c) |
| Linkdy | [`556676e`](https://github.com/JGeek00/linkdy/tree/556676ec4d42a3001a637743ad8fa7591f5f2500) |

**Issue data.** Three API pulls fed the issue-tracker sections:
- `gh issue list --state all --limit 300` for each repo. That returns the 300 most recent issues, PRs excluded. The time window each covers is shown in the stats tables.
- The most-upvoted issues: `search/issues` with `sort=reactions-+1&order=desc`.
- For wallabag and Shiori, the top 100 GitHub Discussions, read through GraphQL.

How each metric is defined:
- **Maintainer**: a commenter whose `authorAssociation` is OWNER, MEMBER or COLLABORATOR.
- **Response time**: the median time from an issue being opened to its first maintainer comment.
- **Active committers**: human commit authors on the default branch since 2025-10-26, with bots excluded.

---

## Summary table

| Project | Best at | Quality grade | License | What to take |
|---|---|---|---|---|
| **wallabag** (server) | Turning a URL into a clean article. It uses roughly 2,000 community extraction rules and logs into paywalled sites with your own credentials. It also accepts HTML the client already fetched instead of fetching the page itself. | **B+**. Mature: unit, integration and functional CI, five people triage issues, median first reply in 8 h. But no release in 12 months, and half of all issues are "page extracted wrong" reports. | MIT; extraction rules CC0 | **Components.** Take the CC0 rule set plus graby (MIT) for the home server's article fetcher. Take the idea of accepting client-supplied HTML. |
| **wallabag/android-app** | A durable offline action queue that merges redundant edits; full-text search and text-to-speech on the phone. | **C+**. Java with greenDAO, zero tests. 134 of 300 issues open; bugs take a median 50 days to close; recurring text-to-speech crashes. | **GPL-3.0** | **Ideas only.** Copying any code would make yearn GPL. |
| **Shiori** (server) | A single Go binary that downloads a page once and feeds that one copy to readability, the offline archiver and the ebook builder. It saves the record before processing it. | **C**. Nearly dormant: 8 human commits and 14 new issues in 12 months. History of upgrades losing data. Depends on two archived libraries. A bot closed 22 issues as stale. | MIT | **Ideas plus sibling libraries:** obelisk (MIT) and readeck/go-readability v2. |
| **Shiori-Android-Client** | The closest stack to yearn: Kotlin, Compose, Room, WorkManager, offline-first sync with temporary local IDs. | **C+**. Clean modules with some tests, but one developer, and the data-loss bug (#64) is confirmed in code: logout deletes unsynced saves. Shared text is treated as a URL as-is. | Apache-2.0 | **A lesson in what to avoid.** Its sync design is a usable reference. |
| **linkding** (server) | The cleanest small codebase of the three, built around a fast saving flow. One `check` call returns "already saved?", page metadata and suggested tags. It accepts client-uploaded snapshots, normalizes URLs, and has a one-line-per-rule auto-tagger and a real query parser. | **B+**. 24k lines of tests against 11.5k of source, including tests that cap database queries. Single maintainer; 73 of 123 issues from the last year still open; a 7-month release gap prompted "is this still maintained?" ([#1367](https://github.com/sissbruecker/linkding/issues/1367)). | MIT; SingleFile (called as a subprocess) is AGPL-3.0 | **Components:** the `check` and snapshot-upload API shapes, URL normalization, the auto-tag rule format, the multi-file "assets" model. |
| **Pinkt** (linkding/Pinboard client) | The best share-sheet handling in this group. Two share targets (edit or quick save), the quick one saving in a WorkManager foreground job. Pulls the URL out of free text and keeps text the user highlighted in Chrome. Falls back to a pending local save when the network fails. Writes files atomically. | **A-**. One developer, but 70 of 73 issues closed, median first reply 12 h, no open bugs, Compose plus Hilt, 17k lines of tests. | Apache-2.0 | **Copy specific files** (keep the Apache license notice): `ExtractUrl`, the share worker pattern, `OfflineCopyStorage`. |
| **Linkdy** (linkding client) | Cross-platform Flutter client listed on the Play Store and App Store. | **B-**. Responsive developer, but online-only (no local database), one test, Sentry telemetry. | Apache-2.0 | **Nothing to take.** |

**Best single feature per project.**
- **wallabag**: it saves whatever HTML the client sends instead of re-fetching the page ([`ContentProxy::updateEntry`](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/ContentProxy.php#L43-L89)). Its own Instagram rule says that is the only way Instagram works.
- **Shiori**: it saves the record first and processes the content after, so a failed fetch never loses the save ([`handler-api-ext.go`](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/webserver/handler-api-ext.go#L62-L112)).
- **linkding**: one `GET /api/bookmarks/check/` call returns the existing bookmark, the page metadata and the auto-tags ([`routes.py`](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/api/routes.py#L104-L133)). Its client Pinkt adds `ExtractUrl`, which keeps the quote from Chrome's "share highlight".

---

## 1. wallabag

### 1.1 Standout features (server)

**1. It saves content the client already fetched.** `ContentProxy::updateEntry` checks whether the request carried `html`. If it did, it calls `graby->setContentAsPrefetched()` and cleans that HTML instead of downloading the page ([ContentProxy.php L43-L89](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/ContentProxy.php#L43-L89)). Two more rules in the same function:
- Imported data is merged over fetched data, so a failed fetch never overwrites what the client sent (L68-72).
- The URL is always kept, so the entry can be re-fetched later (L75-77).

The REST endpoint `POST /api/entries` takes `content`, `title`, `authors`, `published_at`, `preview_picture` and `origin_url` ([EntryRestController.php L738-L831](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Controller/Api/EntryRestController.php#L738-L831)). If parsing throws, the error is only logged and the entry is still saved.

This matters for yearn because the shared rule files admit server-side fetching fails on JavaScript-heavy sites:
- [`instagram.com.txt`](https://github.com/fivefilters/ftr-site-config/blob/master/instagram.com.txt): "This works NOT with wallabag UI… only works with wallabagger browser-plugin with activated option 'Retrieve content from the browser'."
- [`reddit.com.txt`](https://github.com/fivefilters/ftr-site-config/blob/master/reddit.com.txt): comments "are injected by JavaScript" and are only captured when the browser sends the page.

**2. Site-specific extraction rules.** Extraction goes through graby ([MIT](https://github.com/j0k3r/graby); dependency at [composer.json L87-88](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/composer.json#L87-L88)). Graby uses fivefilters' `ftr-site-config`:
- About 2,060 per-site XPath rule files, covering title, body, strip, author, date and single-page links.
- Released **CC0 / public domain** ([LICENSE.txt](https://github.com/fivefilters/ftr-site-config/blob/master/LICENSE.txt)).
- The repo was pushed 2026-10-25, so it is still maintained.
- When no rule matches, graby falls back to readability-style detection.

**3. Logging into paywalled sites with stored credentials.** `GrabySiteConfigBuilder` looks up the user's saved credentials for a host, also trying the parent domain. It merges them with the site rule's `login_uri`, username and password fields, extra fields and `not_logged_in_xpath`, and masks the credentials in logs ([L39-L80](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/SiteConfig/GrabySiteConfigBuilder.php#L39-L80)). Credentials are encrypted with defuse/php-encryption; a key is generated with mode 0600 on first use ([CryptoProxy.php L19-L29](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/CryptoProxy.php#L19-L29)).

**4. Tracking the URL through redirects.** wallabag keeps three URLs per entry: `given_url` (what the user shared), `url` (the final URL) and `origin_url`. `updateOriginUrl` compares the two URLs part by part and acts on what changed ([ContentProxy.php L338-L403](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/ContentProxy.php#L338-L403)):
- Only a trailing slash or the scheme differs: update `url`.
- Only the fragment differs: do nothing.
- Anything else: record the original as `origin_url`.

"Ignore origin" rules skip known redirectors such as feedproxy ([wallabag.yml L164-L171](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/app/config/wallabag.yml#L164-L171)). Duplicate checks run on SHA-1 URL hashes through `/api/entries/exists?hashed_urls[]=` ([EntryRestController.php L92](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Controller/Api/EntryRestController.php#L92)).

**5. Rule-based auto-tagging.** Tagging rules are written in the RulerZ expression language over `title, url, content, language, mimetype, readingTime, domainName` and similar fields, with `matches`/`notmatches` for regex ([TaggingRule.php L33-34](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Entity/TaggingRule.php#L33-L34)). Rules run on every save and can be re-applied to the whole library ([RuleBasedTagger.php L30-L52](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/RuleBasedTagger.php#L30-L52)).

**6. Fallbacks when extraction fails.** `stockEntry` handles missing pieces in order ([ContentProxy.php L253-L330](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/ContentProxy.php#L253-L330)):
- No body: mark the entry `notParsed` and append the OpenGraph description.
- The URL is an image: use it as the preview.
- Otherwise: use the first image in the content as the preview.

`DownloadImages` stores images locally:
- Reads `srcset` attributes.
- Re-encodes images.
- Sanitizes SVGs with enshrined/svg-sanitize ([DownloadImages.php L65, L148-160](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/DownloadImages.php#L65)).

**7. Imports and exports.**
- 17 importers: Pocket (API, CSV and HTML), Instapaper, Omnivore, Pinboard, Delicious, Shaarli, Readability, elcurator, Firefox, Chrome, and wallabag v1/v2 ([src/Import/](https://github.com/wallabag/wallabag/tree/091765a99b999cc19aaff568253c72d795c9f93e/src/Import)). Imports can run through RabbitMQ or Redis workers ([src/Consumer/](https://github.com/wallabag/wallabag/tree/091765a99b999cc19aaff568253c72d795c9f93e/src/Consumer)).
- Exports to EPUB, PDF, CSV, JSON, XML, TXT and Markdown ([EntriesExport.php L130-L430](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/src/Helper/EntriesExport.php#L130-L430)).

### 1.2 Standout features (android-app)

- **Offline action queue that merges redundant edits.** Every action is written to a local queue first.
  - When the queue is read, the app ([QueueHelper.java L78-L182](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/src/main/java/fr/gaulupeau/apps/Poche/data/QueueHelper.java#L78-L182)):
    - drops edits to articles that were later deleted;
    - folds several article changes into the last one;
    - unions tag deletions.
  - Saves made offline are stored with `addLink(link, origin, localArticleId)`.
- **Full-text search on the phone** with SQLite FTS: a virtual table kept current by triggers ([FtsDao.java](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/src/main/java/fr/gaulupeau/apps/Poche/data/dao/FtsDao.java)).
- **Text-to-speech reading**, about 2,100 lines ([TtsService.java](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/src/main/java/fr/gaulupeau/apps/Poche/tts/TtsService.java)).
- **Weakness: it throws away most of what is shared.** `AddUrlProxyActivity` keeps the first `Patterns.WEB_URL` match from `EXTRA_TEXT` and discards the rest ([L29-L72](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/src/main/java/fr/gaulupeau/apps/Poche/ui/AddUrlProxyActivity.java#L29-L72)). The share filter accepts `text/plain` only ([manifest L61-75](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/src/main/AndroidManifest.xml#L61-L75)). Sharing several URLs at once is an open request ([#1419](https://github.com/wallabag/android-app/issues/1419)).

### 1.3 What users love and want

**Server, most-upvoted issues** (thumbs-up count first):
- Authentication: OpenID Connect (127, [#4205](https://github.com/wallabag/wallabag/issues/4205)); "API shouldn't require a username and password" (54, [#2800](https://github.com/wallabag/wallabag/issues/2800)); HTTP header auth (29, [#3898](https://github.com/wallabag/wallabag/issues/3898)); LDAP (24, [#966](https://github.com/wallabag/wallabag/issues/966)).
- Capture breadth: RSS to wallabag (31, closed, [#3360](https://github.com/wallabag/wallabag/issues/3360)); local PDF upload and viewer (18, [#2655](https://github.com/wallabag/wallabag/issues/2655)); better YouTube and video pages (16, [#2149](https://github.com/wallabag/wallabag/issues/2149), asking for description, channel and length as reading time); copy-paste content in (15, closed, [#2229](https://github.com/wallabag/wallabag/issues/2229)); import SingleFile captures (13, [#6847](https://github.com/wallabag/wallabag/issues/6847)).
- Discussions: the most-upvoted topic is an interface redesign (17, [#8467](https://github.com/wallabag/wallabag/discussions/8467)); MCP support has 5 ([#8665](https://github.com/wallabag/wallabag/discussions/8665)).

**Android app, most-upvoted issues:**
- Two-factor login (21, [#359](https://github.com/wallabag/android-app/issues/359)).
- Annotations (14, closed, [#115](https://github.com/wallabag/android-app/issues/115)).
- **"Tag right after share"** (14, closed, [#675](https://github.com/wallabag/android-app/issues/675)).
- Text-to-speech crashes (11, [#1348](https://github.com/wallabag/android-app/issues/1348)).
- Android 17 local-network connection failure (11, [#1516](https://github.com/wallabag/android-app/issues/1516)).
- Re-fetch an article from the app (8, [#1065](https://github.com/wallabag/android-app/issues/1065)).

### 1.4 Quality from the issue tracker

| Metric | Server: 300 issues, 2025-03 to 2026-10 | Android: 300 issues, 2017-11 to 2026-10 |
|---|---|---|
| Open / closed | 114 / 186 | 134 / 166 |
| Labeled bugs open / closed | 4 / 9 | 17 / 34 |
| Median days to close (all; bugs) | 1.1; 7.8 | 7.1; **49.5** |
| Issues with a maintainer reply | 66% | 49% |
| Median time to first maintainer reply | **7.8 h** | 13.2 h |
| Maintainers triaging | HolgerAusB 261 comments, j0k3r 66, Kdecherf 33, nicosomb 20, Simounet 5 | di72nn 276, tcitworld 45, others in single digits |
| Active committers, last 12 months | yguedidi 214, j0k3r 213, Kdecherf 13, others ≤7 | Strubbl 40, tcitworld 10 |
| Releases | latest 2.6.14 on 2025-10-07; **none since** | 2.5.3 on 2024-05-24, then 2.6.0 on 2026-06-01 (a two-year gap) |

**Recurring themes.**
- **Extraction failures dominate the server tracker.** 149 of 300 issues carry the "Site Config" label; they are titled "Wrong display in wallabag (HOST)", for example [#9084](https://github.com/wallabag/wallabag/issues/9084) (fstoppers.com) and [#8918](https://github.com/wallabag/wallabag/issues/8918) (golem.de). Without them, the server's median close time goes from 1.1 to 3.2 days.
- **Upgrades:** "Upgrading to Wallabag 2.4.3 broke the app" ([android #1261](https://github.com/wallabag/android-app/issues/1261)) is still open.
- **Android crashes:**
  - Text-to-speech: [#1519](https://github.com/wallabag/android-app/issues/1519) (Android 16), [#1367](https://github.com/wallabag/android-app/issues/1367).
  - Out of memory while syncing: [#1467](https://github.com/wallabag/android-app/issues/1467).
  - SQLite "too many parameters" in the deleted-article sweeper: [#1469](https://github.com/wallabag/android-app/issues/1469).
  - A 500-error notification loop: [#905](https://github.com/wallabag/android-app/issues/905).

### 1.5 Code-quality spot check

- **Server:** about 21k lines of PHP across Symfony bundles, 117 test files, CI that runs separate unit, integration and functional PHPUnit suites, and phpstan at [level 5](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/phpstan.neon#L5).
  - Errors are logged with context and processing continues. Credentials are masked in logs.
  - Still on Symfony 5.4 ([composer.json L138](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/composer.json#L138)); "Drop PHP < 8.1 + Symfony 6 upgrade" has been open since 2022 ([#6051](https://github.com/wallabag/wallabag/issues/6051)).
  - The RulerZ tagging library comes from a `dev-master` fork.
- **Android:** about 24k lines of Java, **zero test files**.
  - Dependencies: greenDAO 3.3.0 (no longer maintained), okhttp 5, and an API wrapper pinned to a beta, `v2.0.0-beta.6` ([build.gradle L68-L76](https://github.com/wallabag/android-app/blob/5b87ad77dfaaa42f815bae240e076321a449e996/app/build.gradle#L68-L76)).

### 1.6 License

- **Server: MIT** ([COPYING.md](https://github.com/wallabag/wallabag/blob/091765a99b999cc19aaff568253c72d795c9f93e/COPYING.md)). You may copy and modify it if you keep the copyright and permission notice.
- **graby: MIT.**
- **ftr-site-config: CC0.** No obligations at all.
- **android-app: GPL-3.0.** Copying any of its code makes yearn's APK a derivative that must be released under GPL-3.0 with source. Reimplement its ideas instead.

---

## 2. Shiori

### 2.1 Standout features (server)

1. **Save first, process second.** When the browser extension sends a page:
   - It uses the extension's HTML if present and only downloads when there is none.
   - It saves the bookmark *before* processing, "since we need the ID".
   - If processing fails, it logs the error and still returns the saved bookmark ([handler-api-ext.go L62-L112](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/webserver/handler-api-ext.go#L62-L112)).
2. **One download feeds three consumers.** `ProcessBookmark` copies the response through `io.MultiWriter` into buffers for archiving, readability parsing and the readability check, so the page is fetched only once ([processing.go L52-L64](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/core/processing.go#L52-L64)). The same function then:
   - extracts byline, text and HTML;
   - downloads the og:image, then the favicon if that fails, into a 600×400 thumbnail;
   - builds an EPUB and an offline archive when requested ([L43-L191](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/core/processing.go#L43-L191)).
3. **One binary for CLI, web UI and API**, with SQLite (FTS5), MySQL or Postgres.
   - Strips `utm_*` parameters on save ([url.go L39-L56](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/core/url.go#L39-L56)).
4. **Weaknesses in archiving and search:**
   - The archive format is go-shiori/warc. Despite the name it is a bbolt key-value file, not a standard WARC; its README says it is archived and "doesn't work in SPA site like Twitter or Reddit" ([go-shiori/warc](https://github.com/go-shiori/warc)).
   - go-readability is deprecated in favor of `codeberg.org/readeck/go-readability/v2` ([README](https://github.com/go-shiori/go-readability)). Both are still pinned in [go.mod L14-15](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/go.mod#L14-L15).
   - SQLite search wraps the whole query in quotes, so FTS treats it as one phrase ([sqlite.go L420-L436](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/database/sqlite.go#L420-L436)). That explains "Search API fails for multi-keyword queries" ([#899](https://github.com/go-shiori/shiori/issues/899)).

### 2.2 Standout features (Shiori-Android-Client)

- **Clean six-module Kotlin architecture** (presentation, domain, data, network, model, common) using Compose, Room, DataStore, WorkManager and Koin ([AGENTS.md](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/AGENTS.md)).
- **Offline-first sync.** Bookmarks created offline get a timestamp as their ID. `SyncWorker` creates each one on the server, then swaps in the server's ID. It refreshes an expired session by logging in again with the remembered credentials ([SyncWorker.kt L45-L113](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/data/src/main/java/com/desarrollodroide/data/repository/workers/SyncWorker.kt#L45-L113)). Readable HTML is cached in Room through `BookmarkHtmlEntity`.
- **Anti-patterns to avoid, all confirmed in code:**
  - **Logout deletes unsynced saves.** It cancels every sync worker and deletes all local bookmarks, unsynced ones included ([SendLogoutUseCase.kt L43-L47](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/domain/src/main/java/com/desarrollodroide/domain/usecase/SendLogoutUseCase.kt#L43-L47)). This is the root cause of "Lost all unsynced data when logging out… lost all my data for a month" ([#64](https://github.com/DesarrolloAntonio/Shiori-Android-Client/issues/64), open, no reply).
  - **Sync gives up silently.** It stops after `MAX_SYNC_ATTEMPTS = 5` ([L185-L191, L236-L240](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/data/src/main/java/com/desarrollodroide/data/repository/workers/SyncWorker.kt#L185-L191)).
  - **Fragile local-ID check.** "Is this a local ID?" is decided by `id > 1_000_000` ([IntExtensions.kt L10](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/data/src/main/java/com/desarrollodroide/data/extensions/IntExtensions.kt#L10)).
  - **Raw share text used as the URL.** The share handler takes all of `EXTRA_TEXT` as the URL with no extraction, falls back to the URL as the title, and refuses the share without a session ([BookmarkEditorActivity.kt L56-L66](https://github.com/DesarrolloAntonio/Shiori-Android-Client/blob/37ba690dcf89e64b71cbd52666b99c64b448a8a0/presentation/src/main/java/com/desarrollodroide/pagekeeper/ui/bookmarkeditor/BookmarkEditorActivity.kt#L56-L66)). See the title-equals-URL bug [#53](https://github.com/DesarrolloAntonio/Shiori-Android-Client/issues/53).

### 2.3 What users love and want

**Server, most-upvoted issues** (thumbs-up count first):
- "Archived and looking for a new maintainer" (16, [#256](https://github.com/go-shiori/shiori/issues/256), from 2020).
- Stay logged in longer (10, [#229](https://github.com/go-shiori/shiori/issues/229)).
- A "read later" option (8, [#178](https://github.com/go-shiori/shiori/issues/178)).
- **Archive with obelisk** (8, open since 2022, [#353](https://github.com/go-shiori/shiori/issues/353)).
- Bookmarklet (7, [#218](https://github.com/go-shiori/shiori/issues/218)).
- "Save the whole page, at least the images" (5, [#108](https://github.com/go-shiori/shiori/issues/108)).
- Can't remove a tag from an existing bookmark (5, open, [#1167](https://github.com/go-shiori/shiori/issues/1167)).

**Discussions:**
- Reverse-proxy authentication (15, [#461](https://github.com/go-shiori/shiori/discussions/461)).
- Public links page (11, [#476](https://github.com/go-shiori/shiori/discussions/476)).
- OIDC (8 and 7, [#625](https://github.com/go-shiori/shiori/discussions/625), [#1003](https://github.com/go-shiori/shiori/discussions/1003)).
- Highlights (7, [#626](https://github.com/go-shiori/shiori/discussions/626)).
- Auto-tagging rules (6, [#460](https://github.com/go-shiori/shiori/discussions/460)).
- Upload local PDFs (6, [#403](https://github.com/go-shiori/shiori/discussions/403)).

**Android client:**
- "Unable to log in" (7, 21 comments, open, [#57](https://github.com/DesarrolloAntonio/Shiori-Android-Client/issues/57)).
- User-installed root certificates ignored (3, [#43](https://github.com/DesarrolloAntonio/Shiori-Android-Client/issues/43)); a pull request with a fix has sat unmerged since 2025-03 ([#54](https://github.com/DesarrolloAntonio/Shiori-Android-Client/pull/54)).

### 2.4 Quality from the issue tracker

| Metric | Server: 300 issues, 2020-08 to 2026-09 | Android: 54 issues, 2024-01 to 2026-08 |
|---|---|---|
| Open / closed | 86 / 214 (**22 closed by the stale bot**) | 16 / 38 |
| Bugs open / closed | 54 / 85 | 8 / 7 |
| Median days to close (all; bugs) | 30.2; 8.2 | 30.3; 32.3 |
| Issues with a maintainer reply | 58% | 63% |
| Median time to first maintainer reply | 87.6 h | 133 h |
| Maintainers | fmartingr 249 comments, Monirzadeh 80, mirkoperillo 14 | DesarrolloAntonio, alone |
| Activity, last 12 months | **8 human commits** (fmartingr 5); **14 new issues**, 8 still open | 119 commits; 6 releases (latest v1.52.03 on 2026-09-24) |
| Latest server release | v1.8.0 on 2025-09-26 | n/a |

**Recurring themes.**
- **Database migrations and data loss during upgrades** (about 50 of 300 titles mention upgrade, migration or the database):
  - "Lost bookmarks since upgrade to 1.5.0" ([#291](https://github.com/go-shiori/shiori/issues/291)).
  - "Update will delete all archive data" ([#681](https://github.com/go-shiori/shiori/issues/681)), closed by the stale bot with no fix.
  - The 0.2.0→0.3.0 migration failing on MySQL and Postgres ([#1009](https://github.com/go-shiori/shiori/issues/1009), [#977](https://github.com/go-shiori/shiori/issues/977)).
  - "recover lost bookmarks" ([#1191](https://github.com/go-shiori/shiori/issues/1191), open).
- **Panics:**
  - "server panics when trying to bookmark the same url twice"; a commenter adds that changing only the order of query parameters also triggers it ([#1181](https://github.com/go-shiori/shiori/issues/1181), open since 2026-01).
  - The legacy `/api/` handlers panic instead of returning 401 ([#1204](https://github.com/go-shiori/shiori/issues/1204)).
- **Empty archives and EPUBs:** [#773](https://github.com/go-shiori/shiori/issues/773), [#1028](https://github.com/go-shiori/shiori/issues/1028).

### 2.5 Code-quality spot check

- **Server:** about 13k lines of Go with about 12k lines of tests (47 test files), plus e2e tests and per-database test files. Domain logic sits behind interfaces in `internal/domains`.
  - **Two HTTP stacks run side by side.** The new `internal/http` server still mounts the legacy handlers ("TODO: Remove this once the legacy API is removed", [server.go L95-L100](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/http/server.go#L95-L100)).
  - **Errors become panics in the old code.** The old `internal/webserver` turns errors into panics through `checkError` ([utils.go L14](https://github.com/go-shiori/shiori/blob/8fa2e2b4fa1f83c534016024209d0053a765f80d/internal/webserver/utils.go#L14)); there are 14 `panic(` calls outside tests.
- **Android:** about 24k lines of Kotlin with 59 test and androidTest files. Credentials go through a `SecretCipher`. The recent pull request "Bring the Android client up to parity with the web UI" ([#65](https://github.com/DesarrolloAntonio/Shiori-Android-Client/pull/65)) and the AGENTS.md file point to AI-assisted bulk changes.

### 2.6 License

- **Server: MIT**, as are obelisk and go-shiori/warc. Keep the notice.
- **Android client: Apache-2.0** (no NOTICE file). Keep the license text and mark changed files. Apache-2.0 also grants patent rights.

---

## 3. linkding (plus Pinkt and Linkdy)

### 3.1 Standout features (server)

1. **One call does everything a save dialog needs.** `GET /api/bookmarks/check/?url=` returns ([routes.py L104-L133](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/api/routes.py#L104-L133)):
   - the existing bookmark, matched by normalized URL;
   - the scraped title, description and preview image;
   - the tags auto-tagging *would* apply.

   Metadata is cached in an LRU cache, so the save that follows does not scrape again ([website_loader.py](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/website_loader.py#L31-L45)). The scraper streams the page and stops at `</head>`, with a 5 MB cap ([L97-L120](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/website_loader.py#L97-L120)).
2. **Clients can upload their own snapshot.** `POST /api/bookmarks/singlefile/` takes `url` plus a file, creates the bookmark if needed with server-side snapshotting switched off, and attaches the file as a gzipped HTML snapshot ([routes.py L136-L165](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/api/routes.py#L136-L165), [assets.py `upload_snapshot` L151-L175](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/assets.py#L151-L175)).
   - A bookmark can hold many **assets**: HTML snapshots, PDF downloads (with a size cap) and arbitrary uploads ([assets.py L59-L150](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/assets.py#L59-L150)).
3. **Server-side snapshots with careful cleanup.** linkding calls `single-file-cli` with uBlock options. On timeout it sends terminate, then kills the whole process group so stray Chromium processes die too ([singlefile.py L17-L49](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/singlefile.py#L17-L49)).
   - Snapshots run one at a time under a Huey lock and resume when the server starts ([tasks.py L278-L335](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/tasks.py#L278-L335)).
   - Background tasks retry with exponential backoff: 60 s, 240 s, … 15,360 s ([tasks.py L21-L40](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/tasks.py#L21-L40)).
   - Internet Archive submission is a separate path.
4. **URL normalization for duplicate checks.** It lowercases scheme and host, strips trailing slashes, and sorts query parameters ([utils.py L170-L210](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/utils.py#L170-L210)). The result is stored in an indexed `url_normalized` column, with exact-URL matching as a fallback ([models.py L106-L113](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/models.py#L106-L113)). Saving a URL a second time merges into the existing bookmark ([bookmarks.py L12-L52](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/bookmarks.py#L12-L52)).
5. **Auto-tagging in about 50 lines.** Each rule is a line of the form `<domain[/path][?query][#fragment]> tag tag…`, with comments allowed. Domains are compared as IDNA suffixes, paths as prefixes, query strings by subset ([auto_tagging.py L7-L56](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/auto_tagging.py#L7-L56)).
6. **A real search query parser**: tokenizer plus parser with `#tag`, `!special`, AND/OR/NOT, parentheses and quoting, in 573 lines ([search_query_parser.py](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/search_query_parser.py)). Saved searches are called "bundles".
   - **Known bug:** `read_term` stops at `#` and `!` even inside a word ([L45-L56](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/search_query_parser.py#L45-L56)). That is [#1530](https://github.com/sissbruecker/linkding/issues/1530), "a bookmark's URL with a #fragment finds nothing".
7. **PWA share target**: maps both `url` and `text` to `url` ([manifest.py L87-L96](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/views/manifest.py#L87-L96)).

### 3.2 Standout features (Pinkt and Linkdy)

**Pinkt (Kotlin):**
- **Two share targets.** "Share to Pinkt" opens an editor; "no edit" uses a transparent activity ([manifest L42-L70](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/AndroidManifest.xml#L42-L70)).
- **Quick save runs in the background.** `ShareReceiverWorker` is a WorkManager foreground job that posts progress, "already saved on <date>", success and failure notifications ([ShareReceiverWorker.kt L50-L137](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/kotlin/com/fibelatti/pinboard/features/share/ShareReceiverWorker.kt#L50-L137)).
- **`ExtractUrl` handles messy share text** ([ExtractUrl.kt L11-L41](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/kotlin/com/fibelatti/pinboard/features/posts/domain/usecase/ExtractUrl.kt#L11-L41)):
  - finds the first URL anywhere in the free text;
  - cuts the `#:~:text=` fragment that Chrome adds when sharing a highlight;
  - **keeps the quoted text before the URL as the highlight**;
  - removes UTM and user-chosen parameters.
- **Previews never block a save.** `GetUrlPreview` parses raw bytes so jsoup can detect the charset, caps the download at 2 MB, and on any failure recovers to a minimal preview ([GetUrlPreview.kt L33-L41](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/kotlin/com/fibelatti/pinboard/features/posts/domain/usecase/GetUrlPreview.kt#L33-L41)).
- **Saves fall back to local storage.** The linkding data source saves locally with a `pendingSync = ADD` marker on the row when offline, or when the remote call fails with an `IOException` ([PostsDataSourceLinkdingApi.kt L62-L116](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/kotlin/com/fibelatti/pinboard/features/linkding/data/PostsDataSourceLinkdingApi.kt#L62-L116)).
- **Atomic offline copies.** `OfflineCopyStorage.write` writes to a temp file and then moves it into place, plus sweeps orphan files ([OfflineCopyStorage.kt L42-L58](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/app/src/main/kotlin/com/fibelatti/pinboard/features/offline/OfflineCopyStorage.kt#L42-L58)).

**Linkdy (Flutter):**
- Share filter accepts `text/*` ([manifest](https://github.com/JGeek00/linkdy/blob/556676ec4d42a3001a637743ad8fa7591f5f2500/android/app/src/main/AndroidManifest.xml#L29-L33)).
- Built on dio, riverpod and a webview. No local database and no offline queue; `sentry_flutter` is bundled ([pubspec.yaml](https://github.com/JGeek00/linkdy/blob/556676ec4d42a3001a637743ad8fa7591f5f2500/pubspec.yaml)).

### 3.3 What users love and want

**linkding, most-upvoted issues** (thumbs-up count first):
- Categories or folders beyond tags (46, [#285](https://github.com/sissbruecker/linkding/issues/285)).
- **Check whether saved URLs still work** (40, [#68](https://github.com/sissbruecker/linkding/issues/68)).
- A "Thank you!" thread (19, [#196](https://github.com/sissbruecker/linkding/issues/196)).
- ArchiveBox as an archive backend (18, [#380](https://github.com/sissbruecker/linkding/issues/380)).
- Webhooks (17, [#492](https://github.com/sissbruecker/linkding/issues/492)).
- Group by domain (17, [#415](https://github.com/sissbruecker/linkding/issues/415)).
- **Generate tags on import** (16, [#69](https://github.com/sissbruecker/linkding/issues/69)).
- **Download page contents for better search** (14, [#609](https://github.com/sissbruecker/linkding/issues/609)).
- An official MCP server (11, [#1362](https://github.com/sissbruecker/linkding/issues/1362)).

**Praise:** "There are really no good alternatives… Linkding is the best" ([#1367 comment](https://github.com/sissbruecker/linkding/issues/1367)). In the same thread one user says they moved to Karakeep, and another stays because linkding paginates instead of scrolling infinitely.

**Pinkt:** F-Droid listing (6, [#11](https://github.com/fibelatti/pinboard-kotlin/issues/11)), linkding support (closed, [#107](https://github.com/fibelatti/pinboard-kotlin/issues/107)), Karakeep support ([#420](https://github.com/fibelatti/pinboard-kotlin/issues/420)), offline linkding snapshots (closed, [#346](https://github.com/fibelatti/pinboard-kotlin/issues/346)).

**Linkdy:** in-app reader view ([#18](https://github.com/JGeek00/linkdy/issues/18)), a share-menu target (closed, [#4](https://github.com/JGeek00/linkdy/issues/4)).

### 3.4 Quality from the issue tracker

| Metric | linkding: 300 issues, 2024-11 to 2026-10 | Pinkt: 73 issues, 2019 to 2026 | Linkdy: 37 issues |
|---|---|---|---|
| Open / closed | 128 / 172 | 3 / 70 | 7 / 30 |
| Bugs open / closed | labels barely used (2 closed) | **0 / 29** | 0 / 4 |
| Median days to close (all; bugs) | 7.3; n/a | 9.5; **3.9** | 17; 8 |
| Issues with a maintainer reply | **44%** (32% have no comments at all) | **97%** | 78% |
| Median time to first maintainer reply | 41.7 h | **11.6 h** | 125 h |
| Maintainers | sissbruecker alone (82 of about 90 human commits in 12 months) | fibelatti alone (223 commits in 12 months) | JGeek00 (25) plus 3 contributors |
| Releases | v1.45.0 on 2026-01-06, then **nothing until** v1.46.0 on 2026-08-09; v1.47.0 on 2026-09-13 | v3.19.3 on 2026-10-23, roughly monthly | 1.8.3 on 2026-10-17 |

linkding's backlog is growing: 73 of 123 issues filed in the last 12 months are open, and many open pull requests from outside contributors have no review (see the [#1367](https://github.com/sissbruecker/linkding/issues/1367) thread).

**Recurring themes.**
- **Snapshots unreliable:**
  - The extension "doesn't reliably attach the html snapshot" although saving reports success ([#1210](https://github.com/sissbruecker/linkding/issues/1210)).
  - The 1.46.0-plus image's snapshots "always fail" ([#1443](https://github.com/sissbruecker/linkding/issues/1443), fixed).
  - Twitter snapshots show "JavaScript disabled" ([#1250](https://github.com/sissbruecker/linkding/issues/1250)).
  - Constant snapshot retries ([#1420](https://github.com/sissbruecker/linkding/issues/1420)).
  - The asset view reads the whole file into memory and runs out of memory ([#1455](https://github.com/sissbruecker/linkding/issues/1455), fixed).
- **Upgrades:** a 500 error after 1.40→1.41 ([#1108](https://github.com/sissbruecker/linkding/issues/1108)); a Postgres certificate permission error ([#660](https://github.com/sissbruecker/linkding/issues/660)); `psycopg` connection closed in background tasks ([#1316](https://github.com/sissbruecker/linkding/issues/1316)).
- **Pinkt's data-loss history, now fixed:**
  - "Bookmarks were wiped during prolonged Linkding server outage… not able to queue a bookmark for saving when the server is offline" ([#345](https://github.com/fibelatti/pinboard-kotlin/issues/345)). The pending-sync rework shipped in 3.3.0.
  - "No bookmarks after upgrade to 3.2" ([#337](https://github.com/fibelatti/pinboard-kotlin/issues/337)).
  - Share bugs: shares of `mobile.twitter` links failing ([#7](https://github.com/fibelatti/pinboard-kotlin/issues/7)) and Firefox shares failing ([#3](https://github.com/fibelatti/pinboard-kotlin/issues/3)).

### 3.5 Code-quality spot check

- **linkding:** about 11.5k lines of Python against **about 24k lines of tests**.
  - Tests cover API permissions, OIDC and auth proxies, and performance (`assertNumQueries` in 3 files), plus Playwright e2e tests (`tests_e2e/`).
  - Services are small single-purpose modules (`services/*.py`, 2.9k lines in total).
  - Errors are logged and work continues: the metadata scraper swallows all exceptions and returns empty fields.
  - One wart: dead code in `monolith.py` builds a `shell=True` command with the URL in single quotes, which is an injection pattern, though nothing calls it ([L13-L23](https://github.com/sissbruecker/linkding/blob/27b7303baf41bb28babc610ac8eaa486e1ddfab5/bookmarks/services/monolith.py#L13-L23)).
- **Pinkt:** about 31k lines of Kotlin with about 17k lines of tests, Room DAO tests, Hilt, Compose, spotless, reproducible IzzyOnDroid builds and a signed-APK digest ([README](https://github.com/fibelatti/pinboard-kotlin/blob/2fe0c05fda06674ad6b21ad2b0604b2d29658d7c/README.md)). Comments explain *why*, for example the atomic replace and the charset sniffing.
- **Linkdy:** about 13k lines of Dart, one test.

### 3.6 License

- **linkding: MIT.** Keep the notice.
- **SingleFile and single-file-cli: AGPL-3.0** ([single-file-cli](https://github.com/gildas-lormeau/single-file-cli)). linkding runs it as a separate process and keeps its own license. yearn can do the same on the home server; modifying SingleFile and serving it over a network would trigger AGPL source obligations. This is my reading of the license, not legal advice.
- **Pinkt and Linkdy: Apache-2.0** (no NOTICE file). Copying files is fine if you keep the license header and text and mark changes.
- LinkBuddy is AGPL-3.0; Linklater is MIT. Neither was read in depth.

---

## What yearn should take

**wallabag.** Not something to take wholesale: the server is PHP built around articles, and the Android app is GPL. Take:
- **ftr-site-config (CC0)** as the article-rule set for the home server, with graby (MIT) or a port of it.
- The **prefetched-content contract**: the client uploads the HTML it captured and the server only falls back to fetching. Its own Instagram and Reddit rules show server-side fetching fails on exactly yearn's targets.
- The `given_url` / `url` / `origin_url` triplet and the "save even when parsing fails, flag `notParsed`" rule.
- As ideas only: the Android offline queue that merges redundant edits.

**Shiori.** Ideas only; the server is close to dormant and depends on archived libraries. Copy two patterns into yearn's server pipeline:
- **Save the raw record before any processing.**
- **Fetch once and feed every extractor from that one copy.**

If the server is written in Go, take obelisk (MIT, single-file HTML) and readeck/go-readability v2 directly rather than Shiori's dependencies. The Android client shares yearn's stack (Kotlin, Room, WorkManager) and is a useful sync reference. It is mostly a list of what to avoid:
- Never delete unsynced captures on logout or account change.
- Never let a worker give up silently.
- Never treat the share text as the URL.
- Never refuse a share because there is no session.

**linkding.** Don't take it wholesale: it is a URL bookmark manager with no media and no graph. Take:
- The **API shapes**: a pre-save `check` call returning existing item, metadata and suggested tags; and a "client-supplied snapshot creates the item if missing" upload.
- The **multi-asset item model**, normalized-URL duplicate detection, and the line-based auto-tag rule format. These are MIT and small enough to port.

From **Pinkt** (Apache-2.0, Kotlin), copy or adapt three files with attribution:
- `ExtractUrl`, which keeps Chrome's highlighted quote. It still discards non-URL text, so yearn should store the whole raw text alongside it.
- The quick-share **WorkManager foreground worker with notifications**.
- `OfflineCopyStorage`'s atomic write.

Linkdy has nothing to take.
