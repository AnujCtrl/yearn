"""Receiver configuration and the start-up safety checks."""

import ipaddress
import re
import tomllib
from dataclasses import dataclass
from pathlib import Path

DEFAULT_DATA_ROOT = "~/yearn-data"
DEFAULT_PORT = 8787
MIN_TOKEN_LENGTH = 32
UNSAFE_FSTYPES = frozenset({"fuseblk", "ntfs", "ntfs3"})
TAILSCALE_NET = ipaddress.ip_network("100.64.0.0/10")


class ConfigError(Exception):
    """The configuration is unusable; the message is shown to the user."""


@dataclass(frozen=True)
class Config:
    data_root: Path
    host: str
    port: int
    token: str


def validate_bind_host(host: str) -> None:
    try:
        addr = ipaddress.IPv4Address(host)
    except ValueError:
        raise ConfigError(
            f"host {host!r} is not an IPv4 address; use 127.0.0.1 or the "
            "Tailscale address (100.64.0.0/10)"
        ) from None
    if addr.is_loopback and host == "127.0.0.1":
        return
    if addr in TAILSCALE_NET:
        return
    raise ConfigError(
        f"refusing to listen on {host}: only 127.0.0.1 or a Tailscale "
        "address (100.64.0.0/10) is allowed"
    )


def _unescape_mount_path(raw: str) -> str:
    return re.sub(r"\\([0-7]{3})", lambda m: chr(int(m.group(1), 8)), raw)


def assert_safe_data_root(path: Path, mountinfo: str | None = None) -> None:
    if mountinfo is None:
        mountinfo = Path("/proc/self/mountinfo").read_text()
    resolved = path.expanduser().resolve(strict=False)

    best_mount: Path | None = None
    best_fstype = ""
    for line in mountinfo.splitlines():
        left, sep, right = line.partition(" - ")
        fields = left.split()
        if not sep or len(fields) < 5 or not right.split():
            continue
        mount = Path(_unescape_mount_path(fields[4]))
        if resolved != mount and mount not in resolved.parents:
            continue
        if best_mount is None or len(mount.parts) >= len(best_mount.parts):
            best_mount, best_fstype = mount, right.split()[0]

    if best_fstype in UNSAFE_FSTYPES:
        raise ConfigError(
            f"data root {resolved} is on a {best_fstype} mount ({best_mount}); "
            "NTFS drives freeze this machine under load. Use the home volume."
        )


def load_config(path: Path) -> Config:
    try:
        with open(path, "rb") as f:
            raw = tomllib.load(f)
    except FileNotFoundError:
        raise ConfigError(f"config file not found: {path}") from None
    except tomllib.TOMLDecodeError as e:
        raise ConfigError(f"config file {path} is not valid TOML: {e}") from None

    host = raw.get("host")
    if not isinstance(host, str) or not host:
        raise ConfigError("config: 'host' is required")
    token = raw.get("token")
    if not isinstance(token, str) or len(token) < MIN_TOKEN_LENGTH:
        raise ConfigError(f"config: 'token' is required and must be at least {MIN_TOKEN_LENGTH} characters")
    port = raw.get("port", DEFAULT_PORT)
    if isinstance(port, bool) or not isinstance(port, int) or not 1 <= port <= 65535:
        raise ConfigError("config: 'port' must be an integer between 1 and 65535")
    data_root = Path(raw.get("data_root", DEFAULT_DATA_ROOT)).expanduser()

    validate_bind_host(host)
    assert_safe_data_root(data_root)
    return Config(data_root=data_root, host=host, port=port, token=token)
