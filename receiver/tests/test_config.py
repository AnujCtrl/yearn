from pathlib import Path

import pytest

from yearn_receiver.config import (
    ConfigError,
    assert_safe_data_root,
    load_config,
    validate_bind_host,
)


def test_bind_rejects_all_interfaces():
    with pytest.raises(ConfigError):
        validate_bind_host("0.0.0.0")


def test_bind_rejects_lan_address():
    with pytest.raises(ConfigError):
        validate_bind_host("192.168.0.10")


def test_bind_accepts_tailscale_and_localhost():
    validate_bind_host("100.101.102.103")
    validate_bind_host("127.0.0.1")


MOUNTINFO = (
    "29 1 0:26 / / rw,relatime - btrfs /dev/mapper/root rw\n"
    "80 29 8:17 / /mnt/hdd1 rw,relatime - fuseblk /dev/sdb1 rw\n"
)


def test_data_root_on_ntfs_is_refused(tmp_path):
    with pytest.raises(ConfigError, match="ntfs|fuseblk"):
        assert_safe_data_root(Path("/mnt/hdd1/yearn"), MOUNTINFO)


def test_data_root_on_btrfs_home_is_fine():
    assert_safe_data_root(Path("/home/user/yearn-data"), MOUNTINFO)


def test_load_config_requires_long_token(tmp_path):
    p = tmp_path / "r.toml"
    p.write_text('host="127.0.0.1"\ntoken="short"\n')
    with pytest.raises(ConfigError, match="token"):
        load_config(p)
