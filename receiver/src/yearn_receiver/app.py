"""HTTP wire contract (v1) over the CaptureStore."""

from __future__ import annotations

import hmac
import json
import re
from uuid import UUID

from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.types import ASGIApp, Receive, Scope, Send

from .store import CaptureStore, NotStored, RecordRejected

_HEX64 = re.compile(r"[0-9a-f]{64}")
_INDEX = re.compile(r"[0-9]{1,9}")


class BearerAuth:
    """Pure ASGI middleware: no task cancellation, no body interference."""

    def __init__(self, app: ASGIApp, token: str):
        self.app = app
        self.expected = f"Bearer {token}".encode()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        given = b""
        for k, v in scope["headers"]:
            if k == b"authorization":
                given = v
                break
        if not hmac.compare_digest(given, self.expected):
            await JSONResponse({"error": "unauthorized"}, status_code=401)(scope, receive, send)
            return
        await self.app(scope, receive, send)


def _err(error: str, status: int, index: int | None = None) -> JSONResponse:
    body: dict = {"error": error}
    if index is not None:
        body["index"] = index
    return JSONResponse(body, status_code=status)


def _canonical_id(raw: str) -> str | None:
    try:
        if str(UUID(raw)) == raw.lower():
            return raw.lower()
    except ValueError:
        pass
    return None


def create_app(store: CaptureStore, token: str) -> Starlette:
    async def health(request: Request) -> JSONResponse:
        return JSONResponse({"service": "yearn-receiver", "api": 1})

    async def status(request: Request) -> JSONResponse:
        cid = _canonical_id(request.path_params["id"])
        if cid is None:
            return _err("bad_id", 400)
        st = store.status(cid)
        return JSONResponse(
            {"id": cid, "stored": st.stored, "staged": {str(i): h for i, h in sorted(st.staged.items())}}
        )

    async def put_file(request: Request) -> JSONResponse:
        cid = _canonical_id(request.path_params["id"])
        if cid is None:
            return _err("bad_id", 400)
        raw_index = request.path_params["index"]
        if not _INDEX.fullmatch(raw_index):
            return _err("bad_index", 400)
        index = int(raw_index)
        digest = request.headers.get("x-sha256", "")
        if not _HEX64.fullmatch(digest):
            return _err("missing_sha256", 400)
        already = store.status(cid).stored
        try:
            # stage_file drains the body without writing when already stored
            await store.stage_file(cid, index, request.stream(), digest)
        except RecordRejected as e:
            return _err(e.error, 422, e.index)
        return JSONResponse({"index": index, "sha256": digest}, status_code=200 if already else 201)

    async def commit(request: Request) -> JSONResponse:
        cid = _canonical_id(request.path_params["id"])
        if cid is None:
            return _err("bad_id", 400)
        try:
            record = json.loads(await request.body())
        except (ValueError, UnicodeDecodeError):
            return _err("bad_record", 422)
        if not isinstance(record, dict):
            return _err("bad_record", 422)
        try:
            path = await store.commit(cid, record)
        except RecordRejected as e:
            return _err(e.error, 422, e.index)
        return JSONResponse({"stored": True, "path": path})

    async def note(request: Request) -> JSONResponse:
        cid = _canonical_id(request.path_params["id"])
        if cid is None:
            return _err("bad_id", 400)
        try:
            body = json.loads(await request.body())
        except (ValueError, UnicodeDecodeError):
            return _err("bad_note", 422)
        if (
            not isinstance(body, dict)
            or not isinstance(body.get("note"), str)
            or not isinstance(body.get("note_updated_at"), str)
        ):
            return _err("bad_note", 422)
        try:
            store.append_note(cid, body["note"], body["note_updated_at"])
        except NotStored:
            return _err("not_stored", 404)
        return JSONResponse({"ok": True})

    return Starlette(
        routes=[
            Route("/v1/health", health, methods=["GET"]),
            Route("/v1/captures/{id}", status, methods=["GET"]),
            Route("/v1/captures/{id}/files/{index}", put_file, methods=["PUT"]),
            Route("/v1/captures/{id}/commit", commit, methods=["POST"]),
            Route("/v1/captures/{id}/note", note, methods=["PUT"]),
        ],
        middleware=[Middleware(BearerAuth, token=token)],
    )
