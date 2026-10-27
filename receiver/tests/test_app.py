import os
from uuid import uuid4

import httpx
import pytest

from helpers import rec, sha
from yearn_receiver.app import create_app
from yearn_receiver.store import CaptureStore

pytestmark = pytest.mark.anyio

TOKEN = "t" * 40
AUTH = {"Authorization": f"Bearer {TOKEN}"}


def make_client(tmp_path, headers):
    app = create_app(CaptureStore(tmp_path), TOKEN)
    return httpx.AsyncClient(
        transport=httpx.ASGITransport(app), base_url="http://test", headers=headers
    )


@pytest.fixture
async def client(tmp_path):
    async with make_client(tmp_path, AUTH) as c:
        yield c


@pytest.fixture
async def client_no_auth(tmp_path):
    async with make_client(tmp_path, {}) as c:
        yield c


def one(data):
    return [{"index": 0, "display_name": "v.mp4", "sha256": sha(data)}]


async def put_file(client, cid, data, index=0, **kw):
    return await client.put(
        f"/v1/captures/{cid}/files/{index}", content=data, headers={"X-Sha256": sha(data)}, **kw
    )


async def test_requires_token(client_no_auth):
    r = await client_no_auth.get("/v1/health")
    assert r.status_code == 401 and r.json() == {"error": "unauthorized"}


async def test_wrong_token_and_scheme(tmp_path):
    for h in ({"Authorization": "Bearer " + "x" * 40}, {"Authorization": TOKEN}, {"Authorization": b"Bearer \xe9"}):
        async with make_client(tmp_path, h) as c:
            assert (await c.get("/v1/health")).status_code == 401


async def test_auth_runs_before_id_validation(client_no_auth):
    assert (await client_no_auth.get("/v1/captures/not-a-uuid")).status_code == 401


async def test_health(client):
    assert (await client.get("/v1/health")).json() == {"service": "yearn-receiver", "api": 1}


async def test_bad_id_is_400(client):
    r = await client.get("/v1/captures/not-a-uuid")
    assert r.status_code == 400 and r.json() == {"error": "bad_id"}
    for method, path in [
        ("PUT", "/files/0"),
        ("POST", "/commit"),
        ("PUT", "/note"),
    ]:
        r = await client.request(method, f"/v1/captures/nope{path}", headers={"X-Sha256": "0" * 64}, content=b"{}")
        assert r.status_code == 400 and r.json() == {"error": "bad_id"}


async def test_status_unknown_capture(client):
    cid = str(uuid4())
    r = await client.get(f"/v1/captures/{cid}")
    assert r.status_code == 200 and r.json() == {"id": cid, "stored": False, "staged": {}}


async def test_full_upload_flow(client):
    cid = str(uuid4())
    data = os.urandom(300_000)
    r = await put_file(client, cid, data)
    assert r.status_code == 201 and r.json() == {"index": 0, "sha256": sha(data)}
    assert (await client.get(f"/v1/captures/{cid}")).json()["staged"] == {"0": sha(data)}
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, one(data)))
    assert r.status_code == 200 and r.json()["stored"] is True
    path = r.json()["path"]
    assert path.startswith("captures/2026/10/") and path.endswith(cid)
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, one(data)))
    assert r.status_code == 200 and r.json() == {"stored": True, "path": path}
    r = await put_file(client, cid, data)
    assert r.status_code == 200 and r.json() == {"index": 0, "sha256": sha(data)}
    assert (await client.get(f"/v1/captures/{cid}")).json() == {"id": cid, "stored": True, "staged": {}}


async def test_hash_mismatch_is_422(client):
    cid = str(uuid4())
    r = await client.put(f"/v1/captures/{cid}/files/0", content=b"abc", headers={"X-Sha256": "0" * 64})
    assert r.status_code == 422 and r.json() == {"error": "hash_mismatch", "index": 0}
    assert (await client.get(f"/v1/captures/{cid}")).json()["staged"] == {}


async def test_bad_sha_header_is_400(client):
    cid = str(uuid4())
    for h in ({}, {"X-Sha256": "abc"}, {"X-Sha256": "A" * 64}, {"X-Sha256": "g" * 64}):
        r = await client.put(f"/v1/captures/{cid}/files/0", content=b"x", headers=h)
        assert r.status_code == 400 and r.json() == {"error": "missing_sha256"}


async def test_bad_index_is_400(client):
    cid = str(uuid4())
    for idx in ("abc", "-1", "1.5"):
        r = await client.put(f"/v1/captures/{cid}/files/{idx}", content=b"x", headers={"X-Sha256": sha(b"x")})
        assert r.status_code == 400 and r.json() == {"error": "bad_index"}


async def test_commit_bad_json_is_422_bad_record(client):
    cid = str(uuid4())
    for content in (b"not json", b"[1,2]", b'"s"', b"\xff\xfe"):
        r = await client.post(f"/v1/captures/{cid}/commit", content=content)
        assert r.status_code == 422 and r.json() == {"error": "bad_record"}
    bad = rec(cid, [])
    bad["schema_version"] = 2
    r = await client.post(f"/v1/captures/{cid}/commit", json=bad)
    assert r.status_code == 422 and r.json() == {"error": "bad_record"}


async def test_commit_id_mismatch(client):
    cid = str(uuid4())
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(str(uuid4()), []))
    assert r.status_code == 422 and r.json() == {"error": "id_mismatch"}


async def test_commit_missing_file_has_index(client):
    cid = str(uuid4())
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, one(b"never")))
    assert r.status_code == 422 and r.json() == {"error": "missing_file", "index": 0}


async def test_commit_hash_mismatch_has_index(client):
    cid = str(uuid4())
    await put_file(client, cid, b"real")
    r = await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, one(b"other")))
    assert r.status_code == 422 and r.json() == {"error": "hash_mismatch", "index": 0}


async def test_note_before_commit_is_404_then_200_after(client):
    cid = str(uuid4())
    note = {"note": "hi", "note_updated_at": "2026-10-26T12:50:00+05:30"}
    r = await client.put(f"/v1/captures/{cid}/note", json=note)
    assert r.status_code == 404 and r.json() == {"error": "not_stored"}
    await put_file(client, cid, b"d")
    await client.post(f"/v1/captures/{cid}/commit", json=rec(cid, one(b"d")))
    r = await client.put(f"/v1/captures/{cid}/note", json=note)
    assert r.status_code == 200 and r.json() == {"ok": True}


async def test_bad_note_is_422(client):
    cid = str(uuid4())
    good = "2026-10-26T12:50:00+05:30"
    for payload in ({"note": "x"}, {"note_updated_at": good}, {"note": 1, "note_updated_at": good}, {"note": "x", "note_updated_at": 5}, [1]):
        r = await client.put(f"/v1/captures/{cid}/note", json=payload)
        assert r.status_code == 422 and r.json() == {"error": "bad_note"}
    r = await client.put(f"/v1/captures/{cid}/note", content=b"zzz")
    assert r.status_code == 422 and r.json() == {"error": "bad_note"}


@pytest.mark.parametrize("where", ["stage", "commit"])
async def test_disk_full_answers_507(tmp_path, monkeypatch, capsys, where):
    import errno

    from yearn_receiver import store as store_mod

    def full(*a, **kw):
        raise OSError(errno.ENOSPC, "No space left on device")

    async with make_client(tmp_path, AUTH) as c:
        cid = str(uuid4())
        data = b"abc"
        if where == "stage":
            monkeypatch.setattr(store_mod, "_fsync_dir", full)
            r = await put_file(c, cid, data)
        else:
            monkeypatch.setattr(store_mod, "_write_durable", full)
            r = await c.post(f"/v1/captures/{cid}/commit", json=rec(cid, []))
    assert r.status_code == 507
    assert r.json() == {"error": "disk_full"}
    assert "disk full" in capsys.readouterr().err
