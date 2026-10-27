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

## Final-review checks

Added with the final fix wave. Each one covers behaviour that Robolectric can't show on a real phone.

- [ ] **a. The daily job really runs on this phone.**
  - How:
    - Right after install and first launch: `adb shell dumpsys jobscheduler | grep -A5 yearn`.
    - In the evening, stop the receiver and share something. Leave the phone alone overnight without opening yearn.
    - In the morning, start the receiver and still don't open yearn.
  - Pass:
    - `dumpsys` lists jobs for `com.anujp.yearn/androidx.work.impl.background.systemjob.SystemJobService`. WorkManager doesn't show the unique-work name there, but one of them is periodic with a 24 h interval and a connectivity constraint.
    - The capture reaches the desktop by itself, through the retry backoff (at most about 5 h) or the daily job. Write down how long it took.
    - Opening yearn afterwards shows it `Uploaded`, and it never showed `Failed` overnight.
- [ ] **b. Tailscale off: nothing leaves, nothing fails.**
  - How:
    - Turn the Tailscale VPN off on the phone. On Wi-Fi, share two things and tap **Sync now**. Then repeat on mobile data with Wi-Fi off.
    - On Wi-Fi, also run this on the desktop while you sync: `sudo tcpdump -ni any 'tcp port 8787 and not net 100.64.0.0/10'`.
    - Turn Tailscale back on.
  - Pass:
    - While Tailscale is off, the captures stay `Waiting` and never turn `Failed`. **Test connection** reads `Couldn't reach your desktop`.
    - `tcpdump` shows no packet from the phone's LAN address: the tailnet guard refuses non-Tailscale addresses before connecting.
    - With Tailscale back on, Sync now uploads them.
- [ ] **c. Killed mid-copy.**
  - How: share a large video (300 MB+). While it copies, press Home, then run `adb shell am kill com.anujp.yearn`. Open yearn.
  - Pass:
    - The list has exactly one capture for that share.
    - The detail shows the file with `Copy interrupted: yearn was closed before this file finished copying` in red.
    - The capture still uploads: the desktop has its record with that file listed as an error.
- [ ] **d. A cloud-only Google Photos video.**
  - How: in Google Photos, share a video that is only in the cloud (not on the phone). While yearn copies, press Home and open another app for a minute.
  - Pass: write down what happened. Did the copy finish while yearn was cached in the background (file listed with its size), or did it end as `Copy interrupted`? Either way there must be exactly one capture, and it must upload.
- [ ] **e. Wrong token, then the right one.**
  - How: in Settings, enter a wrong pairing token and share two things. Wait until they show `Failed · Desktop didn't accept the pairing token`. Then enter the correct token.
  - Pass:
    - Saving the correct token puts both captures back to `Waiting` by itself, with no Retry tap, and they upload.
    - **Sync now** on the list also retries captures that failed on the token. Other failures, such as `Desktop rejected the file`, stay `Failed` until you tap Retry.
