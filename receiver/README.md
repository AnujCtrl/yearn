# yearn receiver

A small Python service on the desktop that durably stores captures sent from
the phone over Tailscale. It listens only on the Tailscale address (or
127.0.0.1) and answers "stored" only after everything is fsynced and verified.

## Install

    cd receiver
    uv sync
    mkdir -p ~/.config/systemd/user
    ln -s "$PWD/deploy/yearn-receiver.service" ~/.config/systemd/user/

## Pair

    uv run yearn-receiver pair

The first run creates `~/.config/yearn/receiver.toml` (mode 0600) with the
Tailscale address, port 8787, data root `~/yearn-data` and a new token. It
prints the token and `http://desktop:8787` for the phone. Running it again
rotates only the token; restart the service afterwards.

## Enable

    systemctl --user enable --now yearn-receiver

## Data root

The data root must stay on the home volume. The receiver refuses to start
if it is on an NTFS or fuseblk mount (fsync through ntfs-3g is unreliable).

## Reindex

The capture folders under `captures/` are the source of truth. To rebuild the
index from them:

    uv run yearn-receiver reindex

`serve` does this once at start-up too.
