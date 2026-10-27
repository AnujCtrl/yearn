# yearn

Save anything from your phone's share sheet, in full, to your own desktop.

- **Android app** (`android/`): shows up in every app's share sheet, saves the
  raw share (text, links, every extra, the files themselves) on the phone first,
  then uploads it in the background. iOS-glass style UI, built for the Galaxy Z
  Fold's two screens.
- **Desktop receiver** (`receiver/`): a small Python service, reached only over
  Tailscale, that stores each capture durably as a folder under `~/yearn-data`.
  Runs in Docker; see `receiver/README.md`.

## Install the app

Releases are signed APKs built by GitHub Actions. Add this repo's URL to
[Obtainium](https://github.com/ImranR98/Obtainium) to install and update it.

## Set it up

1. On the desktop: `cd receiver && uv run yearn-receiver pair`. It prints a
   pairing token and the address to use.
2. Start the receiver (`receiver/README.md`).
3. On the phone: open yearn. Settings opens by itself; type the desktop address
   and the token, then tap **Test connection**.

Both devices must be on the same Tailscale tailnet. The app refuses to send
anything to an address outside it.
