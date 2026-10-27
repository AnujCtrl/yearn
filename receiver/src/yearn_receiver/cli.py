"""Command line: serve, pair, reindex."""

import argparse
import json
import os
import re
import secrets
import socket
import subprocess
import sys
import tomllib
from pathlib import Path

import uvicorn

from .app import create_app
from .config import DEFAULT_DATA_ROOT, DEFAULT_PORT, ConfigError, load_config
from .store import CaptureStore

DEFAULT_CONFIG = "~/.config/yearn/receiver.toml"


def _tailscale_ipv4() -> str:
    try:
        result = subprocess.run(
            ["tailscale", "ip", "-4"], capture_output=True, text=True, check=True
        )
    except (OSError, subprocess.CalledProcessError) as e:
        raise ConfigError(f"could not get the Tailscale address from `tailscale ip -4`: {e}") from None
    lines = result.stdout.strip().splitlines()
    if not lines:
        raise ConfigError("`tailscale ip -4` printed no address; is Tailscale up?")
    return lines[0].strip()


def _hostname() -> str:
    """This machine's name; with Tailscale MagicDNS the phone reaches it by that name."""
    return socket.gethostname().split(".")[0]


def _toml_str(value: str) -> str:
    # A JSON string is a valid TOML basic string.
    return json.dumps(value)


def _write_private(path: Path, text: str, *, replace: bool) -> None:
    """Write text to path, mode 0600 from the first byte."""
    target = path.with_name(path.name + ".tmp") if replace else path
    fd = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(fd, "w") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
        if replace:
            os.replace(target, path)
    except BaseException:
        if replace:
            target.unlink(missing_ok=True)
        raise


def _pair(path: Path) -> int:
    port = DEFAULT_PORT
    token = secrets.token_urlsafe(32)
    if path.exists():
        text = path.read_text()
        try:
            port = int(tomllib.loads(text).get("port", DEFAULT_PORT))
        except (ValueError, TypeError):
            pass  # a broken config is reported by `serve`; pair still rotates the token
        line = f"token = {_toml_str(token)}"
        if re.search(r"^token\s*=.*$", text, flags=re.M):
            text = re.sub(r"^token\s*=.*$", lambda _: line, text, count=1, flags=re.M)
        else:
            text = text.rstrip("\n") + "\n" + line + "\n"
        _write_private(path, text, replace=True)
    else:
        host = _tailscale_ipv4()
        path.parent.mkdir(parents=True, exist_ok=True)
        text = (
            f"host = {_toml_str(host)}\n"
            f"port = {DEFAULT_PORT}\n"
            f"data_root = {_toml_str(DEFAULT_DATA_ROOT)}\n"
            f"token = {_toml_str(token)}\n"
        )
        _write_private(path, text, replace=False)
    print(f"token: {token}")
    print(f"url:   http://{_hostname()}:{port}")
    return 0


def _run(args: argparse.Namespace) -> int:
    path = Path(args.config).expanduser()
    if args.command == "pair":
        return _pair(path)
    if not path.exists():
        raise ConfigError(f"no config at {path}; run `yearn-receiver pair` first")
    config = load_config(path)
    store = CaptureStore(config.data_root)
    if args.command == "reindex":
        print(store.reindex())
        return 0
    store.reindex()
    uvicorn.run(create_app(store, config.token), host=config.host, port=config.port)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="yearn-receiver")
    sub = parser.add_subparsers(dest="command", required=True)
    for name, help_text in (
        ("serve", "run the receiver"),
        ("pair", "create the config, or rotate its token"),
        ("reindex", "rebuild the index from the capture folders"),
    ):
        p = sub.add_parser(name, help=help_text)
        p.add_argument("--config", default=DEFAULT_CONFIG, help="config file (default: %(default)s)")
    args = parser.parse_args(argv)
    try:
        return _run(args)
    except ConfigError as e:
        print(f"yearn-receiver: {e}", file=sys.stderr)
        return 2
