import asyncio
import json
import os
from uuid import uuid4

import pytest

from yearn_receiver.store import (
    CaptureStatus,
    CaptureStore,
    HashMismatch,
    NotStored,
    RecordRejected,
    safe_name,
)
from helpers import body, rec, sha

pytestmark = pytest.mark.anyio


def one_file(data=b"hello", name="a.txt"):
    return [{"index": 0, "display_name": name, "sha256": sha(data)}]


async def test_stage_then_commit_stores_record_and_files(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    data = b"x" * 100_000
    await s.stage_file(cid, 0, body(data), sha(data))
    assert s.status(cid) == CaptureStatus(stored=False, staged={0: sha(data)})
    path = await s.commit(cid, rec(cid, [{"index": 0, "display_name": "clip.mp4", "sha256": sha(data)}]))
    folder = tmp_path / path
    assert path.startswith("captures/2026/10/")
    assert (folder / "files" / "00-clip.mp4").read_bytes() == data
    assert sorted(p.name for p in (folder / "files").iterdir()) == ["00-clip.mp4"]
    stored = json.loads((folder / "record.json").read_text())
    assert stored["id"] == cid and "received_at" in stored
    assert stored["received_at"][-6] in "+-"  # local time with offset
    assert s.status(cid) == CaptureStatus(stored=True, staged={})
    assert not (tmp_path / ".staging" / cid).exists()
    lines = (tmp_path / "index.jsonl").read_text().splitlines()
    assert json.loads(lines[0])["id"] == cid
    assert json.loads(lines[0])["path"] == path


async def test_stage_hash_mismatch_deletes_part(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    with pytest.raises(HashMismatch) as e:
        await s.stage_file(cid, 0, body(b"abc"), "0" * 64)
    assert e.value.error == "hash_mismatch" and e.value.index == 0
    assert s.status(cid).staged == {}
    assert list((tmp_path / ".staging" / cid / "files").iterdir()) == []


async def test_stage_for_stored_capture_writes_nothing(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.commit(cid, rec(cid, [{"index": 0, "display_name": "a", "error": "gone"}]))
    assert await s.stage_file(cid, 3, body(b"abc"), sha(b"abc")) == sha(b"abc")
    assert not (tmp_path / ".staging" / cid).exists()


async def test_commit_rejects_missing_file(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    with pytest.raises(RecordRejected) as e:
        await s.commit(cid, rec(cid, [{"index": 0, "display_name": "a", "sha256": "a" * 64}]))
    assert e.value.error == "missing_file" and e.value.index == 0
    assert not s.status(cid).stored


async def test_commit_rejects_hash_mismatch_of_staged_file(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.stage_file(cid, 0, body(b"abc"), sha(b"abc"))
    with pytest.raises(HashMismatch) as e:
        await s.commit(cid, rec(cid, one_file(b"different")))
    assert e.value.index == 0
    assert not s.status(cid).stored


async def test_commit_rehashes_even_if_file_changed_after_staging(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.stage_file(cid, 0, body(b"abc"), sha(b"abc"))
    (tmp_path / ".staging" / cid / "files" / "0").write_bytes(b"corrupt")
    with pytest.raises(HashMismatch):
        await s.commit(cid, rec(cid, one_file(b"abc")))


async def test_commit_accepts_file_with_error_and_no_upload(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    path = await s.commit(cid, rec(cid, [{"index": 0, "display_name": "a", "error": "permission denied"}]))
    assert s.status(cid).stored
    assert not (tmp_path / path / "files").exists() or list((tmp_path / path / "files").iterdir()) == []


async def test_commit_rejects_id_mismatch(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    with pytest.raises(RecordRejected) as e:
        await s.commit(cid, rec(str(uuid4()), []))
    assert e.value.error == "id_mismatch"


@pytest.mark.parametrize(
    "mutate",
    [
        lambda r: r.update(schema_version=2),
        lambda r: r.pop("schema_version"),
        lambda r: r.update(captured_at="nonsense"),
        lambda r: r.update(captured_at="2026-10-26T12:41:07"),  # no offset
        lambda r: r.update(captured_at=5),
        lambda r: r.update(files="x"),
        lambda r: r.update(files=[{"index": "0", "display_name": "a", "error": "e"}]),
        lambda r: r.update(files=[{"index": True, "display_name": "a", "error": "e"}]),
        lambda r: r.update(files=[{"index": 0, "display_name": 5, "error": "e"}]),
        lambda r: r.update(files=[{"index": 0, "display_name": "a"}]),
        lambda r: r.update(files=[{"index": 0, "display_name": "a", "error": ""}]),
        lambda r: r.update(files=[{"index": 0, "display_name": "a", "sha256": "zz"}]),
        lambda r: r.update(files=[{"index": 0, "display_name": "a", "error": "e"}] * 2),
        lambda r: r.update(files=["nope"]),
    ],
)
async def test_commit_rejects_bad_records(tmp_path, mutate):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    r = rec(cid, [{"index": 0, "display_name": "a", "error": "e"}])
    mutate(r)
    with pytest.raises(RecordRejected) as e:
        await s.commit(cid, r)
    assert e.value.error == "bad_record"
    assert not s.status(cid).stored


async def test_commit_accepts_z_offset_and_empty_display_name(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    r = rec(cid, [{"index": 0, "display_name": "", "error": "e"}])
    r["captured_at"] = "2026-03-05T01:02:03Z"
    assert (await s.commit(cid, r)).startswith("captures/2026/03/")


async def test_unknown_keys_stored_untouched(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    r = rec(cid, [])
    r["extras"] = {"k": [1, "é"]}
    path = await s.commit(cid, r)
    stored = json.loads((tmp_path / path / "record.json").read_text())
    received = stored.pop("received_at")
    assert stored == r and received


async def test_repeat_commit_is_noop(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    r = rec(cid, [])
    p1 = await s.commit(cid, r)
    mtime = os.stat(tmp_path / p1 / "record.json").st_mtime_ns
    p2 = await s.commit(cid, r)
    assert p1 == p2
    assert len((tmp_path / "index.jsonl").read_text().splitlines()) == 1
    assert os.stat(tmp_path / p1 / "record.json").st_mtime_ns == mtime


async def test_concurrent_commits_store_once(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    data = b"y" * 50_000
    await s.stage_file(cid, 0, body(data), sha(data))
    r = rec(cid, one_file(data, "f.bin"))
    p1, p2 = await asyncio.gather(s.commit(cid, r), s.commit(cid, r))
    assert p1 == p2
    assert len((tmp_path / "index.jsonl").read_text().splitlines()) == 1


async def test_leftover_staging_does_not_count_as_stored(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    files = tmp_path / ".staging" / cid / "files"
    files.mkdir(parents=True)
    (files / "0").write_bytes(b"half written")
    assert s.status(cid) == CaptureStatus(stored=False, staged={})  # no sidecar: not reported
    data = b"fresh"
    await s.stage_file(cid, 0, body(data), sha(data))
    path = await s.commit(cid, rec(cid, one_file(data)))
    assert (tmp_path / path / "files" / "00-a.txt").read_bytes() == data


async def test_unused_staged_files_and_sidecars_not_committed(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.stage_file(cid, 0, body(b"one"), sha(b"one"))
    await s.stage_file(cid, 1, body(b"extra"), sha(b"extra"))
    path = await s.commit(cid, rec(cid, one_file(b"one")))
    assert [p.name for p in (tmp_path / path / "files").iterdir()] == ["00-a.txt"]


def test_display_name_cannot_escape():
    assert safe_name("../../etc/passwd") == "etc_passwd"
    assert "/" not in safe_name("a/b\x00c") and len(safe_name("x" * 300)) == 80 and safe_name("") == "file"
    assert not safe_name("...").startswith(".")
    assert safe_name("..") == "file"


async def test_hostile_display_name_stays_in_folder(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.stage_file(cid, 0, body(b"z"), sha(b"z"))
    path = await s.commit(cid, rec(cid, one_file(b"z", "../../../evil\x00/..")))
    names = [p.name for p in (tmp_path / path / "files").iterdir()]
    assert len(names) == 1 and names[0].startswith("00-") and "/" not in names[0]
    assert not (tmp_path / "evil").exists()


async def test_append_note_requires_stored(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    with pytest.raises(NotStored):
        s.append_note(cid, "hi", "2026-10-26T13:00:00+05:30")
    path = await s.commit(cid, rec(cid, []))
    s.append_note(cid, "hi", "2026-10-26T13:00:00+05:30")
    lines = (tmp_path / path / "notes.jsonl").read_text().splitlines()
    assert len(lines) == 1
    entry = json.loads(lines[0])
    assert entry["note"] == "hi" and entry["note_updated_at"] == "2026-10-26T13:00:00+05:30"
    assert "received_at" in entry


async def test_reindex_rebuilds_from_folders(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    path = await s.commit(cid, rec(cid, []))
    original = (tmp_path / "index.jsonl").read_text()
    (tmp_path / "index.jsonl").unlink()
    assert s.reindex() == 1
    assert (tmp_path / "index.jsonl").read_text() == original
    assert json.loads(original)["path"] == path


def test_reindex_on_empty_root(tmp_path):
    assert CaptureStore(tmp_path).reindex() == 0
    assert (tmp_path / "index.jsonl").read_text() == ""


async def test_cancelled_commit_does_not_let_retry_overlap(tmp_path, monkeypatch):
    import threading

    from yearn_receiver import store as store_mod

    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    started, release = threading.Event(), threading.Event()
    real = store_mod._write_durable

    entered = []

    def slow(path, data):
        if path.name == "record.json":
            entered.append(1)
            started.set()
            release.wait(5)
        real(path, data)

    monkeypatch.setattr(store_mod, "_write_durable", slow)
    r = rec(cid, [])
    first = asyncio.create_task(s.commit(cid, r))
    while not started.is_set():
        await asyncio.sleep(0.01)
    first.cancel()
    with pytest.raises(asyncio.CancelledError):
        await first
    second = asyncio.create_task(s.commit(cid, r))
    await asyncio.sleep(0.1)  # second must be waiting, not running alongside
    assert len(entered) == 1  # no second worker inside the commit while the first runs
    release.set()
    path = await second
    assert path.startswith("captures/2026/10/")
    assert len(list((tmp_path / "captures").glob("*/*/*"))) == 1
    assert json.loads((tmp_path / path / "record.json").read_text())["id"] == cid
    assert len((tmp_path / "index.jsonl").read_text().splitlines()) == 1


# -- final-review fixes ------------------------------------------------------


def test_safe_name_keeps_extension():
    assert safe_name("画像.png") == "file.png"
    long = safe_name("a" * 200 + ".mp4")
    assert long.endswith(".mp4") and len(long) <= 80
    assert safe_name("../../etc/passwd") == "etc_passwd"
    assert safe_name("") == "file"
    assert len(safe_name("x" * 300)) == 80
    assert not safe_name(".bashrc").startswith(".")


async def test_error_null_beside_sha_is_treated_as_absent(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.stage_file(cid, 0, body(b"hi"), sha(b"hi"))
    f = [{"index": 0, "display_name": "a.txt", "sha256": sha(b"hi"), "error": None}]
    path = await s.commit(cid, rec(cid, f))
    assert (tmp_path / path / "files" / "00-a.txt").read_bytes() == b"hi"


async def test_reindex_skips_junk_folders(tmp_path, capsys):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    await s.commit(cid, rec(cid, []))
    base = tmp_path / "captures" / "2026" / "10"
    bad_json = base / str(uuid4())
    bad_json.mkdir()
    (bad_json / "record.json").write_text("{nope")
    no_keys = base / str(uuid4())
    no_keys.mkdir()
    (no_keys / "record.json").write_text(json.dumps({"id": "x"}))
    not_uuid = base / "Not-A-Uuid"
    not_uuid.mkdir()
    (not_uuid / "record.json").write_text("{}")
    assert s.reindex() == 1
    lines = (tmp_path / "index.jsonl").read_text().splitlines()
    assert [json.loads(line)["id"] for line in lines] == [cid]
    assert len(capsys.readouterr().err.strip().splitlines()) == 3


async def test_identical_note_put_is_idempotent(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    path = await s.commit(cid, rec(cid, []))
    s.append_note(cid, "hi", "2026-10-26T13:00:00+05:30")
    s.append_note(cid, "hi", "2026-10-26T13:00:00+05:30")
    s.append_note(cid, "hi", "2026-10-26T14:00:00+05:30")
    lines = (tmp_path / path / "notes.jsonl").read_text().splitlines()
    assert len(lines) == 2


async def test_note_after_torn_last_line_gets_fresh_line(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    path = await s.commit(cid, rec(cid, []))
    notes = tmp_path / path / "notes.jsonl"
    notes.write_bytes(b'{"note": "tor')
    s.append_note(cid, "hi", "2026-10-26T13:00:00+05:30")
    lines = notes.read_text().splitlines()
    assert len(lines) == 2 and json.loads(lines[1])["note"] == "hi"


async def test_lone_surrogate_in_record_and_note_is_stored(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    r = rec(cid, [])
    r["extras"] = {"title": "bad \ud800 text"}
    path = await s.commit(cid, r)
    assert json.loads((tmp_path / path / "record.json").read_text())["extras"]["title"] == "bad \ud800 text"
    s.append_note(cid, "n \ud800", "2026-10-26T13:00:00+05:30")
    line = (tmp_path / path / "notes.jsonl").read_text().splitlines()[0]
    assert json.loads(line)["note"] == "n \ud800"
    assert len((tmp_path / "index.jsonl").read_text().splitlines()) == 1


async def test_concurrent_puts_of_same_index_use_distinct_parts(tmp_path):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    seen = []

    async def slow(data):
        yield data[:1]
        seen.append(sorted(p.name for p in (tmp_path / ".staging" / cid / "files").glob("*.part")))
        await asyncio.sleep(0.05)
        yield data[1:]

    a, b = b"first", b"other"
    await asyncio.gather(
        s.stage_file(cid, 0, slow(a), sha(a)),
        s.stage_file(cid, 0, slow(b), sha(b)),
    )
    assert any(len(names) == 2 for names in seen)
    assert list((tmp_path / ".staging" / cid / "files").glob("*.part")) == []


async def test_commit_retry_after_failed_final_rename(tmp_path, monkeypatch):
    s = CaptureStore(tmp_path)
    cid = str(uuid4())
    data = b"payload"
    await s.stage_file(cid, 0, body(data), sha(data))
    r = rec(cid, [{"index": 0, "display_name": "a.bin", "sha256": sha(data)}])
    staging = tmp_path / ".staging" / cid
    real = os.rename

    def flaky(src, dst, *a, **kw):
        if str(src) == str(staging):
            raise OSError("boom")
        return real(src, dst, *a, **kw)

    monkeypatch.setattr(os, "rename", flaky)
    with pytest.raises(OSError):
        await s.commit(cid, r)
    monkeypatch.undo()
    path = await s.commit(cid, r)
    assert (tmp_path / path / "files" / "00-a.bin").read_bytes() == data
    assert s.status(cid).stored
