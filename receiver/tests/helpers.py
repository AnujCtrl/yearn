import hashlib


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def rec(id: str, files: list[dict]) -> dict:
    return {
        "schema_version": 1,
        "id": id,
        "captured_at": "2026-10-26T12:41:07+05:30",
        "intent": "share",
        "files": files,
    }


async def body(b: bytes):
    third = max(1, len(b) // 3)
    for i in range(0, len(b), third):
        yield b[i : i + third]
