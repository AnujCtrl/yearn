# yearn receiver

A small Python service on the desktop that durably stores captures sent from
the phone over Tailscale, or straight over your home Wi-Fi. It listens on every
address (`0.0.0.0`), requires the pairing token on every request, and answers
"stored" only after everything is fsynced and verified.

It runs as a Docker container with host networking, restarted by Docker
(`restart: unless-stopped`), so it comes back after a reboot as long as the
Docker service is enabled.

## Pair

Pairing runs on the host, so it can see the machine's name and home address:

    cd receiver
    uv sync
    uv run yearn-receiver pair

The first run creates `~/.config/yearn/receiver.toml` (mode 0600) with host
`0.0.0.0`, port 8787, data root `~/yearn-data` and a new token. It prints the
token, the Tailscale address `http://<this machine's name>:8787` (the name
MagicDNS gives it; the Tailscale IP works too) and, when the machine is on a
home network, the home Wi-Fi address `http://<its LAN IP>:8787`. Type them into
the app's Settings; the home address is optional. Running it again rotates only
the token; restart the container afterwards.

For the home address, let the port through the firewall from your home network
only (ufw shown), and reserve the desktop's IP in your router so it doesn't
change:

    sudo ufw allow from 192.168.0.0/16 to any port 8787 proto tcp

`host` may also be a Tailscale address or `127.0.0.1` to listen on that alone.

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
