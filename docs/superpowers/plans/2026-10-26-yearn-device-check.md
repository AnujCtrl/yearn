# yearn device check (Fold8)

Step 5 of Task 10 in `2026-10-26-yearn-android-app.md`. It needs the phone in hand and the receiver
from `2026-10-26-yearn-receiver.md` running on the desktop, so it was not run during the build. Tick
each box and write what you saw under it, including anything that failed.

**Setup**
- Install: `cd android && GRADLE_USER_HOME=~/.gradle-local ./gradlew :app:installDebug`.
- Desktop: `yearn-receiver` running and reachable over Tailscale.
- Logs while testing: `adb logcat -s yearn AndroidRuntime WM-WorkerWrapper`.

## 0. Pairing

- [ ] **Pair the phone with the desktop.**
  - How:
    - Run `yearn-receiver pair` on the desktop.
    - Open yearn. With no token saved, it opens on Settings by itself.
    - Check the address, which defaults to `http://desktop:8787`; change it if your tailnet name differs.
    - Tap **Pairing** and type the token. The field shows only its last four characters.
    - Press Done.
  - Pass:
    - The row reads `Paired · ••••` plus the token's last four characters.
    - **Test connection** reads `Reachable just now`.
    - A wrong token gives `Desktop didn't accept the pairing token`.
    - With the receiver stopped, it gives `Couldn't reach your desktop`.
- [ ] **KeystoreSecretBox seals and opens on the device.**
  - How: after pairing, force-stop yearn (Settings > Apps > yearn > Force stop), reopen it and go to Settings.
  - Pass:
    - Still `Paired · ••••xxxx`, and Test connection still says Reachable.
    - yearn does not open on Settings again, which would mean the token could not be opened.
    - `adb shell run-as com.anujp.yearn cat files/datastore/settings.preferences_pb | strings` does not contain the token.

## 1–8. The plan's checks

- [ ] **1. yearn is in the share sheet** of Instagram, X, Reddit, LinkedIn, GitHub, Chrome, WhatsApp and Gallery.
  - How: open the share sheet in each app, using a post, a link, a photo and a video.
  - Pass: yearn is listed in all eight. Note any app that hides it behind "More".
- [ ] **2. Share from each app; the Raw view shows the full intent.**
  - How:
    - Share from each app, open the save and tap **Raw share**.
    - Tap **Copy** and paste the JSON into `android/app/src/test/resources/corpus/<app>.json`, for example `instagram.json` or `chrome.json` (see that folder's README).
  - Pass:
    - One capture per share, with the right source label and title.
    - `extras` lists every key the app sent, including vendor keys.
    - Copy puts the whole record on the clipboard.
- [ ] **3. A 300 MB+ gallery video.**
  - How: share it from Gallery. Partway through the copy, press Home.
  - Pass:
    - The sheet stays up and shows progress until the copy ends.
    - After Home, the copy still finishes: the detail lists the file with its size, with no `Copy interrupted` error, and its thumbnail shows.
- [ ] **4. Fold and unfold mid-capture.**
  - How: start a share, type a note, then fold or unfold while the sheet is up.
  - Pass: one capture in the list (never two), and the note draft is still there.
- [ ] **5. Receiver stopped, then started.**
  - How:
    - Stop the receiver and share three things.
    - Start the receiver and tap **Sync now**.
  - Pass:
    - While stopped, the captures stay `Waiting` and never `Failed`.
    - After Sync now they become `Uploaded`.
    - On the desktop, `captures/<YYYY>/<MM>/<id>/files/` matches the files byte for byte (compare `sha256sum` with each file's `sha256` in the raw record).
- [ ] **6. Airplane mode.**
  - How: turn airplane mode on, share something, turn it off. Don't open yearn.
  - Pass: within a minute or two the capture is on the desktop. Opening yearn afterwards shows it `Uploaded`.
- [ ] **7. Battery saver and the sheet.**
  - How: turn battery saver on and share something.
  - Pass:
    - The capture sheet still looks deliberate: either a real blur or a solid frosted scrim.
    - No muddy half-transparent sheet over the source app.
- [ ] **8. Both screens look right.**
  - How: check the list, the detail and Settings on the cover screen (folded) and the inner screen, in light and in dark.
  - Pass:
    - They match the boards in `.superpowers/sdd/2026-10-26-yearn-android-app/boards/` (App-Home, App-Detail, App-Settings, App-Inner).
    - Back walks detail → list and Settings → previous screen.
    - Inner screen: the detail sits in the pane beside the floating sidebar, and Settings covers the whole window.

## Extra checks

- [ ] **Real image and video thumbnails.**
  - How: share a photo and a video from Gallery.
  - Pass:
    - The list shows real thumbnails, not letter tiles.
    - The detail shows the photo at full width.
    - For a video, the detail shows its frame thumbnail.
    - Tapping a file row (or the picture) opens it in the gallery or player through the FileProvider.
    - A file that failed to copy shows its error in red and does nothing when tapped.
- [ ] **Cross-window blur vs the battery-saver scrim.**
  - How: share once with battery saver off and once with it on. Also check Settings > Accessibility > Reduce transparency (One UI's "Reduce transparency and blur"), if set.
  - Pass:
    - With blur available, the capture sheet blurs the app behind it.
    - When One UI turns cross-window blur off, the sheet falls back to its scrim and stays readable.
    - Write down which one you saw.
- [ ] **In-app glass blur.**
  - How: scroll the list under the glass buttons and the capsule. Open a save with a photo.
  - Pass:
    - The buttons and capsule frost what scrolls under them.
    - The detail sheet frosts the photo, as on the board.
    - On the inner screen the sidebar frosts the detail's media.
- [ ] **Keep yearn awake.**
  - How: on a fresh install the row is highlighted in orange with the switch off. Tap it.
  - Pass:
    - The system asks to let yearn run in the background. After you allow it and come back, the row reads `Battery: Unrestricted`, the switch is on and the highlight is gone.
    - Tapping it again opens the battery optimisation list.
- [ ] **Note editing.**
  - How: on an `Uploaded` save, type a note and press Done. Edit it and press Back. Clear it and switch apps.
  - Pass: each change reaches the desktop's `notes.jsonl`, including the cleared one, and the capture stays `Uploaded`.
- [ ] **Raw share scrolls into view.**
  - How: on a long save, tap **Raw share**.
  - Pass: the sheet scrolls so the JSON block is visible. This scroll couldn't be checked under Robolectric.
- [ ] **Keyboard over the note.**
  - How: tap the note field in the detail sheet and on the inner screen.
  - Pass: the field stays above the keyboard.
