"""Durable capture storage: staging, atomic commit, notes, reindex.

Nothing here reports success before the data is on disk: files, directories,
the final rename and the index line are all fsynced first.
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import os
import re
import sys
import threading
from collections import defaultdict
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from uuid import UUID, uuid4

_HEX64 = re.compile(r"[0-9a-fA-F]{64}")
_UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")
_READ = 1 << 20


@dataclass
class CaptureStatus:
    stored: bool
    staged: dict[int, str]


class RecordRejected(Exception):
    def __init__(self, error: str, index: int | None = None):
        super().__init__(error if index is None else f"{error} (file {index})")
        self.error = error
        self.index = index


class HashMismatch(RecordRejected):
    def __init__(self, index: int | None = None):
        super().__init__("hash_mismatch", index)


class NotStored(Exception):
    pass


_EXT = re.compile(r"[A-Za-z0-9_-]{1,10}")


def safe_name(display_name: str) -> str:
    stem, dot, ext = display_name.rpartition(".")
    if not dot or not _EXT.fullmatch(ext):
        stem, suffix = display_name, ""
    else:
        suffix = "." + ext
    stem = _UNSAFE.sub("_", stem).lstrip("._") or "file"
    return stem[: 80 - len(suffix)] + suffix


def _dumps(obj: object, **kw) -> bytes:
    """JSON as UTF-8 bytes; a lone surrogate falls back to \\u escapes instead of raising."""
    try:
        return json.dumps(obj, ensure_ascii=False, **kw).encode()
    except UnicodeEncodeError:
        return json.dumps(obj, ensure_ascii=True, **kw).encode()


def _fsync_dir(path: Path) -> None:
    fd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _fsync_file(path: Path) -> None:
    fd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _write_durable(path: Path, data: bytes) -> None:
    with open(path, "wb") as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(_READ):
            h.update(chunk)
    return h.hexdigest()


def _check_id(capture_id: str) -> None:
    if str(UUID(capture_id)) != capture_id.lower():
        raise ValueError(f"not a canonical uuid: {capture_id!r}")


def _now() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def _validate(capture_id: str, record: dict) -> tuple[datetime, list[dict]]:
    """Return (captured_at, files that need an upload)."""
    if not isinstance(record, dict) or record.get("schema_version") != 1:
        raise RecordRejected("bad_record")
    if record.get("id") != capture_id:
        raise RecordRejected("id_mismatch")
    captured = record.get("captured_at")
    if not isinstance(captured, str):
        raise RecordRejected("bad_record")
    try:
        when = datetime.fromisoformat(captured)
    except ValueError:
        raise RecordRejected("bad_record") from None
    if when.tzinfo is None:
        raise RecordRejected("bad_record")
    files = record.get("files")
    if not isinstance(files, list):
        raise RecordRejected("bad_record")
    seen: set[int] = set()
    needed: list[dict] = []
    for f in files:
        if not isinstance(f, dict):
            raise RecordRejected("bad_record")
        idx = f.get("index")
        if type(idx) is not int or idx < 0 or idx in seen:
            raise RecordRejected("bad_record")
        seen.add(idx)
        if not isinstance(f.get("display_name"), str):
            raise RecordRejected("bad_record")
        err = f.get("error")
        if isinstance(err, str) and err:
            continue
        sha = f.get("sha256")
        if err is not None or not isinstance(sha, str) or not _HEX64.fullmatch(sha):
            raise RecordRejected("bad_record")
        needed.append(f)
    return when, needed


class CaptureStore:
    def __init__(self, root: Path):
        self.root = Path(root)
        self._locks: dict[str, asyncio.Lock] = defaultdict(asyncio.Lock)
        self._index_lock = threading.Lock()
        # Held by the commit worker thread itself, so it outlives a cancelled awaiter.
        self._thread_locks: dict[str, threading.Lock] = defaultdict(threading.Lock)

    # -- paths -------------------------------------------------------------

    def _staging(self, capture_id: str) -> Path:
        return self.root / ".staging" / capture_id

    def _find_committed(self, capture_id: str) -> Path | None:
        _check_id(capture_id)
        for folder in (self.root / "captures").glob(f"*/*/{capture_id}"):
            if (folder / "record.json").is_file():
                return folder
        return None

    # -- queries -----------------------------------------------------------

    def status(self, capture_id: str) -> CaptureStatus:
        if self._find_committed(capture_id) is not None:
            return CaptureStatus(stored=True, staged={})
        staged: dict[int, str] = {}
        files = self._staging(capture_id) / "files"
        if files.is_dir():
            for side in files.glob("*.sha256"):
                stem = side.name.removesuffix(".sha256")
                if stem.isdecimal() and (files / stem).is_file():
                    staged[int(stem)] = side.read_text().strip()
        return CaptureStatus(stored=False, staged=staged)

    # -- staging -----------------------------------------------------------

    async def stage_file(
        self, capture_id: str, index: int, chunks: AsyncIterator[bytes], expected_sha256: str
    ) -> str:
        if self._find_committed(capture_id) is not None:
            async for _ in chunks:
                pass
            return expected_sha256
        files = self._staging(capture_id) / "files"
        await asyncio.to_thread(files.mkdir, parents=True, exist_ok=True)
        part = files / f"{index}.{uuid4().hex}.part"
        final, side = files / str(index), files / f"{index}.sha256"
        h = hashlib.sha256()
        try:
            with open(part, "wb") as f:
                async for chunk in chunks:
                    h.update(chunk)
                    f.write(chunk)
                f.flush()
                await asyncio.to_thread(os.fsync, f.fileno())
        except BaseException:
            part.unlink(missing_ok=True)
            raise
        if h.hexdigest() != expected_sha256.lower():
            part.unlink(missing_ok=True)
            raise HashMismatch(index)
        digest = h.hexdigest()

        def finish() -> None:
            side.unlink(missing_ok=True)  # never leave a sidecar describing older content
            os.rename(part, final)
            _fsync_dir(files)
            _write_durable(side, digest.encode())
            _fsync_dir(files)

        await asyncio.to_thread(finish)
        return digest

    # -- commit ------------------------------------------------------------

    async def commit(self, capture_id: str, record: dict) -> str:
        _check_id(capture_id)
        async with self._locks[capture_id]:
            existing = self._find_committed(capture_id)
            if existing is not None:
                return existing.relative_to(self.root).as_posix()
            thread_lock = self._thread_locks[capture_id]
            return await asyncio.to_thread(self._commit_locked, capture_id, record, thread_lock)

    def _commit_locked(self, capture_id: str, record: dict, thread_lock: threading.Lock) -> str:
        with thread_lock:
            existing = self._find_committed(capture_id)
            if existing is not None:
                return existing.relative_to(self.root).as_posix()
            return self._commit_unlocked(capture_id, record)

    def _commit_unlocked(self, capture_id: str, record: dict) -> str:
        when, needed = _validate(capture_id, record)
        staging = self._staging(capture_id)
        files = staging / "files"
        files.mkdir(parents=True, exist_ok=True)

        # Verify everything before touching anything.
        for f in needed:
            src = files / str(f["index"])
            if not src.is_file():
                # a failed earlier commit may already have renamed it
                src = files / f"{f['index']:02d}-{safe_name(f['display_name'])}"
                if not src.is_file():
                    raise RecordRejected("missing_file", f["index"])
            if _sha256_file(src) != f["sha256"].lower():
                src.unlink(missing_ok=True)
                (files / f"{f['index']}.sha256").unlink(missing_ok=True)
                raise HashMismatch(f["index"])

        stored = dict(record)
        stored["received_at"] = _now()
        _write_durable(staging / "record.json", _dumps(stored, indent=2))

        keep: set[str] = set()
        for f in needed:
            name = f"{f['index']:02d}-{safe_name(f['display_name'])}"
            if (files / str(f["index"])).is_file():
                os.rename(files / str(f["index"]), files / name)
            keep.add(name)
        for leftover in files.iterdir():  # sidecars, unused or stale uploads
            if leftover.name not in keep:
                leftover.unlink()
        for name in keep:
            _fsync_file(files / name)
        _fsync_dir(files)
        _fsync_dir(staging)

        final = self.root / "captures" / f"{when.year:04d}" / f"{when.month:02d}" / capture_id
        final.parent.mkdir(parents=True, exist_ok=True)
        os.rename(staging, final)
        _fsync_dir(final.parent)
        _fsync_dir(final.parent.parent)
        _fsync_dir(self.root / "captures")
        _fsync_dir(self.root / ".staging")
        _fsync_dir(self.root)

        path = final.relative_to(self.root).as_posix()
        self._append_index(
            {"id": capture_id, "captured_at": record["captured_at"], "received_at": stored["received_at"], "path": path}
        )
        return path

    def _append_index(self, entry: dict) -> None:
        index = self.root / "index.jsonl"
        with self._index_lock:
            created = not index.exists()
            with open(index, "ab") as f:
                f.write(_dumps(entry) + b"\n")
                f.flush()
                os.fsync(f.fileno())
            if created:
                _fsync_dir(self.root)

    # -- notes / reindex ---------------------------------------------------

    def append_note(self, capture_id: str, note: str, note_updated_at: str) -> None:
        folder = self._find_committed(capture_id)
        if folder is None:
            raise NotStored(capture_id)
        line = _dumps({"note": note, "note_updated_at": note_updated_at, "received_at": _now()})
        notes = folder / "notes.jsonl"
        created = not notes.exists()
        torn = False
        if not created:
            existing = notes.read_bytes()
            torn = bool(existing) and not existing.endswith(b"\n")
            for raw in existing.splitlines():
                try:
                    prev = json.loads(raw)
                except ValueError:
                    continue
                if isinstance(prev, dict) and prev.get("note") == note and prev.get("note_updated_at") == note_updated_at:
                    return  # a repeat of a stored note changes nothing
        with open(notes, "ab") as f:
            f.write((b"\n" if torn else b"") + line + b"\n")
            f.flush()
            os.fsync(f.fileno())
        if created:
            _fsync_dir(folder)

    def reindex(self) -> int:
        entries = []
        for rec_path in sorted((self.root / "captures").glob("*/*/*/record.json")):
            rel = rec_path.parent.relative_to(self.root).as_posix()
            try:
                _check_id(rec_path.parent.name)
                if rec_path.parent.name != rec_path.parent.name.lower():
                    raise ValueError("folder name is not a lowercase uuid")
                record = json.loads(rec_path.read_text())
                entry = {
                    "id": record["id"],
                    "captured_at": record["captured_at"],
                    "received_at": record["received_at"],
                    "path": rel,
                }
                if not all(isinstance(entry[k], str) for k in ("id", "captured_at", "received_at")):
                    raise ValueError("id, captured_at and received_at must be strings")
            except (ValueError, KeyError, TypeError, OSError) as e:
                print(f"yearn-receiver: reindex: skipping {rel}: {e}", file=sys.stderr)
                continue
            entries.append(entry)
        entries.sort(key=lambda e: (e["received_at"], e["id"]))
        data = b"".join(_dumps(e) + b"\n" for e in entries)
        self.root.mkdir(parents=True, exist_ok=True)
        with self._index_lock:
            tmp = self.root / "index.jsonl.tmp"
            _write_durable(tmp, data)
            os.replace(tmp, self.root / "index.jsonl")
            _fsync_dir(self.root)
        return len(entries)
