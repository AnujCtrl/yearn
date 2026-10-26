# yearn Android app (phase 1, build steps 1–2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that shows up in every app's share sheet, saves the full raw share on the phone before anything else happens, lists the saves with their sync status, and uploads them to the desktop receiver over Tailscale.

**Architecture:** One `app` module, split into packages by job:
- `capture/` parses shares, copies files and runs the share sheet.
- `data/` is a Room database and the capture record.
- `sync/` holds the HTTP client, the uploader, WorkManager and settings.
- `ui/` holds the screens and the glass components.

A transparent `ShareActivity` writes the row first, then streams files into app storage while a plain glass sheet shows progress. WorkManager uploads with the receiver's v1 contract. The liquid glass-water animation is not in this plan: it is build step 3, a separate review loop on the Fold8.

**Tech Stack:**
- Kotlin, Jetpack Compose, Room (KSP), WorkManager, OkHttp, kotlinx.serialization, DataStore
- Haze for backdrop blur (Apache-2.0)
- Inter font (OFL), bundled
- Tests: JUnit4, Robolectric, MockWebServer, kotlinx-coroutines-test, work-testing

**Spec:** `docs/superpowers/specs/2026-10-26-yearn-phase1-design.md`. The wire contract is in `docs/superpowers/plans/2026-10-26-yearn-receiver.md` § "Wire contract (v1)": the uploader implements exactly that table.

## Global Constraints

- Package `com.anujp.yearn`. Project root `android/`.
- Build versions:
  - AGP 9.0.0, Gradle 9.3.1 (both already cached in `~/.gradle`)
  - `compileSdk`/`targetSdk` 36, `minSdk` 33
  - Gradle runs on JDK 21: `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk` in `gradle.properties` (the system default JDK 26 is too new for AGP)
  - SDK at `/home/user/Android/Sdk` (`local.properties`, gitignored)
- Library versions: the latest stable at implementation time, pinned in `gradle/libs.versions.toml`.
- Accept `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for `*/*`.
- Persist first: the capture row is committed before the first file byte is copied and before the sheet shows "Saved".
- Never read a whole file into memory, neither when copying nor when uploading.
- No network call anywhere in the share path. Uploads happen only in WorkManager.
- Nothing leaves the phone except to the user-configured receiver URL. No analytics or crash-reporting SDKs.
- Status words are exactly `Waiting`, `Uploading`, `Uploaded`, `Failed`.
- Failure reasons are exactly:
  - `Couldn't reach your desktop`
  - `Desktop rejected the file`
  - `Desktop didn't accept the pairing token`
  - `File missing on this phone`
  - `Desktop not set up yet`
- Visual direction: iOS Liquid Glass inspiration, **no Material look**:
  - no Material 3 components on screen (no `Scaffold`/`TopAppBar`/`Card`/`ListItem`/`FloatingActionButton`), no Material icons, no dynamic colour
  - Compose `foundation` + own glass components; `material3` may be a dependency only for `Text`/ripple plumbing if needed
  - Reference: https://claude.ai/artifact/L8wXWK4Amuk5wryV4Az2g8 (app row) and `docs/design/capture-poc.html`
- Copied code keeps its license header and source URL in a comment (spec §9). GPL/AGPL projects: ideas only.

## Review Focus

- **Killed mid-copy.** The process dies after the row commits but before a file finishes copying. The URI grant is gone, so the capture must still exist and be uploadable, with that file marked as an error. → Task 3 test `markInterruptedCopies_turnsPendingIntoErrorsAndCompletesCapture`.
- **No duplicate captures.** A share re-delivered from Recents, or an activity recreated by folding/unfolding, must not create a second capture. → Task 6 tests `launchedFromHistory_capturesNothing` and `recreate_keepsSingleCapture`.
- **Desktop off for days.** The capture stays `Waiting` (never `Failed`) and keeps being retried. → Task 7 test `unreachable_staysWaitingAndAsksForRetry`.
- **Share with nothing usable** (no text, no stream, unknown extras). It is still recorded with the raw intent and shown in the list. → Task 2 test `emptyShare_stillParses` and Task 5 test `emptyShare_createsCaptureWithNoFiles`.
- **One unreadable file in a multi-share** (permission denied or deleted). The other files still copy; that one is recorded with its error and the upload still commits. → Task 4 test `securityException_isRecordedAsFailure` and Task 7 test `fileWithCopyError_isListedNotUploaded`.

Test command used throughout: `cd android && ./gradlew :app:testDebugUnitTest --tests '<class>'`.

---

### Task 1: Project scaffold with share target

**Files:**
- Create:
  - `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/.gitignore`
  - `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`
  - `android/app/src/main/java/com/anujp/yearn/YearnApp.kt`, `MainActivity.kt`, `capture/ShareActivity.kt`
  - `android/app/src/main/res/values/themes.xml`
- Test: `android/app/src/test/java/com/anujp/yearn/ShareTargetManifestTest.kt`

**Interfaces:**
- Produces:
  - Manifest: `ShareActivity` exported, with intent filters `SEND` and `SEND_MULTIPLE` for `*/*`, category `DEFAULT`, `android:theme="@style/Theme.Yearn.Capture"`, `android:excludeFromRecents="true"`, `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density"`, and `launchMode="standard"`.
  - Manifest: `MainActivity` is the launcher.
  - `Theme.Yearn.Capture`: translucent window, no title, transparent background, no animation (`windowAnimationStyle` null).
  - The app class `YearnApp`.

- [ ] **Step 1: Generate the wrapper.** Run `~/.gradle/wrapper/dists/gradle-9.3.1-bin/*/gradle-9.3.1/bin/gradle -p android wrapper --gradle-version 9.3.1`. Expected: `android/gradlew` exists.
- [ ] **Step 2: Write the build files and the stub activities.**
  - Plugins: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`.
  - AGP 9 has Kotlin built in, so don't apply `kotlin-android`.
  - Turn on `testOptions.unitTests.isIncludeAndroidResources = true` for Robolectric.
  - Add every library from the tech stack now, so later tasks only add code.
- [ ] **Step 3: Write the failing test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ShareTargetManifestTest {
    private fun resolves(action: String, type: String) =
        ApplicationProvider.getApplicationContext<Context>().packageManager
            .queryIntentActivities(Intent(action).setType(type), 0)
            .any { it.activityInfo.name == ShareActivity::class.java.name }
    @Test fun acceptsTextImageVideoAndAnything() {
        assertTrue(resolves(Intent.ACTION_SEND, "text/plain"))
        assertTrue(resolves(Intent.ACTION_SEND, "video/mp4"))
        assertTrue(resolves(Intent.ACTION_SEND_MULTIPLE, "image/*"))
        assertTrue(resolves(Intent.ACTION_SEND, "application/x-unknown"))
    }
}
```

- [ ] **Step 4: Run it.** Expected: it passes once the manifest is right. If it passes before Step 2's filters exist, the test is wrong: fix it.
- [ ] **Step 5: `./gradlew :app:assembleDebug`.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit**

```bash
git add android && git commit -m "feat(app): scaffold with share target for SEND and SEND_MULTIPLE */*"
```

### Task 2: Raw share parser

**Files:**
- Create: `android/app/src/main/java/com/anujp/yearn/capture/RawShareParser.kt`, `capture/SourceHints.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/capture/RawShareParserTest.kt`

**Interfaces:**
- Produces:
  - `data class SourceHints(val referrer: String?, val extraReferrer: String?, val extraReferrerName: String?, val launchedFromPackage: String?, val launchedFromUid: Int?, val callingPackage: String?)`
  - `data class SharePreview(val title: String?, val text: String?, val url: String?, val sourceLabel: String)`
  - `data class ParsedShare(val intent: JsonObject, val extras: JsonObject, val clipData: JsonObject?, val sourceHints: JsonObject, val streamUris: List<Uri>, val preview: SharePreview)`
  - `object RawShareParser { fun parse(intent: Intent, hints: SourceHints): ParsedShare; fun firstUrl(text: String): String? }`

**Rules:**
- **`intent`:** `{action, type, categories:[…], flags:Int, data:String?}`.
- **`extras`:** every key from `intent.extras`, as `{"type": T, "value": …}`. T is one of:
  - `String`, `CharSequence`, `String[]`, `ArrayList<String>`
  - `Int`, `Long`, `Boolean`, `Double`, `Float`
  - `Uri`, `ArrayList<Uri>`, `Bundle` (value is a nested extras object)
  - `unknown`: value is `{"class_name", "to_string"}`
  
  A `Spanned` CharSequence also gets an `"html"` value from `Html.toHtml(spanned, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)`.
- **`clipData`:** `{description:{mime_types:[…], label}, items:[{text, html_text, uri, intent}]}`, or null.
- **`streamUris`:** `EXTRA_STREAM` (single or list), then the ClipData item URIs. Duplicates removed, first-seen order kept.
- **`preview.title`:** `EXTRA_SUBJECT`, then `EXTRA_TITLE`.
- **`preview.text`:** `EXTRA_TEXT` with the first URL removed and whitespace trimmed; null if empty.
- **`firstUrl`:** the regex `https?://[^\s<>"]+`, then strip trailing `.,;:!?)]'"`.
- **`sourceLabel`:**
  - the URL host without a leading `www.`
  - else the `launchedFromPackage`/`referrer` package
  - else `Shared files` when there are streams, `Shared text` when there is text, otherwise `Shared something`

- [ ] **Step 1: Write failing tests** (Robolectric):
  - `instagramLink_textPlainUrlOnly`: `EXTRA_TEXT="https://www.instagram.com/reel/abc/?igsh=x"` gives `url` = that string, `sourceLabel="instagram.com"`, `text=null`, extras `android.intent.extra.TEXT.type=="String"`.
  - `xPost_wordsPlusUrl`: text `"Liquid glass in Compose https://x.com/shaderjune/status/1."` gives `url` without the trailing dot and `text="Liquid glass in Compose"`.
  - `chrome_subjectBecomesTitle`: SUBJECT plus TEXT url gives `title` = the subject.
  - `galleryMultiple_dedupesStreamAndClipData`: `SEND_MULTIPLE` with 3 URIs in both `EXTRA_STREAM` and ClipData gives `streamUris.size==3` in order, and `sourceLabel=="Shared files"`.
  - `styledText_keepsHtml`: a `SpannableString` with a `StyleSpan(BOLD)` gives extras `type=="CharSequence"` and an `html` containing `<b>`.
  - `nestedBundleAndUnknownParcelable`: a Bundle extra recurses; a `Rect` extra gives `type=="unknown"` and `class_name=="android.graphics.Rect"`.
  - `emptyShare_stillParses`: `Intent(ACTION_SEND).setType("*/*")` gives empty extras, `clipData==null`, `sourceLabel=="Shared something"`.
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.capture.RawShareParserTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.** Use `bundle.keySet()` + `@Suppress("DEPRECATION") bundle.get(key)`, since a raw dump has to see every type.
- [ ] **Step 4: Run it.** Expected: 7 passed.
- [ ] **Step 5: Commit** `feat(app): raw share parser keeps every extra, clip item and hint`.

### Task 3: Local database and capture record

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/data/`: `Entities.kt`, `CaptureDao.kt`, `YearnDatabase.kt`, `CaptureRecord.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/data/CaptureDaoTest.kt`, `CaptureRecordTest.kt`

**Interfaces:**
- Produces:
  - `enum class SyncStatus { WAITING, UPLOADING, UPLOADED, FAILED }` and `enum class FileState { PENDING, DONE, ERROR }`, with converters.
  - `@Entity("captures") data class CaptureEntity(@PrimaryKey val id: String, val capturedAt: String, val capturedAtMillis: Long, val baseRecordJson: String, val sourceLabel: String, val previewTitle: String?, val previewText: String?, val previewUrl: String?, val thumbPath: String?, val note: String?, val noteUpdatedAt: String?, val noteNeedsSync: Boolean, val filesComplete: Boolean, val syncStatus: SyncStatus, val attempts: Int, val lastError: String?, val uploadedAt: String?)`
  - `@Entity("files", primaryKeys=["captureId","index"]) data class FileEntity(val captureId: String, val index: Int, val sourceUri: String, val displayName: String?, val mimeType: String?, val size: Long?, val localPath: String?, val sha256: String?, val error: String?, val state: FileState)`
  - `CaptureDao`:
    - `@Transaction suspend fun insertCapture(c: CaptureEntity, files: List<FileEntity>)`
    - `fun observeAll(): Flow<List<CaptureEntity>>` (newest first by `capturedAtMillis`)
    - `fun observe(id: String): Flow<CaptureEntity?>`
    - `suspend fun get(id: String): CaptureEntity?`
    - `suspend fun files(id: String): List<FileEntity>`
    - `suspend fun updateFile(f: FileEntity)`
    - `suspend fun setFilesComplete(id: String, thumbPath: String?)`
    - `suspend fun setSync(id: String, status: SyncStatus, attempts: Int, lastError: String?, uploadedAt: String?)`
    - `suspend fun setNote(id: String, note: String, noteUpdatedAt: String)` (also sets `noteNeedsSync = 1`)
    - `suspend fun clearNoteNeedsSync(id: String)`
    - `suspend fun pendingUploads(): List<CaptureEntity>` (`filesComplete` and (`syncStatus` in WAITING, UPLOADING or `noteNeedsSync`))
    - `@Transaction suspend fun markInterruptedCopies(): Int`
  - `YearnDatabase.create(context): YearnDatabase` (file `yearn.db`). `exportSchema = true`, with the schema dir `android/app/schemas`.
  - `object CaptureRecord`:
    - `fun base(id: String, capturedAt: String, parsed: ParsedShare, device: JsonObject): JsonObject` gives `{schema_version:1, id, captured_at, intent, extras, clip_data, source_hints, device}`.
    - `fun full(base: JsonObject, files: List<FileEntity>, note: String?, noteUpdatedAt: String?): JsonObject` adds `files:[{index, display_name, mime_type, size, sha256}` or `{…, error}]`, `note` and `note_updated_at`.
- Constant: the interrupted-copy error text is `Copy interrupted: yearn was closed before this file finished copying`.

- [ ] **Step 1: Write failing tests** (Robolectric, in-memory Room):
  - `observeAll_newestFirst`
  - `pendingUploads_skipsIncompleteAndUploaded`
  - `setNote_marksNeedsSync`
  - `markInterruptedCopies_turnsPendingIntoErrorsAndCompletesCapture`: a capture with one DONE and one PENDING file gives a return value of 1, the PENDING file becomes ERROR with the constant text, the capture becomes `filesComplete`, and it appears in `pendingUploads()`.
  - `CaptureRecordTest.full_matchesReceiverContract`: `schema_version==1`, every files entry has an int `index`, a `display_name`, and exactly one of `sha256` (64 hex) or `error`. `captured_at` matches `\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d[+-]\d\d:\d\d`.
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.data.*'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.**
- [ ] **Step 4: Run it.** Expected: all pass.
- [ ] **Step 5: Commit** `feat(app): Room store and versioned capture record`.

### Task 4: Streaming file copier and thumbnails

**Files:**
- Create: `android/app/src/main/java/com/anujp/yearn/capture/FileCopier.kt`, `capture/Thumbnails.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/capture/FileCopierTest.kt`

**Interfaces:**
- Produces:
  - `interface Copier { suspend fun copy(captureId: String, index: Int, uri: Uri, onBytes: (Long) -> Unit = {}): CopyResult }`, which `FileCopier` implements. Later tasks depend on `Copier`.
  - `sealed interface CopyResult`
    - `data class Done(val localPath: String, val displayName: String?, val mimeType: String?, val size: Long, val sha256: String)`
    - `data class Failed(val displayName: String?, val error: String)`
  - `class FileCopier(private val resolver: ContentResolver, private val capturesDir: File)` with `suspend fun copy(captureId: String, index: Int, uri: Uri, onBytes: (Long) -> Unit = {}): CopyResult`
    - Runs on `Dispatchers.IO` with a 64 KiB buffer, hashing as it reads.
    - Writes `capturesDir/<id>/<index>.part`, then renames it to `<index>`. On failure no `.part` file is left.
    - Display name and size come from `OpenableColumns`; the MIME type from `resolver.getType`.
    - `SecurityException` gives `"No permission to read this file"`. A missing file or I/O error gives `"Couldn't read this file: <message>"`.
  - `object Thumbnails { fun make(source: File, mimeType: String?, out: File): File? }`
    - `image/*`: `ImageDecoder` with a target size whose long edge is ≤ 512 px, saved as JPEG at quality 85.
    - `video/*`: `MediaMetadataRetriever.getFrameAtTime(1_000_000)`, scaled the same way.
    - Anything else: null. Any exception: null.

- [ ] **Step 1: Write failing tests.** Use `shadowOf(resolver).registerInputStream(uri, stream)`.
  - `copiesFiveMegabytesWithCorrectHash`
  - `failedCopy_leavesNoPartFile`: the stream throws `IOException` midway.
  - `securityException_isRecordedAsFailure`: the error text is exactly `No permission to read this file`.
  - `thumbnails_nonMediaReturnsNull`
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.capture.FileCopierTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.** Use `DigestInputStream(SHA-256)`, then `fos.fd.sync()` before the rename.
- [ ] **Step 4: Run it.** Expected: 4 passed. (Image and video thumbnails are verified on the device in Task 10.)
- [ ] **Step 5: Commit** `feat(app): streaming content-URI copier with SHA-256 and thumbnails`.

### Task 5: Capture service (persist first)

**Files:**
- Create: `android/app/src/main/java/com/anujp/yearn/capture/CaptureService.kt`, `sync/SyncScheduler.kt` (interface only here)
- Modify: `YearnApp.kt` (holds the singletons: db, copier, service, scheduler)
- Test: `android/app/src/test/java/com/anujp/yearn/capture/CaptureServiceTest.kt`

**Interfaces:**
- Consumes: `RawShareParser.parse`, `CaptureDao`, `CaptureRecord.base`, `FileCopier.copy`, `Thumbnails.make`.
- Produces:
  - `interface SyncScheduler { fun enqueueNow(); fun ensureDaily() }`
  - `data class CaptureProgress(val captureId: String, val preview: SharePreview, val thumbPath: String?, val filesTotal: Int, val filesDone: Int, val bytesCopied: Long, val bytesTotal: Long?, val complete: Boolean)`
  - `class CaptureFailed(cause: Throwable) : Exception(cause)`
  - `fun deviceJson(context: Context): JsonObject` gives `{app_version: versionName, android_version: Build.VERSION.RELEASE, sdk: SDK_INT, model: Build.MODEL}`.
  - `class CaptureService(dao: CaptureDao, copier: Copier, scheduler: SyncScheduler, thumbsDir: File, device: JsonObject, clock: () -> ZonedDateTime = { ZonedDateTime.now() }, newId: () -> String = { UUID.randomUUID().toString() })` with:
    - `suspend fun begin(intent: Intent, hints: SourceHints): CaptureProgress`. It parses, builds the base record, and inserts the capture (WAITING, `filesComplete` = no streams) with every stream file PENDING. Then it returns. That return is the "caught" moment. A database error is wrapped in `CaptureFailed`.
    - `suspend fun copyFiles(captureId: String, onProgress: (CaptureProgress) -> Unit)`. It copies the files one at a time and updates each file row. It thumbnails the first file that yields one, then calls `setFilesComplete`, then `scheduler.enqueueNow()` exactly once.
    - `suspend fun saveNote(captureId: String, note: String)`: calls `dao.setNote` with the timestamp from `clock`, then `scheduler.enqueueNow()`.

- [ ] **Step 1: Write failing tests** (in-memory Room, a fake `Copier`, and a fake scheduler):
  - `begin_commitsRowBeforeAnyCopy`: the fake copier asserts `dao.get(id) != null` when called.
  - `textShare_isCompleteAtBeginAndEnqueuesOnce`
  - `copyFiles_updatesRowsThumbnailsAndEnqueuesOnce`
  - `emptyShare_createsCaptureWithNoFiles`
  - `begin_wrapsDatabaseErrorInCaptureFailed`: close the db first.
  - `saveNote_setsNeedsSyncAndEnqueues`
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.capture.CaptureServiceTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.**
  - In `YearnApp.onCreate`, call `dao.markInterruptedCopies()` (on an app-scope coroutine) and then `scheduler.ensureDaily()`.
  - Until Task 8, `YearnApp` wires an `object NoopSyncScheduler : SyncScheduler` (in `sync/SyncScheduler.kt`). Task 8 swaps it for `WorkSyncScheduler` and deletes the no-op.
  - Text-only captures enqueue in `begin`.
- [ ] **Step 4: Run it.** Expected: 6 passed.
- [ ] **Step 5: Commit** `feat(app): capture service commits the share before copying files`.

### Task 6: ShareActivity and the plain glass sheet

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/capture/`: `CaptureViewModel.kt`, `CaptureSheet.kt`
- Modify: `capture/ShareActivity.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/capture/ShareActivityTest.kt`

**Interfaces:**
- Consumes: `CaptureService` (Task 5), via `YearnApp`.
- Produces:
  - `CaptureViewModel(service)` holds `state: StateFlow<SheetState>`.
  - `sealed interface SheetState`, with these cases:
    - `Catching`
    - `Saving(progress)`
    - `Saved(progress)`
    - `Note(progress, draft)`
    - `StorageError(message)`
    - `Finished`

**Behaviour:**
- `onCreate`:
  - If `intent.flags` has `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, `finish()` without capturing.
  - The ViewModel starts the capture once, guarded by a `started` flag in the ViewModel, so a recreate never captures twice.
  - Build `SourceHints` from:
    - `referrer?.toString()`
    - the `Intent.EXTRA_REFERRER` Uri as a string
    - `getStringExtra(Intent.EXTRA_REFERRER_NAME)`
    - `launchedFromPackage` and `launchedFromUid` (API 34+, else null)
    - `callingPackage`
    
    Each is wrapped in `runCatching`, so a missing hint is null, never a crash.
- The window draws behind a cross-window blur:
  - `window.addFlags(FLAG_BLUR_BEHIND)`, `attributes.blurBehindRadius = 40` (spec §6).
  - If `windowManager.isCrossWindowBlurEnabled` is false, use a scrim of `#9E0E0822` instead (spec §6 fallback, and the "blur off" board).
- **The sheet**, per `docs/design/capture-poc.html` at real size:
  - bottom-aligned, 14 dp side and 18 dp bottom insets, 40 dp corner radius
  - fill white 14 % alpha, 1 dp white 30 % border, top inner highlight
  - thumbnail 84×112 dp at 18 dp radius
  - `"<sourceLabel>"` at 17 sp semibold, then the URL and two lines of text at 14 sp, 80 % alpha
  - a 5 dp progress bar
  - `Saved to yearn` at 18 sp semibold, plus the note dot: a 34 dp ring with a 12 dp dot, touch target 48 dp
  - Inter font
- **Timing:**
  - `Saved` holds 1000 ms, then `Finished` calls `finish()` with no exit animation.
  - Tapping the note dot shows `Note`: a field plus a `Done` button. `Done` saves the note, then finishes.
  - A downward drag over 80 dp finishes, but only in `Saved`.
- **Back:**
  - In `Saving`, the activity stays and the progress line reads `Still saving…`.
  - In `Note`, Back saves the draft and finishes.
- **Storage error:** the sheet shows `Couldn't save on this phone. Trying again…` and retries `begin` every 2 s, keeping the intent in memory.

- [ ] **Step 1: Write failing tests** (Robolectric `ActivityScenario` with a share Intent; the app's service is backed by an in-memory db):
  - `textShare_createsOneCaptureAndFinishes`: advance the main looper by 1100 ms. Expect one row, and the activity finishing.
  - `launchedFromHistory_capturesNothing`
  - `recreate_keepsSingleCapture`: `scenario.recreate()` while in `Saving`. Expect one row.
  - `backWhileSaving_doesNotFinish`: a fake copier suspends until released.
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.capture.ShareActivityTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.** Build the sheet only with Compose `foundation` (`Box`, `Column`, `BasicText`, `BasicTextField`).
- [ ] **Step 4: Run it.** Expected: 4 passed.
- [ ] **Step 5: Early blur check on the device** (spec §4 asks for this early):
  - Run `./gradlew :app:installDebug` against the Fold8 (wireless debugging via `adb connect`, if Anuj has it on).
  - Share an Instagram reel and check: is the app behind blurred, or did the scrim fallback show?
  - Note the answer in the commit message. If the device isn't reachable, write `blur check pending` instead.
- [ ] **Step 6: Commit** `feat(app): share sheet saves first, glass sheet per POC, back/fold safe`.

### Task 7: Receiver client and uploader

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/sync/`: `ReceiverClient.kt`, `CaptureUploader.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/sync/CaptureUploaderTest.kt`

**Interfaces:**
- Consumes: the wire contract v1; `CaptureDao`; `CaptureRecord.full`.
- Produces:
  - `sealed class ReceiverError : Exception()`:
    - `Unreachable(cause)` for an `IOException` or a 5xx
    - `Unauthorized` for a 401
    - `Rejected(error: String, index: Int?)` for a 422
    - `NotStored` for a 404 on a note
  - `data class RemoteStatus(val stored: Boolean, val staged: Map<Int, String>)`
  - `class ReceiverClient(baseUrl: HttpUrl, token: String, http: OkHttpClient)` with:
    - `suspend fun health()`
    - `suspend fun status(id: String): RemoteStatus`
    - `suspend fun putFile(id: String, index: Int, file: File, sha256: String)`: the body is `file.asRequestBody(mime)`, which streams
    - `suspend fun commit(id: String, record: JsonObject): String`
    - `suspend fun putNote(id: String, note: String, noteUpdatedAt: String)`
  - `data class UploadOutcome(val uploaded: Int, val retry: Boolean)`
  - `class CaptureUploader(dao: CaptureDao, client: suspend () -> ReceiverClient?, clock: () -> ZonedDateTime)` with `suspend fun uploadPending(): UploadOutcome`. For each `pendingUploads()` capture, in order:
    1. If the client is null, set WAITING with `Desktop not set up yet` and return `retry=false`.
    2. Set UPLOADING and call `status`.
    3. If not stored: PUT each DONE file whose staged hash differs, then commit `CaptureRecord.full(…)`.
    4. Set UPLOADED with `uploadedAt`, then clear `noteNeedsSync` (the commit carried the note).
    5. If it was already stored and `noteNeedsSync` is set: `putNote`, then clear it.
  - Error handling:
    - `Unreachable` sets WAITING with `Couldn't reach your desktop`, adds 1 to `attempts`, and sets `retry=true`.
    - `Unauthorized` sets FAILED with `Desktop didn't accept the pairing token`.
    - `Rejected` sets FAILED with `Desktop rejected the file`.
    - A DONE file whose `localPath` no longer exists sets FAILED with `File missing on this phone`.

- [ ] **Step 1: Write failing tests** (MockWebServer with a dispatcher that mimics the receiver, and in-memory Room):
  - `happyPath_statusPutCommit_endsUploaded`: with a 3 MB file. Assert the request order and the `X-Sha256` header.
  - `alreadyStored_skipsUploadAndMarksUploaded`
  - `stagedWithSameHash_skipsPut`
  - `unauthorized_marksFailedWithReason`
  - `rejected_marksFailedWithReason`
  - `unreachable_staysWaitingAndAsksForRetry`: shut the server down first.
  - `noteEditedAfterUpload_sendsNoteOnly`
  - `fileWithCopyError_isListedNotUploaded`: no PUT for that index, and the commit body's files entry has `error`.
  - `notConfigured_staysWaitingNoRetry`
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.sync.CaptureUploaderTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement it.** OkHttp timeouts: connect 10 s, read/write 120 s. Make the calls with `withContext(Dispatchers.IO)`.
- [ ] **Step 4: Run it.** Expected: 9 passed.
- [ ] **Step 5: Commit** `feat(app): v1 receiver client and idempotent uploader`.

### Task 8: Background sync, settings and token storage

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/sync/`: `SyncWorker.kt`, `WorkSyncScheduler.kt`, `SettingsStore.kt`, `TokenVault.kt`
- Modify: `YearnApp.kt` (wire the real scheduler and the client factory)
- Test: `android/app/src/test/java/com/anujp/yearn/sync/SyncSchedulingTest.kt`, `SettingsStoreTest.kt`

**Interfaces:**
- Consumes: `CaptureUploader` (Task 7), `SyncScheduler` (Task 5).
- Produces:
  - `class WorkSyncScheduler(context) : SyncScheduler`:
    - `enqueueNow()`: unique work `yearn-sync-now`, `ExistingWorkPolicy.APPEND_OR_REPLACE`, `NetworkType.CONNECTED`, exponential backoff starting at 30 s.
    - `ensureDaily()`: unique periodic work `yearn-sync-daily`, every 24 h, `KEEP`, CONNECTED.
  - `class SyncWorker : CoroutineWorker`: runs `uploader.uploadPending()`. Returns `Result.retry()` if `outcome.retry`, otherwise `success`. On success with at least one upload, writes `lastSuccessfulSync`.
  - `interface SecretBox { fun seal(plain: ByteArray): ByteArray; fun open(sealed: ByteArray): ByteArray }`. `KeystoreSecretBox` uses AES/GCM/NoPadding with the AndroidKeyStore alias `yearn_token_key` and a 12-byte IV prepended.
  - `class SettingsStore(context, box: SecretBox)`:
    - `val baseUrl: Flow<String?>` (default `http://desktop:8787`)
    - `suspend fun setBaseUrl(String)`
    - `suspend fun setToken(String)`
    - `suspend fun token(): String?`
    - `val lastSuccessfulSync: Flow<String?>`
    - `suspend fun setLastSuccessfulSync(String)`
    
    It uses DataStore preferences, and stores the token only as a base64 sealed blob.

- [ ] **Step 1: Write failing tests:**
  - `enqueueNow_requiresNetworkAndIsUnique` (`WorkManagerTestInitHelper`, inspect `getWorkInfosForUniqueWork`)
  - `ensureDaily_isPeriodicAndKept`
  - `worker_returnsRetryWhenUploaderAsks` (`TestListenableWorkerBuilder` plus a fake uploader)
  - `token_isNeverStoredInPlainText`: with a fake `SecretBox` that XORs. Assert the raw prefs file doesn't contain the token string, and `token()` round-trips.
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.sync.*'`.** Expected: the new tests FAIL.
- [ ] **Step 3: Implement it.** Use a custom `WorkerFactory` from `YearnApp` (`Configuration.Provider`) so the worker gets the uploader.
- [ ] **Step 4: Run it.** Expected: all sync tests pass.
- [ ] **Step 5: Commit** `feat(app): WorkManager sync on connect + daily, Keystore-sealed pairing token`.

### Task 9: Glass components, saves list and inner-screen layout

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/ui/`:
  - `glass/Glass.kt`: `GlassCircleButton`, `GlassCapsule`, `InsetGroup`, `LargeTitle`, `YearnType` (Inter), `YearnColors` (light/dark)
  - `home/HomeViewModel.kt`, `home/HomeScreen.kt`, `YearnRoot.kt`
- Create: `android/app/src/main/res/font/inter_variable.ttf`, plus `LICENSE-Inter.txt` beside it (OFL)
- Modify: `MainActivity.kt`
- Test: `android/app/src/test/java/com/anujp/yearn/ui/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `CaptureDao.observeAll`, `SyncScheduler.enqueueNow`, `CaptureDao.setSync`.
- Produces:
  - `data class SaveRow(val id: String, val title: String, val subtitle: String, val thumbPath: String?, val status: String, val failedReason: String?, val hasNote: Boolean)`
  - `HomeViewModel(dao, scheduler, clock)`:
    - `rows: StateFlow<List<SaveRow>>`
    - `waitingCount: StateFlow<Int>`
    - `fun syncNow()`
    - `fun retry(id: String)`: FAILED becomes WAITING, `attempts` 0, `lastError` null, then `enqueueNow`
  - `fun relativeTime(then: ZonedDateTime, now: ZonedDateTime): String`:
    - `now` under 1 min
    - `N min` under 1 h
    - `N h` under 24 h
    - `Yesterday`
    - otherwise `MMM d`

**Look** (app row of the design board):
- Large bold title `Saves` (34 sp, −0.02 em).
- Glass circle buttons (44 dp) for sync and settings.
- Rows in inset groups with a 26 dp radius and 0.5 dp separators.
- A floating glass capsule at the bottom: `N waiting for your desktop` plus a blue `Sync now` (`#0A84FF`). It is hidden when nothing is waiting.
- Glass is Haze backdrop blur:
  - light: white 32 %, 0.5 dp white 75 % border, top highlight
  - dark: white 14 %, 0.5 dp white 35 % border
- Follow the system dark setting.
- A save with no thumbnail (link or text) gets a tile tinted from a hash of its `sourceLabel`, with the label's first letter. No favicon fetch: no network in phase 1 beyond the receiver.

**Inner screen:** at widths of 600 dp or more, show the list in a floating glass sidebar (284 dp wide, 32 dp radius, 12 dp inset) over the detail pane. Task 10 fills the detail pane; until then it shows the selected row's title.

- [ ] **Step 1: Write failing tests:**
  - `relativeTime_buckets`: covers all five branches, plus the boundaries at 59 min and 23 h.
  - `rows_mapStatusWordsAndReasons`: FAILED with `lastError` gives `status=="Failed"` and the reason; UPLOADING gives `"Uploading"`.
  - `retry_resetsFailedAndEnqueues`
  - `waitingCount_countsWaitingAndUploading`
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.ui.HomeViewModelTest'`.** Expected: FAIL.
- [ ] **Step 3: Implement the ViewModel, then the glass components and screens.** Use no `androidx.compose.material3` composables on screen (Global Constraints).
- [ ] **Step 4: Run it.** Expected: 4 passed. `./gradlew :app:assembleDebug` succeeds.
- [ ] **Step 5: Commit** `feat(app): glass saves list with sync status, two-pane on the inner screen`.

### Task 10: Detail and settings screens, then the device run

**Files:**
- Create in `android/app/src/main/java/com/anujp/yearn/ui/`: `detail/DetailViewModel.kt`, `detail/DetailScreen.kt`, `settings/SettingsViewModel.kt`, `settings/SettingsScreen.kt`
- Modify: `ui/YearnRoot.kt` (navigation: list → detail → settings; the inner screen shows detail in the pane)
- Modify: `AndroidManifest.xml` (add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `INTERNET`, `ACCESS_NETWORK_STATE`)
- Test: `android/app/src/test/java/com/anujp/yearn/ui/DetailViewModelTest.kt`, `SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `CaptureDao`, `CaptureService.saveNote`, `CaptureRecord.full`, `SettingsStore`, `ReceiverClient.health`.
- Produces:
  - `DetailViewModel(id, dao, service)` with `capture: StateFlow<CaptureEntity?>`, `files: StateFlow<List<FileEntity>>`, `rawJson: StateFlow<String>` (pretty-printed `CaptureRecord.full`) and `fun saveNote(text: String)`.
  - `SettingsViewModel(store, clientFactory, power: () -> Boolean)` with:
    - `fun testConnection()`, which sets `connection: StateFlow<String>` to one of: `Reachable`, `Couldn't reach your desktop`, `Desktop didn't accept the pairing token`, `Desktop not set up yet`
    - `batteryUnrestricted: StateFlow<Boolean>`

**Screens:**
- Detail, per the "one save" board:
  - the media fills the top, with glass back and share/more circles
  - a frosted glass sheet with source and time, the full text, the note field, the files list (tap opens with `ACTION_VIEW` via a `FileProvider`)
  - rows: `Link` (opens the URL), `On your desktop` (status), and `Raw share` (expands the pretty JSON with a Copy button)
- Settings, per the settings board:
  - Address, Pairing token (typed in; shown masked)
  - Test connection, Last successful sync
  - `Keep yearn awake` (opens `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
  - Daily sync (a text line)
- First launch: if the address or token is missing, open Settings; if battery isn't unrestricted, show the `Keep yearn awake` row highlighted.

- [ ] **Step 1: Write failing tests:**
  - `saveNote_afterUpload_marksNoteNeedsSync`
  - `rawJson_includesEveryExtraKey`
  - `testConnection_mapsErrorsToCopy`: all four outcomes, with a fake client.
- [ ] **Step 2: Run `--tests 'com.anujp.yearn.ui.*'`.** Expected: the new tests FAIL.
- [ ] **Step 3: Implement it.**
- [ ] **Step 4: Run the whole suite: `./gradlew :app:testDebugUnitTest`.** Expected: all pass.
- [ ] **Step 5: Device run on the Fold8**, with the receiver from the receiver plan running. Check each item and record the result in `docs/superpowers/plans/2026-10-26-yearn-device-check.md`:
  1. yearn appears in the share sheet of Instagram, X, Reddit, LinkedIn, GitHub, Chrome, WhatsApp and Gallery.
  2. Share from each one. A capture appears, and Raw shows the full intent. Copy the raw JSON of each into `android/app/src/test/resources/corpus/<app>.json` (the spec §12 corpus).
  3. A 300 MB+ gallery video: the sheet stays until the copy finishes. Pressing Home mid-copy still finishes the copy.
  4. Fold and unfold mid-capture: one capture, the note draft kept.
  5. With the receiver stopped: captures stay `Waiting`. Start it and tap Sync now: they become `Uploaded`, and the files on the desktop match.
  6. Turn on airplane mode, share, turn it off: the upload happens without opening the app.
  7. Turn on battery saver: the sheet looks deliberate (blur or scrim).
  8. Both screens: list, detail and settings look right.
- [ ] **Step 6: Commit** `feat(app): detail with raw view and note editing, settings with pairing and battery`, plus the device-check notes and corpus.

---

**After this plan:** build step 3, the liquid glass-water capture animation (spec §6 quality bar), gets its own plan. It replaces Task 6's static sheet with one continuous AGSL glass shape, and is reviewed by Anuj on the Fold8 version by version.
