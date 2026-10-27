# yearn receiver

A small Python service on the desktop that durably stores captures sent from
the phone over Tailscale. It listens only on the Tailscale address (or
127.0.0.1) and answers "stored" only after everything is fsynced and verified.

It runs as a Docker container with host networking, restarted by Docker
(`restart: unless-stopped`), so it comes back after a reboot as long as the
Docker service is enabled.

## Pair

Pairing runs on the host, because it asks `tailscale ip -4` for the address:

    cd receiver
    uv sync
    uv run yearn-receiver pair

The first run creates `~/.config/yearn/receiver.toml` (mode 0600) with the
Tailscale address, port 8787, data root `~/yearn-data` and a new token. It
prints the token and `http://<this machine's name>:8787` for the phone (the
name MagicDNS gives it; the Tailscale IP works too). Type both into the app's
Settings. Running it again rotates only the token; restart the container
afterwards.

## Run

Tell Compose where your home is and who you are (`.env` is gitignored), and
create the data root first so Docker doesn't create it owned by root:

    printf 'YEARN_HOME=%s\nYEARN_UID=%s\nYEARN_GID=%s\n' "$HOME" "$(id -u)" "$(id -g)" > .env
    mkdir -p ~/yearn-data
    sudo docker compose up -d --build

The container mounts `~/.config/yearn` (read-only) and `~/yearn-data` at the
same paths inside, and runs as your uid. Logs: `sudo docker logs -f
yearn-receiver`. After rotating the token: `sudo docker compose restart`.

## Data root

The data root must be on a native Linux filesystem. The receiver refuses to
start if it is on an NTFS or fuseblk mount: fsync through ntfs-3g can't back
its "stored" guarantee.

## Reindex

The capture folders under `captures/` are the source of truth. To rebuild the
index from them:

    sudo docker compose run --rm yearn-receiver reindex

`serve` does this once at start-up too.

## Tests

    uv run pytest
