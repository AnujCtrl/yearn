import stat
import tomllib

import pytest

from yearn_receiver import cli

TOKEN = "t" * 40


def write_config(path, data_root, token=TOKEN, extra=""):
    path.write_text(
        f'host = "127.0.0.1"\nport = 9999\ndata_root = "{data_root}"\n'
        f'token = "{token}"\n{extra}'
    )
    path.chmod(0o600)


def test_pair_creates_config_with_0600_and_long_token(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(cli, "_tailscale_ipv4", lambda: "100.101.102.103")
    monkeypatch.setattr(cli, "_hostname", lambda: "desk")
    cfg = tmp_path / "sub" / "receiver.toml"

    assert cli.main(["pair", "--config", str(cfg)]) == 0

    assert stat.S_IMODE(cfg.stat().st_mode) == 0o600
    data = tomllib.loads(cfg.read_text())
    assert data["host"] == "100.101.102.103"
    assert data["port"] == 8787
    assert len(data["token"]) >= 32
    out = capsys.readouterr().out
    assert data["token"] in out
    assert "http://desk:8787" in out


def test_pair_rotates_token_keeps_other_keys(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(cli, "_tailscale_ipv4", lambda: pytest.fail("must not be called"))
    monkeypatch.setattr(cli, "_hostname", lambda: "desk")
    cfg = tmp_path / "receiver.toml"
    write_config(cfg, tmp_path / "data", extra="# keep me\n")

    assert cli.main(["pair", "--config", str(cfg)]) == 0

    data = tomllib.loads(cfg.read_text())
    assert data["token"] != TOKEN and len(data["token"]) >= 32
    assert data["host"] == "127.0.0.1"
    assert data["port"] == 9999
    assert data["data_root"] == str(tmp_path / "data")
    assert "# keep me" in cfg.read_text()
    assert stat.S_IMODE(cfg.stat().st_mode) == 0o600
    out = capsys.readouterr().out
    assert data["token"] in out
    assert "http://desk:9999" in out and "8787" not in out


def test_reindex_prints_count(tmp_path, capsys):
    cfg = tmp_path / "receiver.toml"
    write_config(cfg, tmp_path / "data")
    (tmp_path / "data").mkdir()

    assert cli.main(["reindex", "--config", str(cfg)]) == 0

    assert capsys.readouterr().out.strip() == "0"


def test_serve_without_config_says_pair_first(tmp_path, capsys):
    assert cli.main(["serve", "--config", str(tmp_path / "missing.toml")]) == 2
    err = capsys.readouterr().err
    assert "yearn-receiver pair" in err
    assert "Traceback" not in err


def test_bad_config_is_one_line_exit_2(tmp_path, capsys):
    cfg = tmp_path / "receiver.toml"
    write_config(cfg, tmp_path / "data", token="short")
    assert cli.main(["reindex", "--config", str(cfg)]) == 2
    err = capsys.readouterr().err
    assert len(err.strip().splitlines()) == 1


def test_serve_reindexes_then_runs_uvicorn(tmp_path, monkeypatch):
    cfg = tmp_path / "receiver.toml"
    write_config(cfg, tmp_path / "data")
    (tmp_path / "data").mkdir()
    calls = []
    monkeypatch.setattr(cli.uvicorn, "run", lambda app, **kw: calls.append(kw))

    assert cli.main(["serve", "--config", str(cfg)]) == 0

    assert calls == [{"host": "127.0.0.1", "port": 9999}]


def test_serve_still_starts_with_junk_capture_folder(tmp_path, monkeypatch):
    cfg = tmp_path / "receiver.toml"
    write_config(cfg, tmp_path / "data")
    junk = tmp_path / "data" / "captures" / "2026" / "10" / "junk"
    junk.mkdir(parents=True)
    (junk / "record.json").write_text("not json")
    calls = []
    monkeypatch.setattr(cli.uvicorn, "run", lambda app, **kw: calls.append(kw))

    assert cli.main(["serve", "--config", str(cfg)]) == 0

    assert calls == [{"host": "127.0.0.1", "port": 9999}]
