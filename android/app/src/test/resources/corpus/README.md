# Raw share corpus

These are real share intents as yearn recorded them, one JSON file per source app. This is the corpus that spec §12 suggests for testing the intent parser against real shares instead of hand-made intents.

**What goes here**
- One file per app: `instagram.json`, `x.json`, `reddit.json`, `linkedin.json`, `github.json`, `chrome.json`, `whatsapp.json` and `gallery.json`.
- If one app shares in more than one shape (a post vs a profile, a photo vs a video), add a suffix: `instagram-reel.json`, `gallery-video.json`.

**How the files are collected**
- During the device run (`docs/superpowers/plans/2026-10-26-yearn-device-check.md`, item 2):
  1. Share from the app to yearn.
  2. Open the save and tap **Raw share**, then **Copy**.
  3. Paste the JSON here unchanged.

**What a file contains**
- The full capture record, exactly as the desktop receives it: `schema_version`, `intent`, `extras`, `clip_data`, `source_hints`, `device`, `files`, `note`.
- Content URIs in it are only strings. Nothing is replayed against a real provider.

**Privacy**
- Before committing a file, read it. Replace anything private, such as WhatsApp message text or phone numbers, with a placeholder of the same type and shape.
- Don't remove keys. The whole point is that every key the app sends is present.

The folder is empty until the device run.
