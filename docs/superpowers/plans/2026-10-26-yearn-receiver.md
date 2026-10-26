# yearn desktop receiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A small always-on service on Anuj's desktop that receives yearn captures from his phone over Tailscale and stores each one durably, exactly as it arrived.

**Architecture:** One Python package. A `CaptureStore` owns all disk work (staging, hash checks, fsync, atomic rename, an append-only index). A thin Starlette app maps the HTTP contract below onto the store. A CLI runs it (`serve`, `pair`, `reindex`), and a systemd user unit keeps it running. No processing of captures: phase 2 builds on this store.

**Tech Stack:** Python 3.14 (system), uv, Starlette + uvicorn, pytest + anyio + httpx (tests).

**Spec:** `docs/superpowers/specs/2026-10-26-yearn-phase1-design.md` (sections 4, 8, 10). This plan also fixes the wire contract that the app plan (`2026-10-26-yearn-android-app.md`) consumes.

## Global Constraints

- Listen only on the desktop's Tailscale address (100.64.0.0/10) or 127.0.0.1 behind `tailscale serve`; never 0.0.0.0, the LAN or the public internet. This desktop's Tailscale IPv4 is `100.101.102.103` (MagicDNS name `desktop`). Default port: `8787`.
- Data root defaults to `~/yearn-data` on the home volume. Refuse to start if the data root is on `fuseblk`, `ntfs` or `ntfs3` (fsync through ntfs-3g is unreliable).
- Reply "stored" only after the record and every file are fsynced, their hashes verified, the directory fsynced, and the folder atomically renamed into place.
- Idempotent on capture UUID: a repeat of any request for an already-stored capture succeeds and changes nothing.
- `record.json` is written exactly as received plus one added key, `received_at`. It is never rewritten afterwards.
- The folder `captures/YYYY/MM/<id>/` is enough to rebuild the index (`reindex`).
- No third-party services, no telemetry.
- Large files are streamed: never hold a whole upload in memory.

## Wire contract (v1)

Every request carries `Authorization: Bearer <token>`. A missing or wrong token gets `401 {"error":"unauthorized"}`. `{id}` must be a UUID, else `400 {"error":"bad_id"}`.

| Request | Success | Errors |
|---|---|---|
| `GET /v1/health` | `200 {"service":"yearn-receiver","api":1}` | |
| `GET /v1/captures/{id}` | `200 {"id":id,"stored":bool,"staged":{"<index>":"<sha256>",…}}` (`staged` is `{}` once stored) | |
| `PUT /v1/captures/{id}/files/{index}`, header `X-Sha256: <lowercase hex>`, raw body | `201 {"index":n,"sha256":hex}`, or `200` with the same body if the capture is already stored | `422 {"error":"hash_mismatch","index":n}` (the staged file is deleted) |
| `POST /v1/captures/{id}/commit`, JSON body = capture record | `200 {"stored":true,"path":"captures/YYYY/MM/<id>"}` (same on a repeat) | `422 {"error":"bad_record"}`, `{"error":"id_mismatch"}`, `{"error":"missing_file","index":n}`, `{"error":"hash_mismatch","index":n}` |
| `PUT /v1/captures/{id}/note`, JSON `{"note":str,"note_updated_at":iso8601}` | `200 {"ok":true}` | `404 {"error":"not_stored"}` |

**Capture record (`schema_version` 1)**, written by the phone. The receiver validates only:
- `schema_version == 1`
- `id` equals the path id
- `captured_at` parses as ISO-8601 with an offset
- `files` is a list whose entries have an int `index`, a `display_name`, and either a 64-hex `sha256` or a non-empty `error`

Every other key is stored untouched. Phone-side keys:
- `intent`, `extras`, `clip_data`
- `source_hints`, `files`
- `note`, `note_updated_at`
- `device`

**On disk:**
- Staging: `<root>/.staging/<id>/files/<index>`.
- Committed: `<root>/captures/<YYYY>/<MM>/<id>/`, where YYYY/MM come from `captured_at`. It holds `record.json`, `files/<index:02d>-<safe display name>` (one per file without `error`) and `notes.jsonl`.
- Safe display name: keep `[A-Za-z0-9._-]`, replace every other run of characters with `_`, cut to 80 chars; empty becomes `file`.
- Index: `<root>/index.jsonl`, one line per committed capture: `{"id","captured_at","received_at","path"}`.

## Review Focus

- A repeated commit arriving while the first is half-done (phone retries after a timeout): the second must not corrupt or duplicate. Commit takes a per-id `asyncio.Lock`, and re-checks "already stored" inside it. → Task 2 test `test_concurrent_commits_store_once`.
- A desktop freeze mid-commit leaves a `.staging/<id>` folder or a half-renamed state: the next upload of the same id must still succeed. Stale staging is reused and overwritten, never trusted for "stored". → Task 2 test `test_leftover_staging_does_not_count_as_stored`.
- Display names with `../`, slashes, NUL or 300 characters must never escape the capture folder. → Task 2 test `test_display_name_cannot_escape`.
- A record whose `files` lists an index that was never uploaded (and has no `error`) must be rejected, not stored with a hole. → Task 2 test `test_commit_rejects_missing_file`.
- A config pointing the data root at an ntfs-3g mount, or binding 0.0.0.0, must refuse to start with a plain message. → Task 1 tests.

---

### Task 1: Project scaffold and config

**Files:**
- Create: `receiver/pyproject.toml`, `receiver/src/yearn_receiver/__init__.py`, `receiver/src/yearn_receiver/config.py`
- Test: `receiver/tests/test_config.py`

**Interfaces:**
- Produces:
  - `@dataclass(frozen=True) Config(data_root: Path, host: str, port: int, token: str)`
  - `class ConfigError(Exception)`
  - `load_config(path: Path) -> Config`. The TOML has keys `data_root` (default `~/yearn-data`, `~` expanded), `host` (required), `port` (default 8787) and `token` (required, at least 32 chars). It calls both validators below.
  - `validate_bind_host(host: str) -> None`: raises `ConfigError` unless the host is `127.0.0.1` or an IPv4 address inside `100.64.0.0/10`.
  - `assert_safe_data_root(path: Path, mountinfo: str | None = None) -> None`: parses `/proc/self/mountinfo` (or the given text), finds the longest mount point that is a prefix of the resolved path, and raises `ConfigError` if its fstype is `fuseblk`, `ntfs` or `ntfs3`.

- [ ] **Step 1: Create `receiver/pyproject.toml`**
  - uv project `yearn-receiver`, `requires-python = ">=3.12"`.
  - Dependencies: `starlette`, `uvicorn`.
  - Dev group: `pytest`, `anyio`, `httpx`.
  - Script entry: `yearn-receiver = "yearn_receiver.cli:main"`.
  - src layout. Run `cd receiver && uv sync`; expect it to succeed.

- [ ] **Step 2: Write failing tests**

```python
def test_bind_rejects_all_interfaces():
    with pytest.raises(ConfigError): validate_bind_host("0.0.0.0")
def test_bind_rejects_lan_address():
    with pytest.raises(ConfigError): validate_bind_host("192.168.0.10")
def test_bind_accepts_tailscale_and_localhost():
    validate_bind_host("100.101.102.103"); validate_bind_host("127.0.0.1")
MOUNTINFO = (
  "29 1 0:26 / / rw,relatime - btrfs /dev/mapper/root rw\n"
  "80 29 8:17 / /mnt/hdd1 rw,relatime - fuseblk /dev/sdb1 rw\n")
def test_data_root_on_ntfs_is_refused(tmp_path):
    with pytest.raises(ConfigError, match="ntfs|fuseblk"):
        assert_safe_data_root(Path("/mnt/hdd1/yearn"), MOUNTINFO)
def test_data_root_on_btrfs_home_is_fine():
    assert_safe_data_root(Path("/home/user/yearn-data"), MOUNTINFO)
def test_load_config_requires_long_token(tmp_path):
    p = tmp_path / "r.toml"; p.write_text('host="127.0.0.1"\ntoken="short"\n')
    with pytest.raises(ConfigError, match="token"): load_config(p)
```

- [ ] **Step 3: Run `cd receiver && uv run pytest tests/test_config.py -v`.** Expected: FAIL (module missing).
- [ ] **Step 4: Implement `config.py`** with the interfaces above. Use `tomllib` and `ipaddress`.
- [ ] **Step 5: Run the same command.** Expected: 6 passed.
- [ ] **Step 6: Commit**

```bash
git add receiver && git commit -m "feat(receiver): project scaffold and validated config"
```

### Task 2: CaptureStore (staging, commit, notes, reindex)

**Files:**
- Create: `receiver/src/yearn_receiver/store.py`
- Test: `receiver/tests/test_store.py`

**Interfaces:**
- Produces:
  - `@dataclass CaptureStatus(stored: bool, staged: dict[int, str])`
  - `class RecordRejected(Exception)` with attributes `error: str` (one of `bad_record`, `id_mismatch`, `missing_file`, `hash_mismatch`) and `index: int | None`.
  - `class HashMismatch(RecordRejected)`
  - `class NotStored(Exception)`
  - `class CaptureStore(root: Path)` with these methods:
    - `status(capture_id: str) -> CaptureStatus`
    - `async stage_file(capture_id: str, index: int, chunks: AsyncIterator[bytes], expected_sha256: str) -> str`
      - Streams the body to `.staging/<id>/files/<index>.part`, hashing as it goes.
      - Then fsync and rename to `<index>`.
      - On a mismatch, deletes the file and raises `HashMismatch`.
      - If the capture is already stored, drains the body and returns `expected_sha256`.
    - `async commit(capture_id: str, record: dict) -> str`
      - Returns a path relative to the root, e.g. `captures/2026/10/<id>`.
      - Works under a per-id `asyncio.Lock`.
      - Already stored: returns the existing path.
      - Validates the record (contract above) and re-hashes every staged file.
      - Writes `record.json` (record plus `received_at`, local time with offset) into staging.
      - Moves each staged file to `files/<NN>-<safe name>`.
      - fsyncs the files, `files/` and the staging dir, then `os.rename`s the staging dir to its final place.
      - fsyncs the parent dirs, appends to `index.jsonl` and fsyncs it.
    - `append_note(capture_id: str, note: str, note_updated_at: str) -> None`
      - Appends `{"note","note_updated_at","received_at"}` to `notes.jsonl` and fsyncs.
      - Raises `NotStored` if the capture isn't committed.
    - `reindex() -> int`: rewrites `index.jsonl` atomically by scanning `captures/*/*/*/record.json`; returns the count.
  - `safe_name(display_name: str) -> str` (the rule in the contract)

Test helpers: `rec(id, files)` builds a minimal valid record; `async def body(b: bytes)` yields `b` in 3 chunks. Tests are `@pytest.mark.anyio`.

- [ ] **Step 1: Write failing tests**

```python
async def test_stage_then_commit_stores_record_and_files(tmp_path):
    s = CaptureStore(tmp_path); cid = str(uuid4()); data = b"x" * 100_000
    await s.stage_file(cid, 0, body(data), sha(data))
    path = await s.commit(cid, rec(cid, [{"index": 0, "display_name": "clip.mp4", "sha256": sha(data)}]))
    folder = tmp_path / path
    assert path.startswith("captures/2026/10/")
    assert (folder / "files" / "00-clip.mp4").read_bytes() == data
    stored = json.loads((folder / "record.json").read_text())
    assert stored["id"] == cid and "received_at" in stored
    assert s.status(cid) == CaptureStatus(stored=True, staged={})
    assert json.loads((tmp_path / "index.jsonl").read_text().splitlines()[0])["id"] == cid
async def test_stage_hash_mismatch_deletes_part(tmp_path):
    s = CaptureStore(tmp_path); cid = str(uuid4())
    with pytest.raises(HashMismatch): await s.stage_file(cid, 0, body(b"abc"), "0" * 64)
    assert s.status(cid).staged == {}
async def test_commit_rejects_missing_file(tmp_path):
    s = CaptureStore(tmp_path); cid = str(uuid4())
    with pytest.raises(RecordRejected) as e:
        await s.commit(cid, rec(cid, [{"index": 0, "display_name": "a", "sha256": "a" * 64}]))
    assert e.value.error == "missing_file" and e.value.index == 0
async def test_commit_accepts_file_with_error_and_no_upload(tmp_path):
    s = CaptureStore(tmp_path); cid = str(uuid4())
    await s.commit(cid, rec(cid, [{"index": 0, "display_name": "a", "error": "permission denied"}]))
    assert s.status(cid).stored
async def test_commit_rejects_id_mismatch(tmp_path): ...       # error == "id_mismatch"
async def test_repeat_commit_is_noop(tmp_path): ...            # same path, index.jsonl still 1 line, record.json mtime unchanged
async def test_concurrent_commits_store_once(tmp_path):        # asyncio.gather two commits -> same path, 1 index line
    ...
async def test_leftover_staging_does_not_count_as_stored(tmp_path):
    # create .staging/<id>/files/0 by hand -> status(cid).stored is False; a fresh stage + commit succeeds
    ...
def test_display_name_cannot_escape():
    assert safe_name("../../etc/passwd") == "etc_passwd"   # leading dots and slashes collapse, no ".."
    assert "/" not in safe_name("a/b\x00c") and len(safe_name("x" * 300)) == 80 and safe_name("") == "file"
def test_append_note_requires_stored(tmp_path): ...            # NotStored, then after commit one line in notes.jsonl
def test_reindex_rebuilds_from_folders(tmp_path): ...          # delete index.jsonl -> reindex() == 1, line restored
```

`rec()` fixes `captured_at="2026-10-26T12:41:07+05:30"`. `safe_name` also strips leading `.` and `_` so the result never starts with a dot.

- [ ] **Step 2: Run `uv run pytest tests/test_store.py -v`.** Expected: FAIL (module missing).
- [ ] **Step 3: Implement `store.py`.** Read the request body in chunks of up to 1 MiB. Use `os.fsync` on file descriptors and on directory fds opened with `os.open(dir, os.O_RDONLY)`. YYYY/MM come from `datetime.fromisoformat(captured_at)`.
- [ ] **Step 4: Run the same command.** Expected: all pass.
- [ ] **Step 5: Commit**

```bash
git add receiver && git commit -m "feat(receiver): durable capture store with staging, commit, notes, reindex"
```

### Task 3: HTTP app

**Files:**
- Create: `receiver/src/yearn_receiver/app.py`
- Test: `receiver/tests/test_app.py`

**Interfaces:**
- Consumes: `CaptureStore` and its errors (Task 2).
- Produces: `create_app(store: CaptureStore, token: str) -> Starlette`. Implements the wire contract table exactly. Token comparison uses `hmac.compare_digest`.

- [ ] **Step 1: Write failing tests.** Use `httpx.AsyncClient(transport=httpx.ASGITransport(app))` and a 40-char token.

```python
async def test_requires_token(client_no_auth): assert (await client_no_auth.get("/v1/health")).status_code == 401
async def test_health(client): assert (await client.get("/v1/health")).json() == {"service": "yearn-receiver", "api": 1}
async def test_bad_id_is_400(client): assert (await client.get("/v1/captures/not-a-uuid")).status_code == 400
async def test_full_upload_flow(client):
    cid = str(uuid4()); data = os.urandom(300_000)
    r = await client.put(f"/v1/captures/{cid}/files/0", content=data, headers={"X-Sha256": sha(data)})
    assert r.status_code == 201
    assert (await client.get(f"/v1/captures/{cid}")).json()["staged"] == {"0": sha(data)}
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, [{"index": 0, "display_name": "v.mp4", "sha256": sha(data)}]))
    assert r.status_code == 200 and r.json()["stored"] is True
    assert (await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, [...]))).status_code == 200   # idempotent
    assert (await client.put(f"/v1/captures/{cid}/files/0", content=data, headers={"X-Sha256": sha(data)})).status_code == 200
async def test_hash_mismatch_is_422(client): ...   # body {"error": "hash_mismatch", "index": 0}
async def test_commit_bad_json_is_422_bad_record(client): ...
async def test_note_before_commit_is_404_then_200_after(client): ...
```

- [ ] **Step 2: Run `uv run pytest tests/test_app.py -v`.** Expected: FAIL.
- [ ] **Step 3: Implement `app.py`.**
  - Starlette routes plus one auth middleware.
  - The file PUT passes `request.stream()` to `stage_file`.
  - Map the store errors to the contract's status codes and bodies.
  - A missing `X-Sha256` header is `400 {"error":"missing_sha256"}`.
- [ ] **Step 4: Run the same command, then `uv run pytest`.** Expected: all pass.
- [ ] **Step 5: Commit**

```bash
git add receiver && git commit -m "feat(receiver): HTTP API over the capture store"
```

### Task 4: CLI, systemd unit, live check

**Files:**
- Create: `receiver/src/yearn_receiver/cli.py`, `receiver/deploy/yearn-receiver.service`, `receiver/README.md`
- Test: `receiver/tests/test_cli.py`

**Interfaces:**
- Consumes: `load_config` (Task 1), `CaptureStore` (Task 2), `create_app` (Task 3).
- Produces: `main(argv: list[str] | None = None) -> int` with these subcommands. All take `--config PATH`, default `~/.config/yearn/receiver.toml`.
  - `serve`: load the config and run uvicorn on `config.host:config.port`.
  - `pair`:
    - If the config doesn't exist, create it (mode 0600) with `host` from `tailscale ip -4` (first line), `port` 8787, `data_root` `~/yearn-data` and a new `secrets.token_urlsafe(32)` token.
    - If it exists, rotate only the token.
    - Print the token and `http://desktop:8787` for the phone.
  - `reindex`: print the count.

- [ ] **Step 1: Write failing tests**
  - `test_pair_creates_config_with_0600_and_long_token(tmp_path, monkeypatch)`: monkeypatch the `tailscale ip` call to return `100.101.102.103`. Assert the file mode is `0o600`, the token is at least 32 chars and `host == "100.101.102.103"`.
  - `test_pair_rotates_token_keeps_other_keys`.
  - `test_reindex_prints_count`.
- [ ] **Step 2: Run `uv run pytest tests/test_cli.py -v`.** Expected: FAIL.
- [ ] **Step 3: Implement `cli.py` with argparse.** Write the unit file:
  - `ExecStart=%h/Documents/personal/yearn/receiver/.venv/bin/yearn-receiver serve`
  - `Restart=on-failure`
  - `After=tailscaled.service network-online.target`
  - `WantedBy=default.target`

  In the README, describe:
  - install: `uv sync` plus a symlink of the unit into `~/.config/systemd/user/`
  - `systemctl --user enable --now yearn-receiver`
  - `yearn-receiver pair`
  - "the data root must stay on the home volume"
- [ ] **Step 4: Run `uv run pytest`.** Expected: all pass.
- [ ] **Step 5: Live check on the desktop (no system change beyond the user unit; ask Anuj before enabling the unit).**
  - Run `uv run yearn-receiver pair`, then `uv run yearn-receiver serve`.
  - In another shell: `curl -s -H "Authorization: Bearer $TOKEN" http://desktop:8787/v1/health`. Expected: `{"service":"yearn-receiver","api":1}`.
  - `curl -s http://127.0.0.1:8787/v1/health` must fail to connect, since it is bound to the Tailscale IP only.
- [ ] **Step 6: Commit**

```bash
git add receiver && git commit -m "feat(receiver): CLI (serve, pair, reindex) and systemd user unit"
```
