# yearn — phase 1 design: capture everything, sync it home

Date: 2026-10-26
Owner: Anuj (sole user)
Status: approved 2026-10-26 (development started)

## 1. Intent

Anuj scrolls past things he wants to come back to: Instagram reels and posts, X posts, articles, videos and images, Reddit and LinkedIn posts, GitHub repos. Today they get lost. yearn is his personal "save for later" for everything.

The whole interaction is: tap Share in any app, pick yearn, done. No forms, no tagging. Weeks later he wants to find a save by its topic or vibe and roughly who posted it, and eventually browse everything as a mind map.

The project has two phases. **This document covers phase 1 only.**

- **Phase 1, "just track":** an Android app that catches every share reliably and keeps everything Android hands it, raw and offline, plus a small receiver on his home desktop that stores those raw captures as they arrive.
- **Phase 2 (separate project, designed later):** the home desktop fetches the real media and full caption/author behind each link, organizes saves by topic automatically, and builds the searchable mind map.

Phase 1 exists so that nothing is ever lost and phase 2 has complete raw material to work from. The research (section 9) found that no open-source tool keeps the full share: every one keeps only the first URL or first file, and most lose the share if their server is unreachable. Keeping the full raw share, durably, is yearn's foundation.

## 2. Goals, non-goals, anti-goals

**Goals**
- yearn appears in the system share sheet from every app and accepts any shared content: text, links, images, video, other files, single or multiple items.
- Every share is persisted on the phone before anything else happens, including with no network.
- The full raw share is kept: every field Android delivers, every file, plus capture metadata (section 5).
- Captures reach the home desktop over Tailscale from anywhere, automatically, and are never deleted from the phone before the desktop confirms it has them.
- The capture moment is beautiful: a glass liquid drop that becomes a frosted sheet and pours back (section 6).
- Opening yearn shows the saves, newest first, with sync status.

**Non-goals (phase 1)**
- Fetching media, captions or metadata from links (yt-dlp, gallery-dl, etc.). That is phase 2.
- AI, tagging, topic grouping, search, the mind map. Phase 2.
- iOS, a web app, multiple users, sharing with other people.
- Login handling for Instagram etc. (phase 2 concern, though see section 10).

**Anti-goals: failures even if everything else works**
- A share is ever lost: on no network, a crash, a reboot, a server outage, a full disk, or the app being killed mid-animation.
- Saving waits on the network or asks Anuj anything. The share sheet must never block on a dialog, a field to fill in, or a spinner.
- Anything shared leaves his devices for a third party. Captures go phone → his desktop over Tailscale only. No analytics, no crash-reporting services, no archive.org submissions, no cloud AI in phase 1.
- The capture animation looks cheap or janky. Anuj explicitly wants it to feel "like real life water"; a plain blur-and-slide is a failure.

## 3. Decisions Anuj made

- **Platform:** native Android app in **Kotlin**.
- **Interaction:** share-and-forget. Auto-organization comes later; Anuj never tags.
- **Note:** a small "note dot" on the capture sheet. Tapping it lets him write a note; ignoring it is the normal path.
- **Capture animation:** a glass drop forms at the bottom of the screen, spreads up into a frosted glass sheet over the blurred source app, shows what was caught, then pours back down into the drop, which squishes, ripples and vanishes. It must feel like real water, which will take real animation work. (Mockup: `docs/design/capture-poc.html`.)
- **Home screen:** a list of saves, newest first, each with its sync status.
- **Sync transport:** **Tailscale**, so it syncs from anywhere, not only home Wi-Fi.
- **Sync timing:** sync whenever a connection is available, plus a once-a-day sync. (Interpretation, confirmed in conversation: upload as soon as possible; a daily background run catches anything left behind.)
- **Server:** runs on his home desktop. Phase 1 needs only a receiver there; the heavy processing comes in phase 2.

## 4. Constraints

**Android share delivery** (from the research, `docs/research/2026-10-26-share-capture-prior-art.md` §6):
- Most apps send `ACTION_SEND` with `text/plain`: some words plus a URL in `EXTRA_TEXT`, sometimes `EXTRA_SUBJECT`/`EXTRA_TITLE`. What Instagram, X, LinkedIn, Reddit and others actually send is undocumented (closed source). That is why phase 1 records the full raw intent.
- Files arrive as `content://` URIs in `EXTRA_STREAM` and/or `ClipData`. **Read permission ends when the receiving activity finishes**, so files must be copied into app storage immediately, during the capture animation.
- Which app shared it can't be relied on: `getReferrer()` can be spoofed or empty, and `getLaunchedFromPackage()` (Android 14+) is usually null unless the sender opts in. Record every available hint, labelled as a hint.

**Phone:** Samsung **Galaxy Z Fold8** (on his tailnet as `my-fold`). The app must look right on **both the cover screen and the inner screen**. Consequences:
- The capture activity must survive folding/unfolding mid-capture (a configuration change) without losing the record, the in-progress file copy, or the note being typed. Persist-first (section 6) makes this safe; the animation may restart its current phase on the new screen.
- The sheet, drop and home-screen list need layouts for the narrow cover screen and the near-square inner screen.
- High refresh-rate display: animations and shaders are tuned for it.
- Samsung's One UI battery management can put apps to sleep and delay or kill background work. The app must guide Anuj once to exempt yearn from battery optimization ("Unrestricted"), and the daily sync must still be checked to actually run on this phone.
- Whether One UI honours cross-window blur behind the capture window must be checked on the device early; if it doesn't, the fallback look (section 6) becomes the main look and must be designed as such.

**Desktop host** (Anuj's desktop/Arch desktop):
- Store captures on the main home volume. **Never write to NTFS (ntfs-3g) drives**; large writes there freeze the machine.
- The desktop has had rare crashes. The receiver must make a capture durable on disk (fsync file and directory) before telling the phone "stored".
- The desktop is not always on. The phone must tolerate the receiver being unreachable for days.

**Tailscale:**
- The receiver listens only on the desktop's Tailscale address (or localhost behind `tailscale serve`), never on the LAN or public internet.
- On the phone, Tailscale is a VPN app. Android allows one active VPN, and if Tailscale is off the upload simply fails and retries later. yearn does not manage Tailscale.

**Licensing:** see section 9. GPL/AGPL projects are used as ideas only, re-implemented. Copyable code comes only from MIT / Apache-2.0 / CC0 sources, with their notices kept. yearn's own license is undecided; keeping it free of GPL code leaves that open.

## 5. The raw capture record

One capture = one share. Each capture gets a UUID generated on the phone at the moment of sharing. The UUID is the idempotency key end to end.

Recorded per capture:
- `id`: UUID v4 (or v7; builder's call)
- `captured_at`: wall-clock time with timezone offset, set once and **never changed** (MoeMemos bug #373 is a lesson: no "updated" overwrite)
- `intent`: `action`, `type` (MIME), `categories`, `flags`, `data` URI if any
- `extras`: **every** extra key present, each serialized with its Java type: strings and char sequences as text (styled `CharSequence` also as HTML via `Html.toHtml`), string arrays, numbers, booleans, URIs as strings, Bundles recursively. Unknown/unserializable types are recorded as `{key, class_name, toString}` rather than dropped. Includes `EXTRA_TEXT`, `EXTRA_HTML_TEXT`, `EXTRA_SUBJECT`, `EXTRA_TITLE`, `EXTRA_STREAM`, `EXTRA_REFERRER`, `EXTRA_REFERRER_NAME`, chooser extras, and anything app-specific.
- `clip_data`: every ClipData item: text, HTML text, URI, intent; plus the clip description's MIME types and label
- `source_hints`: `getReferrer()`, `EXTRA_REFERRER`/`EXTRA_REFERRER_NAME`, `getLaunchedFromPackage()` / `getLaunchedFromUid()` where available (API 34+), and the calling package if any. All labelled as hints.
- `files`: for every URI from `EXTRA_STREAM` and ClipData: the copied file's local path, original display name and size (`OpenableColumns`), MIME type, byte length, SHA-256. A URI that can't be read is recorded with its error, not dropped.
- `note`: optional text from the note dot, with its own `note_updated_at`
- `device`: app version, Android version, device model
- `sync`: status (`waiting`, `uploading`, `uploaded`, `failed`), attempts, last error, `uploaded_at`

The record format is versioned (`schema_version`) because phase 2 will read every record ever captured.

## 6. The capture experience

**Flow**
1. Share target activity launches over the source app: transparent window, no visible app chrome.
2. **Persist first.** Before or in parallel with the first animation frame: write the record to the local database and start copying files. The share is considered caught once the record row is committed. File copies stream (never read whole files into memory; videos can be large) and must finish before the activity finishes. If a copy is still running when the animation ends, the sheet stays up showing progress until it's done. *(Claude's call: never losing a share outranks never waiting. Most shares are text + URL and finish instantly; only big media shows progress. While a copy runs, Back keeps the sheet up and says "Still saving…"; Home leaves the activity alive in the background and the copy continues. A builder may replace this with a hand-off to a foreground service that holds the URI grant, but only after proving on the Fold8 that the grant survives the activity finishing for the main source apps.)*
3. The glass drop forms and spreads into the frosted sheet. The sheet shows what was caught: a thumbnail (first image, or a frame from a video, or a favicon/placeholder for a link), a recognisable source label (domain or app hint), the first lines of the text/caption, and a progress line that completes when the record and files are durable. Then "Saved to yearn" with the note dot.
4. The sheet pours back into the drop, which squishes, ripples, vanishes. Activity finishes. The source app is back exactly as it was.
5. **Note dot:** tapping it stops the pour-back and opens a note field inside the sheet. Done / back saves the note and resumes the pour-back. The note is saved to the same capture.
6. An upload is enqueued (section 8). The share sheet never waits for it.

**Timing** *(Claude's call)*: a typical text/link capture takes about 2 seconds end to end, with the sheet fully open about 1 second. Swiping the sheet down sends it straight to the pour-back; tapping the note dot pauses it; otherwise the pour-back starts on its own. The browser mockup is a mood reference for the shapes and sequence, not a timing spec.

**Accepted shares:** `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for `*/*` (text, images, video, audio, documents, anything). A share with nothing usable is still recorded (the raw intent alone) so it can be inspected.

**Animation quality bar**
- One continuous glass shape that morphs between drop and sheet, with liquid merging (metaball-style), not a crossfade between two elements.
- Spring physics for every movement (overshoot and settle), not fixed-duration easing curves.
- Real glass: the sheet refracts and blurs what's behind it (AGSL `RuntimeShader` on Android 13+), with a highlight and edge light.
- Blur of the source app behind the window uses Android's cross-window blur (`FLAG_BLUR_BEHIND` / `blurBehindRadius`, API 31+). Some devices or battery saver disable it; then fall back to a darkened, tinted scrim. The design must look intentional in the fallback, not broken.
- Respect the system "remove animations" setting with a short, calm fade version.
- Target smooth frame rate on the Fold8 at its high refresh rate, on both screens; dropped frames during the pour are a defect.

**Nothing in the share path can fail visibly.** If the database write fails (e.g. disk full), the sheet says so in plain words and the raw intent is kept in memory and retried; it never silently claims "Saved".

## 7. The yearn app (opened from the home screen)

- **List of saves, newest first.** Each row: thumbnail, source label, first line of text/caption, time ("2 h ago"), note indicator, and a sync status chip: Waiting, Uploading, Uploaded, Failed.
- **Failed** rows show a short plain reason ("Couldn't reach your desktop", "Desktop rejected the file") and a Retry action. A global "Sync now" action exists.
- **Tap a save → detail:** the full text, all files (viewable/playable), the note (editable), and a "Raw" section showing the full recorded intent and extras. The raw view is how Anuj and the phase-2 builder learn what each app actually sends.
- **Settings:** desktop address (Tailscale MagicDNS name or IP and port), pairing token, a "Test connection" button, last successful sync time.
- Editing a note after upload marks the capture as needing a re-sync of its note (a small update, not a re-upload of files).

**Visual direction (Anuj, 2026-10-26, after the design round):** iOS Liquid Glass is the inspiration for every screen: floating glass circle and capsule controls, large bold titles, inset grouped lists with large corner radii, glass sheets over vivid media, a floating glass sidebar on the inner screen. **Not Material Design**: no stock Material components, Material icons or Material colour schemes, even though this is an Android app. The capture sheet follows the approved POC (`docs/design/capture-poc.html`): a small, see-through white glass sheet low on the screen over the still-visible app. Reference boards: https://claude.ai/artifact/L8wXWK4Amuk5wryV4Az2g8 (capture row and app row; the desktop boards are not part of phase 1, since the receiver has no screen).

## 8. Sync: phone → desktop

- **Trigger:** on every new capture and note edit, enqueue unique WorkManager work with a network-connected constraint. Also a periodic daily job that uploads anything still pending. Failures retry with exponential backoff.
- **Unreachable desktop is normal**, not an error worth alarming about: it stays Waiting, then retries. Mark Failed only for errors that won't fix themselves (e.g. rejected by the desktop) or after a long period; exact thresholds are the builder's call.
- **Idempotent upload:** the capture UUID makes uploads safe to repeat. Before re-sending, the phone may ask the desktop whether it already has the capture (pattern from readeck-android's queue) to avoid re-uploading large files.
- **Confirmation:** the desktop replies "stored" only after the record and all files are durable and their SHA-256 hashes match. Only then does the phone mark the capture Uploaded.
- **Large files:** uploads must handle multi-hundred-MB videos without loading them into memory, and resume or restart cleanly if interrupted.
- **Auth:** a pairing token generated on the desktop, entered or scanned into the phone once, stored with Android Keystore encryption. Tailscale already limits who can reach the receiver; the token is defence in depth.
- **Phone storage** *(Claude's call)*: nothing is deleted from the phone in phase 1, even after upload. (A cleanup policy can come later.)

## 9. What to borrow (from the 2026-10-26 research)

Full reports and a dashboard of all 38 projects: `docs/research/` and https://claude.ai/artifact/FTqL7YZMDr8Y5o9iSQZZxy. No project is a wholesale fit for phase 1. Pieces worth using:

| Need | Source | How | License |
|---|---|---|---|
| Pull URL + highlighted quote out of shared text; background save with notification; local pending save | Pinkt (linkding Android client): `ExtractUrl`, share worker, `OfflineCopyStorage` | Copy with attribution | Apache-2.0 |
| Share intake rules: title from SUBJECT then TITLE, all streams, ignore re-deliveries from Recents | Joplin | Re-implement (and go further: also keep HTML text, ClipData, referrer) | AGPL (ideas only) |
| Durable offline queue; check-before-retry | readeck-android `DrainQueuedAddsWorker` | Re-implement | GPL-3.0 (ideas only) |
| Outbox sync: local UUID, needs-sync flag, compare-and-set | MoeMemosAndroid `SyncingRepository` | Re-implement | GPL-3.0 (ideas only) |
| Content-URI copy to app storage | Markor's fallback | Copy, but rewrite to stream (it reads whole files into memory) | Apache-2.0 / CC0 |
| Transparent activity + Compose sheet over the sharing app | Seal | Pattern; its `motion/` animation files are copyable | Apache-2.0 for `motion/` only |
| LAN/Tailscale server discovery, Keystore token, never re-send an upload | ArchiveBox Android | Copy patterns | MIT |
| Two share targets (silent vs. with UI) as toggleable aliases; `PROCESS_TEXT` | YTDLnis, Sesame | Ideas, not in phase 1 scope (see section 11) | GPL (ideas only) |

Not found anywhere: real glass / liquid UI code. That has to be built (Jetpack Compose + AGSL shaders), possibly on an existing Compose blur/backdrop library the builder evaluates. A planned research pass on glass/liquid libraries was cancelled; the builder should do that evaluation.

## 10. The desktop receiver

A small, always-running service on the home desktop (systemd user service) whose only job in phase 1 is to receive captures and store them exactly as they arrived.

- **API:** an upload endpoint taking a capture's record plus its files; a "do you have capture X / which of its files" check; a health/pairing check. Idempotent on capture UUID: a repeat upload of a stored capture returns success without duplicating anything.
- **Storage layout:** one folder per capture under a data root on the home volume, e.g. `~/yearn-data/captures/YYYY/MM/<capture-id>/` holding `record.json` (exactly as received, plus `received_at`) and `files/`. An append-only index (SQLite or JSONL) lists captures for phase 2. The folder must be enough to rebuild the index (ArchiveBox pattern).
- **Durability:** write to a temp location, verify hashes, fsync, atomic rename into place, then reply "stored".
- **Network:** bound to the Tailscale interface only (or localhost behind `tailscale serve`).
- **No processing** of captures in phase 1. Phase 2 will add fetching, enrichment and the mind map on top of this store.

## 11. Build order *(Claude's call)*

1. **Capture + local store + home-screen list** behind a plain, functional sheet. This proves no share is ever lost and starts collecting real raw intents from Anuj's apps immediately.
2. **Desktop receiver + sync over Tailscale.**
3. **The glass-water capture animation** as its own iterative loop: builds installed on Anuj's Fold8, he reviews each version on both screens and says when it feels right. Anuj is the judge of "feels like real water"; there is no automated pass/fail for it.

## 12. Left to the builder

- Android architecture and libraries (Compose, Room, WorkManager, HTTP client, DI), module layout, package name, minimum SDK (glass refraction needs API 33; cross-window blur API 31; the Fold8 is far above both, so the min SDK can be set for simplicity, degrading gracefully below 33 if lower).
- How exactly to achieve the liquid/glass effect, and which helper libraries to use, within the quality bar in section 6.
- Receiver language and framework. Suggestion: Python, since phase 2's tools (gallery-dl, yt-dlp) are Python, but not required.
- Upload protocol details (multipart vs. chunked, resumable or not), retry thresholds, wire format of the record.
- Thumbnail generation details and placeholder design.
- Pairing UX (typed token vs. QR code).
- Whether to add a second "silent" share target, a `PROCESS_TEXT` entry (save selected text), or a Quick Settings tile. Nice to have, not required for phase 1.
- Testing approach. Strongly suggested: a corpus of real raw intents captured from Instagram, X, Reddit, LinkedIn, GitHub, Chrome, WhatsApp and the gallery app, replayed in unit tests of the intent parser.
