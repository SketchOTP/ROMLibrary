#!/usr/bin/env python3
"""Repo-local Authority 6.0 Atlas runtime (workspace implementation, not a release)."""
from __future__ import annotations

import argparse
import ast
import datetime as dt
import fcntl
import hashlib
import html
import json
import os
import re
import sqlite3
import subprocess
import sys
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[2]
AUTH = ROOT / ".authority"
AGENT = ROOT / ".agent"
ATLAS = AGENT / "atlas"
GENERATED = ATLAS / "generated"
DB_PATH = GENERATED / "authority.sqlite3"
SNAPSHOT_PATH = GENERATED / "snapshot.json"
IDENTITY_LEDGER = ATLAS / "RELATIONSHIPS.jsonl"
REPOSITORY_FILE = ATLAS / "REPOSITORY.json"
VIEWER_FILE = Path(__file__).with_name("viewer.html")
RUNTIME_VERSION = "workspace-runtime/0.1.0"
MAX_QUERY = 300
MAX_RESULTS = 50
MAX_TEXT_BYTES = 2 * 1024 * 1024
CANONICAL_LEDGERS = (
    ".agent/GOAL.md",
    ".agent/CURRENT.md",
    ".agent/DIRECTIVES.md",
    ".agent/OUTCOMES.md",
    ".agent/RECORD.md",
    ".agent/LEARNINGS.md",
    ".agent/EXTERNAL.md",
)
EXCLUDED_DIRS = {
    ".git", ".hg", ".svn", ".venv", "venv", "node_modules", "__pycache__",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", "dist", "build",
}
EXCLUDED_PREFIXES = (
    ".authority/setup/",
    ".agent/atlas/generated/",
)
EXCLUDED_SUFFIXES = {".pyc", ".pyo", ".sqlite", ".sqlite3", ".db", ".wal", ".shm"}
JOURNEY_KIND = "journey_record"


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def safe_atomic_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    payload = (json.dumps(value, indent=2, ensure_ascii=False, sort_keys=True) + "\n").encode("utf-8")
    with temporary.open("wb") as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)
    try:
        fd = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    except (AttributeError, OSError):
        pass


def repo_identity() -> dict:
    ATLAS.mkdir(parents=True, exist_ok=True)
    if REPOSITORY_FILE.exists():
        data = json.loads(REPOSITORY_FILE.read_text(encoding="utf-8"))
        if data.get("schema") != "authority.repository.v1" or not data.get("repository_uuid"):
            raise RuntimeError(f"Unrecognized repository identity file: {REPOSITORY_FILE}")
        return data
    data = {
        "schema": "authority.repository.v1",
        "repository_uuid": str(uuid.uuid4()),
        "created_at": utc_now(),
        "root": str(ROOT),
        "git": "not initialized at setup; no Git root detected",
    }
    safe_atomic_json(REPOSITORY_FILE, data)
    return data


def db_connect() -> tuple[sqlite3.Connection, bool]:
    GENERATED.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB_PATH, timeout=30)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript(
        """
        CREATE TABLE IF NOT EXISTS entities (
            entity_id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            layer TEXT NOT NULL,
            path TEXT NOT NULL,
            title TEXT NOT NULL,
            summary TEXT NOT NULL,
            content TEXT NOT NULL,
            sha256 TEXT NOT NULL,
            size INTEGER NOT NULL,
            language TEXT NOT NULL,
            classification TEXT NOT NULL,
            status TEXT NOT NULL,
            aliases TEXT NOT NULL,
            evidence TEXT NOT NULL,
            indexed_at TEXT NOT NULL,
            source_missing INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS entity_path_idx ON entities(path);
        CREATE INDEX IF NOT EXISTS entity_layer_idx ON entities(layer, status);
        CREATE TABLE IF NOT EXISTS edges (
            edge_id TEXT PRIMARY KEY,
            source_id TEXT NOT NULL,
            target_id TEXT NOT NULL,
            relation TEXT NOT NULL,
            evidence TEXT NOT NULL,
            evidence_path TEXT NOT NULL,
            evidence_hash TEXT NOT NULL,
            epistemic_status TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS edge_source_idx ON edges(source_id);
        CREATE INDEX IF NOT EXISTS edge_target_idx ON edges(target_id);
        CREATE TABLE IF NOT EXISTS entity_aliases (
            entity_id TEXT NOT NULL,
            alias TEXT NOT NULL,
            PRIMARY KEY(entity_id, alias)
        );
        CREATE INDEX IF NOT EXISTS entity_alias_value_idx ON entity_aliases(alias);
        CREATE TABLE IF NOT EXISTS snapshots (
            snapshot_id TEXT PRIMARY KEY,
            created_at TEXT NOT NULL,
            repository_uuid TEXT NOT NULL,
            manifest_json TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS runtime_meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        """
    )
    fts = True
    try:
        con.execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS entity_search USING "
            "fts5(entity_id UNINDEXED, layer UNINDEXED, title, path, summary, content, aliases, "
            "tokenize='unicode61 remove_diacritics 2')"
        )
    except sqlite3.OperationalError:
        fts = False
    if con.execute("SELECT COUNT(*) FROM entity_aliases").fetchone()[0] == 0:
        for row in con.execute("SELECT entity_id,aliases FROM entities"):
            try:
                aliases = json.loads(row["aliases"])
            except (json.JSONDecodeError, TypeError):
                aliases = []
            con.executemany("INSERT OR IGNORE INTO entity_aliases(entity_id,alias) VALUES(?,?)", [(row["entity_id"], str(alias)) for alias in aliases if alias])
    con.commit()
    return con, fts


def jsonl_records(path: Path) -> list[dict]:
    if not path.exists():
        return []
    raw = path.read_bytes()
    if raw and not raw.endswith(b"\n"):
        raise RuntimeError(f"Incomplete final JSONL record; preserved without modification: {relative(path)}")
    rows = []
    for number, line in enumerate(raw.splitlines(), 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
            if isinstance(value, dict):
                rows.append(value)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"Invalid JSONL at {relative(path)}:{number}: {exc}") from exc
    return rows


def append_jsonl(path: Path, record: dict, idempotency_key: str) -> bool:
    path.parent.mkdir(parents=True, exist_ok=True)
    record = dict(record)
    record["idempotency_key"] = idempotency_key
    encoded = (json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    with path.open("a+b") as stream:
        fcntl.flock(stream.fileno(), fcntl.LOCK_EX)
        stream.seek(0)
        existing = stream.read()
        if existing and not existing.endswith(b"\n"):
            raise RuntimeError(f"Cannot append after incomplete final JSONL record: {relative(path)}")
        for line in existing.splitlines():
            try:
                if json.loads(line).get("idempotency_key") == idempotency_key:
                    return False
            except (json.JSONDecodeError, AttributeError):
                raise RuntimeError(f"Refusing to append to corrupt JSONL ledger: {relative(path)}")
        stream.seek(0, os.SEEK_END)
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())
        return True


def read_identities() -> tuple[dict[str, dict], dict[str, str]]:
    by_path: dict[str, dict] = {}
    by_id: dict[str, dict] = {}
    if not IDENTITY_LEDGER.exists():
        IDENTITY_LEDGER.parent.mkdir(parents=True, exist_ok=True)
        IDENTITY_LEDGER.touch()
    for rec in jsonl_records(IDENTITY_LEDGER):
        if rec.get("record_type") != "IDENTITY_BINDING":
            continue
        file_id = rec.get("file_id")
        path = rec.get("path")
        action = rec.get("action")
        if not file_id or not path:
            continue
        if action == "bind":
            by_path[path] = {"file_id": file_id, "sha256": rec.get("content_sha256", ""), "status": "active"}
            by_id[file_id] = {"path": path, "sha256": rec.get("content_sha256", ""), "status": "active", "aliases": [path]}
        elif action == "observe":
            if by_path.get(path, {}).get("file_id") == file_id:
                by_path[path]["sha256"] = rec.get("content_sha256", by_path[path].get("sha256", ""))
                old_id = by_id.get(file_id, {})
                by_id[file_id] = {**old_id, "path": path, "sha256": by_path[path]["sha256"], "status": "active"}
        elif action == "rebind":
            old_path = rec.get("old_path")
            state = by_id.get(file_id, {})
            aliases = list(dict.fromkeys([*state.get("aliases", []), *([old_path] if old_path else []), path]))
            if old_path:
                by_path.pop(old_path, None)
            for known_path, known in list(by_path.items()):
                if known.get("file_id") == file_id:
                    by_path.pop(known_path, None)
            by_path[path] = {"file_id": file_id, "sha256": rec.get("content_sha256", ""), "status": "active"}
            by_id[file_id] = {"path": path, "sha256": rec.get("content_sha256", ""), "status": "active", "aliases": aliases}
        elif action == "tombstone":
            if by_path.get(path, {}).get("file_id") == file_id:
                by_path.pop(path, None)
            state = by_id.get(file_id, {})
            aliases = list(dict.fromkeys([*state.get("aliases", []), path]))
            by_id[file_id] = {"path": path, "sha256": rec.get("content_sha256", ""), "status": "deleted", "aliases": aliases}
    return by_id, by_path


def identity_record(action: str, file_id: str, path: str, digest: str, old_path: str | None = None) -> None:
    now = utc_now()
    record = {
        "record_type": "IDENTITY_BINDING",
        "assertion_id": str(uuid.uuid4()),
        "action": action,
        "file_id": file_id,
        "path": path,
        "old_path": old_path,
        "content_sha256": digest,
        "repository_uuid": repo_identity()["repository_uuid"],
        "actor": "authority-atlas-runtime",
        "recorded_at": now,
        "epistemic_status": "VERIFIED_PATH_AND_CONTENT_HASH",
        "evidence": {"method": "filesystem inspection", "root": str(ROOT)},
    }
    append_jsonl(IDENTITY_LEDGER, record, f"identity:{action}:{file_id}:{path}:{digest}")


def excluded(path: Path) -> bool:
    rel = relative(path)
    if any(rel.startswith(prefix) for prefix in EXCLUDED_PREFIXES):
        return True
    if path.suffix.lower() in EXCLUDED_SUFFIXES:
        return True
    if path.name in {".DS_Store", "Thumbs.db"}:
        return True
    return False


def walk_files() -> tuple[list[dict], list[str]]:
    files: list[dict] = []
    excluded_paths: list[str] = []
    for current, dirs, names in os.walk(ROOT, followlinks=False):
        base = Path(current)
        kept_dirs = []
        for name in dirs:
            candidate = base / name
            rel = relative(candidate)
            if name in EXCLUDED_DIRS or any(rel.startswith(prefix.rstrip("/") + "/") for prefix in EXCLUDED_PREFIXES):
                excluded_paths.append(rel + "/")
                continue
            if candidate.is_symlink():
                excluded_paths.append(rel + "/ [symlink directory not followed]")
                continue
            kept_dirs.append(name)
        dirs[:] = kept_dirs
        for name in names:
            path = base / name
            rel = relative(path)
            if excluded(path):
                excluded_paths.append(rel)
                continue
            if path.is_symlink():
                excluded_paths.append(rel + " [symlink not followed]")
                continue
            if not path.is_file():
                continue
            stat = path.stat()
            digest = sha256_file(path)
            binary = stat.st_size > MAX_TEXT_BYTES
            raw = b""
            if not binary:
                raw = path.read_bytes()
                binary = b"\x00" in raw
            text = "" if binary else raw.decode("utf-8", errors="replace")
            language = language_for(path)
            files.append({
                "path": rel,
                "sha256": digest,
                "size": stat.st_size,
                "mtime_ns": stat.st_mtime_ns,
                "language": language,
                "binary": binary,
                "text": text,
            })
    files.sort(key=lambda item: item["path"])
    return files, sorted(excluded_paths)


def language_for(path: Path) -> str:
    mapping = {
        ".py": "python", ".md": "markdown", ".json": "json", ".jsonl": "jsonl",
        ".toml": "toml", ".yaml": "yaml", ".yml": "yaml", ".html": "html",
        ".css": "css", ".js": "javascript", ".ts": "typescript", ".tsx": "typescript-react",
        ".jsx": "javascript-react", ".sh": "shell", ".sql": "sql", ".xml": "xml",
        ".csv": "csv", ".txt": "text", ".ini": "ini", ".cfg": "config",
    }
    return mapping.get(path.suffix.lower(), "asset-or-unknown")


def responsible_summary(path: Path, text: str, binary: bool) -> tuple[str, str, str]:
    rel = relative(path)
    if rel.startswith(".agent/atlas/examples/"):
        return "Synthetic Atlas fixture; not ROM project history or runtime evidence.", "explicit example path and Atlas fixture policy", "1"
    if binary:
        return "Binary or large asset; content indexing is omitted.", "classification", ""
    lines = text.splitlines()
    if path.suffix.lower() == ".md":
        first_heading = next((line.lstrip("# ").strip() for line in lines if line.startswith("#")), path.name)
        paragraph = ""
        for line in lines:
            candidate = line.strip()
            if candidate and not candidate.startswith(("#", "|", "```", "ENTRY_ID:", "UTC:", "ACTOR:", "TYPE:", "GOAL:", "TASK:", "STATUS:", "SUPERSEDES:", "EVIDENCE:", "SUMMARY:", "NEXT:")):
                paragraph = candidate
                break
        summary = f"{first_heading}. {paragraph}" if paragraph else first_heading
        return summary[:320], "document heading and first descriptive text", "1"
    if path.suffix.lower() == ".py":
        try:
            tree = ast.parse(text)
            doc = ast.get_docstring(tree)
            if doc:
                return doc.splitlines()[0][:320], "Python module docstring", "1"
            symbols = [node.name for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))]
            if symbols:
                return "Defines: " + ", ".join(symbols[:8]), "Python syntax-derived declarations", "1"
        except SyntaxError:
            return "Python source; parsing failed, inspect directly.", "syntax parsing failed", "1"
    if path.suffix.lower() == ".json":
        try:
            value = json.loads(text)
            if isinstance(value, dict):
                return "JSON configuration/data with keys: " + ", ".join(list(value)[:10]), "JSON structure", "1"
        except json.JSONDecodeError:
            return "JSON-like data; parse failed, inspect directly.", "parse failed", "1"
    if path.suffix.lower() == ".toml":
        headings = [line.strip()[1:-1] for line in lines if line.strip().startswith("[") and line.strip().endswith("]")]
        return ("TOML configuration; sections: " + ", ".join(headings[:8]) if headings else "TOML configuration."), "TOML sections", "1"
    return f"{language_for(path)} file; inspect declarations and references.", "file classification", "1"


def extract_symbols(path: Path, text: str) -> list[str]:
    symbols: list[str] = []
    if path.suffix.lower() == ".py":
        try:
            tree = ast.parse(text)
            symbols.extend(node.name for node in ast.walk(tree) if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)))
        except SyntaxError:
            pass
    elif path.suffix.lower() in {".js", ".jsx", ".ts", ".tsx"}:
        for pattern in (r"\b(?:function|class)\s+([A-Za-z_$][\w$]*)", r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*="):
            symbols.extend(re.findall(pattern, text))
    elif path.suffix.lower() == ".md":
        symbols.extend(line.lstrip("# ").strip() for line in text.splitlines() if line.startswith("#"))
    elif path.suffix.lower() == ".toml":
        symbols.extend(line.split("=", 1)[0].strip() for line in text.splitlines() if "=" in line and not line.strip().startswith("#"))
    return list(dict.fromkeys(symbols))[:200]


def classification_for(path: str, index: int = 0) -> str:
    if path.startswith(".agent/GOAL") or path.startswith(".agent/CURRENT") or path.startswith(".agent/DIRECT") or path.startswith(".agent/OUTCOMES") or path.startswith(".agent/RECORD") or path.startswith(".agent/LEARNINGS") or path.startswith(".agent/EXTERNAL"):
        root = "100"
    elif path.startswith(".agent/atlas/"):
        root = "230" if "/schemas/" in path or "/record-templates/" in path else ("220" if "/examples/" in path else "200")
    elif path.startswith(".agent/authority/"):
        root = "300"
    elif path.startswith(".authority/runtime/"):
        root = "400"
    elif path.startswith(".agents/"):
        root = "500"
    elif path.startswith(".codex/"):
        root = "600"
    elif path.startswith(".authority/procedures/"):
        root = "700"
    else:
        root = "900"
    digest = hashlib.sha256(path.encode("utf-8")).hexdigest()
    return f"{root}.{int(digest[:3], 16):04d}.{int(digest[3:6], 16):04d}"


def read_identity_map(files: list[dict], repo_uuid: str) -> dict[str, str]:
    by_id, by_path = read_identities()
    current_paths = {item["path"] for item in files}
    assigned: dict[str, str] = {}
    claimed: set[str] = set()
    for item in files:
        path = item["path"]
        old = by_path.get(path)
        if old and old.get("file_id") not in claimed:
            assigned[path] = old["file_id"]
            claimed.add(old["file_id"])
    hash_candidates: dict[str, list[str]] = {}
    for file_id, state in by_id.items():
        if state.get("status") == "active" and state.get("path") not in current_paths and state.get("sha256"):
            hash_candidates.setdefault(state["sha256"], []).append(file_id)
    for item in files:
        path = item["path"]
        if path in assigned:
            continue
        candidates = hash_candidates.get(item["sha256"], [])
        available = [candidate for candidate in candidates if candidate not in claimed]
        if len(available) == 1:
            file_id = available[0]
            previous_path = by_id[file_id]["path"]
            identity_record("rebind", file_id, path, item["sha256"], previous_path)
            assigned[path] = file_id
            claimed.add(file_id)
        else:
            name = Path(path).name
            file_id = "repo:" + repo_uuid + "/entity:" + str(uuid.uuid4())
            identity_record("bind", file_id, path, item["sha256"])
            assigned[path] = file_id
            claimed.add(file_id)
    _, current_by_path = read_identities()
    for old_path, state in current_by_path.items():
        if old_path not in current_paths and state.get("file_id") not in claimed:
            identity_record("tombstone", state["file_id"], old_path, state.get("sha256", ""))
    _, current_by_path = read_identities()
    # The relationship ledger is its own identity authority; hashing it into that
    # ledger would create a self-referential append loop. Its stable identity is
    # derived from repository UUID and its canonical path.
    relation_path = ".agent/atlas/RELATIONSHIPS.jsonl"
    if relation_path in {item["path"] for item in files}:
        assigned[relation_path] = "repo:" + repo_uuid + "/entity:" + str(uuid.uuid5(uuid.NAMESPACE_URL, repo_uuid + ":" + relation_path))
    for item in files:
        path = item["path"]
        file_id = assigned[path]
        if path == relation_path:
            continue
        known = current_by_path.get(path)
        if known and known.get("file_id") == file_id and known.get("sha256") != item["sha256"]:
            identity_record("observe", file_id, path, item["sha256"])
    return assigned


def parse_ledger_records() -> list[dict]:
    records: list[dict] = []
    repo_uuid = repo_identity()["repository_uuid"]
    for rel in CANONICAL_LEDGERS:
        path = ROOT / rel
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        chunks = re.split(r"(?m)(?=^## )", text)
        for chunk in chunks:
            if not chunk.startswith("## "):
                continue
            values: dict[str, str] = {}
            for key in ("ENTRY_ID", "UTC", "ACTOR", "TYPE", "GOAL", "TASK", "STATUS", "SUPERSEDES", "EVIDENCE", "SUMMARY", "NEXT"):
                match = re.search(rf"(?m)^{key}:\s*(.*)$", chunk)
                if match:
                    values[key.lower()] = match.group(1).strip()
            if not values.get("entry_id"):
                continue
            entry_id = values["entry_id"]
            entity_id = "repo:" + repo_uuid + "/journey:" + str(uuid.uuid5(uuid.NAMESPACE_URL, repo_uuid + ":" + entry_id))
            title = chunk.splitlines()[0][3:].strip()
            body_hash = sha256_bytes(chunk.encode("utf-8"))
            records.append({
                "entity_id": entity_id,
                "kind": JOURNEY_KIND,
                "layer": "journey",
                "path": rel + "#" + entry_id,
                "title": title,
                "summary": values.get("summary", title),
                "content": chunk[:MAX_TEXT_BYTES],
                "sha256": body_hash,
                "size": len(chunk.encode("utf-8")),
                "language": "markdown-record",
                "classification": "100.000",
                "status": values.get("status", "UNKNOWN"),
                "aliases": json.dumps([entry_id, values.get("task", ""), title], ensure_ascii=False),
                "evidence": json.dumps({"source": rel, "entry_id": entry_id, "recorded_at": values.get("utc", "UNKNOWN"), "basis": "canonical ledger record"}, ensure_ascii=False),
                "indexed_at": utc_now(),
                "source_missing": 0,
                "entry": values,
            })
    return records


def parse_relationships() -> list[dict]:
    rows = jsonl_records(IDENTITY_LEDGER)
    relations = []
    for row in rows:
        if row.get("record_type") == "RELATIONSHIP":
            relations.append(row)
    return relations


def markdown_links(path: str, text: str, existing: set[str]) -> list[tuple[str, str, str]]:
    links = []
    parent = PurePosixPath(path).parent
    for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
        target = target.split("#", 1)[0].strip()
        if not target or "://" in target or target.startswith(("mailto:", "#")):
            continue
        resolved = str(PurePosixPath(parent, target))
        normalized = os.path.normpath(resolved).replace("\\", "/")
        if normalized.startswith("../") or normalized == "..":
            continue
        if normalized in existing:
            links.append((normalized, "references", "documented local link"))
    return links


def python_imports(path: str, text: str, existing: set[str]) -> list[tuple[str, str, str]]:
    links = []
    if not path.endswith(".py"):
        return links
    try:
        tree = ast.parse(text)
    except SyntaxError:
        return links
    modules = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            modules.add(node.module)
    for module in modules:
        candidate = module.replace(".", "/") + ".py"
        package_init = module.replace(".", "/") + "/__init__.py"
        if candidate in existing:
            links.append((candidate, "imports", "Python AST import syntax"))
        elif package_init in existing:
            links.append((package_init, "imports", "Python AST import syntax"))
    return links


def edge_id(source_id: str, target_id: str, relation: str, evidence_path: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "|".join((source_id, target_id, relation, evidence_path))))


def upsert_entity(con: sqlite3.Connection, fts: bool, row: dict) -> None:
    old = con.execute("SELECT * FROM entities WHERE entity_id=?", (row["entity_id"],)).fetchone()
    compared = [key for key in row if key in (old.keys() if old else ()) and key != "indexed_at"]
    changed = old is None or any(old[key] != row[key] for key in compared)
    if old and not changed:
        row["indexed_at"] = old["indexed_at"]
    con.execute(
        "INSERT INTO entities(entity_id,kind,layer,path,title,summary,content,sha256,size,language,classification,status,aliases,evidence,indexed_at,source_missing) "
        "VALUES(:entity_id,:kind,:layer,:path,:title,:summary,:content,:sha256,:size,:language,:classification,:status,:aliases,:evidence,:indexed_at,:source_missing) "
        "ON CONFLICT(entity_id) DO UPDATE SET kind=excluded.kind,layer=excluded.layer,path=excluded.path,title=excluded.title,summary=excluded.summary,content=excluded.content,sha256=excluded.sha256,size=excluded.size,language=excluded.language,classification=excluded.classification,status=excluded.status,aliases=excluded.aliases,evidence=excluded.evidence,indexed_at=excluded.indexed_at,source_missing=excluded.source_missing",
        row,
    )
    try:
        aliases = json.loads(row["aliases"])
    except (json.JSONDecodeError, TypeError):
        aliases = []
    con.execute("DELETE FROM entity_aliases WHERE entity_id=?", (row["entity_id"],))
    con.executemany("INSERT OR IGNORE INTO entity_aliases(entity_id,alias) VALUES(?,?)", [(row["entity_id"], str(alias)) for alias in aliases if alias])
    if changed and fts:
        con.execute("DELETE FROM entity_search WHERE entity_id=?", (row["entity_id"],))
        con.execute(
            "INSERT INTO entity_search(entity_id,layer,title,path,summary,content,aliases) VALUES(?,?,?,?,?,?,?)",
            (row["entity_id"], row["layer"], row["title"], row["path"], row["summary"], row["content"], row["aliases"]),
        )


def git_info() -> dict:
    try:
        top = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
        head = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
        return {"repository_root": top, "head": head}
    except (subprocess.CalledProcessError, FileNotFoundError):
        return {"repository_root": None, "head": None}


def refresh_snapshot() -> dict:
    repo = repo_identity()
    IDENTITY_LEDGER.parent.mkdir(parents=True, exist_ok=True)
    if not IDENTITY_LEDGER.exists():
        IDENTITY_LEDGER.touch()
    files, exclusions = walk_files()
    file_ids = read_identity_map(files, repo["repository_uuid"])
    # Binding assertions change the relationship ledger. Re-scan once so the
    # published snapshot uses the committed ledger hash and a consistent view.
    files, exclusions = walk_files()
    file_ids.update(read_identity_map(files, repo["repository_uuid"]))
    identity_states, _ = read_identities()
    journey = parse_ledger_records()
    created_at = utc_now()
    manifest_rows = [{"path": f["path"], "sha256": f["sha256"], "size": f["size"], "language": f["language"]} for f in files]
    manifest_hash = sha256_bytes(json.dumps(manifest_rows, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    snapshot_id = "snap-" + created_at.replace(":", "").replace("-", "") + "-" + manifest_hash[:10] + "-" + uuid.uuid4().hex[:8]
    current_paths = {f["path"] for f in files}
    con, fts = db_connect()
    try:
        previous = latest_snapshot(con)
        if previous and previous.get("repository_uuid") == repo["repository_uuid"] and previous.get("runtime") == RUNTIME_VERSION and previous.get("files") == manifest_rows and previous.get("coverage", {}).get("excluded_examples") == exclusions[:200]:
            con.close()
            return previous
        con.execute("BEGIN IMMEDIATE")
        existing_active = con.execute("SELECT entity_id,path FROM entities WHERE kind='file' AND status='active'").fetchall()
        for old in existing_active:
            if old["path"] not in current_paths:
                con.execute("UPDATE entities SET status='deleted', indexed_at=? WHERE entity_id=?", (created_at, old["entity_id"]))
                if fts:
                    con.execute("DELETE FROM entity_search WHERE entity_id=?", (old["entity_id"],))
        path_to_id = {path: file_ids[path] for path in current_paths if path in file_ids}
        for idx, item in enumerate(files, 1):
            path = item["path"]
            p = ROOT / path
            summary, summary_basis, source_line = responsible_summary(p, item["text"], item["binary"])
            symbols = extract_symbols(p, item["text"])
            body = item["text"][:MAX_TEXT_BYTES]
            if item["binary"]:
                body = "Binary asset. File content is not indexed."
            elif symbols:
                body += "\n\nSymbols: " + ", ".join(symbols)
            old = con.execute("SELECT aliases FROM entities WHERE entity_id=?", (path_to_id[path],)).fetchone()
            aliases = [*identity_states.get(path_to_id[path], {}).get("aliases", []), path, PurePosixPath(path).name, *symbols]
            if old:
                try:
                    aliases = list(dict.fromkeys(json.loads(old["aliases"]) + aliases))
                except (json.JSONDecodeError, TypeError):
                    pass
            row = {
                "entity_id": path_to_id[path], "kind": "synthetic_fixture" if path.startswith(".agent/atlas/examples/") else "file", "layer": "code", "path": path,
                "title": PurePosixPath(path).name, "summary": summary, "content": body,
                "sha256": item["sha256"], "size": item["size"], "language": item["language"],
                "classification": classification_for(path, idx), "status": "active",
                "aliases": json.dumps(aliases, ensure_ascii=False),
                "evidence": json.dumps({
                    "summary_basis": summary_basis, "source_path": path, "source_line": source_line,
                    "source_sha256": item["sha256"], "extractor": RUNTIME_VERSION,
                    "extractor_coverage": "inventory/hash; text index" if not item["binary"] else "inventory/hash only; content omitted",
                    "synthetic_fixture": path.startswith(".agent/atlas/examples/"),
                    "semantic_review": "see setup coverage report",
                }, ensure_ascii=False),
                "indexed_at": created_at, "source_missing": 0,
            }
            upsert_entity(con, fts, row)
        present_journey_ids = {record["entity_id"] for record in journey}
        for rec in journey:
            upsert_entity(con, fts, {k: rec[k] for k in (
                "entity_id", "kind", "layer", "path", "title", "summary", "content", "sha256",
                "size", "language", "classification", "status", "aliases", "evidence", "indexed_at", "source_missing"
            )})
        old_checkpoints = con.execute("SELECT entity_id FROM entities WHERE kind=? AND path LIKE '.agent/CURRENT.md#%'", (JOURNEY_KIND,)).fetchall()
        for old in old_checkpoints:
            if old["entity_id"] not in present_journey_ids:
                con.execute("DELETE FROM entities WHERE entity_id=?", (old["entity_id"],))
                if fts:
                    con.execute("DELETE FROM entity_search WHERE entity_id=?", (old["entity_id"],))
        con.execute("DELETE FROM edges")
        edges = []
        all_ids_by_path = {row["path"]: row["entity_id"] for row in con.execute("SELECT entity_id,path FROM entities WHERE kind='file' AND status='active'")}
        for assertion in parse_relationships():
            source = assertion.get("source_id")
            target = assertion.get("target_id")
            relation = assertion.get("relation")
            if source and target and relation:
                edges.append({
                    "edge_id": assertion.get("assertion_id") or edge_id(source, target, relation, assertion.get("evidence_path", "")),
                    "source_id": source, "target_id": target, "relation": relation,
                    "evidence": assertion.get("evidence", "canonical relationship assertion"),
                    "evidence_path": assertion.get("evidence_path", ".agent/atlas/RELATIONSHIPS.jsonl"),
                    "evidence_hash": assertion.get("evidence_sha256", ""),
                    "epistemic_status": assertion.get("epistemic_status", "DOCUMENTED"),
                })
        existing_paths = set(all_ids_by_path)
        for item in files:
            if item["path"].startswith(".agent/atlas/"):
                continue
            source_id = path_to_id.get(item["path"])
            if not source_id or item["binary"]:
                continue
            links = markdown_links(item["path"], item["text"], existing_paths)
            links.extend(python_imports(item["path"], item["text"], existing_paths))
            for target_path, relation, evidence in links:
                target_id = all_ids_by_path.get(target_path)
                if target_id:
                    edges.append({
                        "edge_id": edge_id(source_id, target_id, relation, item["path"]),
                        "source_id": source_id, "target_id": target_id, "relation": relation,
                        "evidence": evidence, "evidence_path": item["path"],
                        "evidence_hash": item["sha256"], "epistemic_status": "DOCUMENTED" if relation == "references" else "SYNTAX_DERIVED",
                    })
        for edge in edges:
            con.execute(
                "INSERT OR IGNORE INTO edges(edge_id,source_id,target_id,relation,evidence,evidence_path,evidence_hash,epistemic_status) VALUES(:edge_id,:source_id,:target_id,:relation,:evidence,:evidence_path,:evidence_hash,:epistemic_status)",
                edge,
            )
        counts = {
            "eligible_files": len(files),
            "inventoried_files": len(files),
            "text_indexed_files": sum(1 for f in files if not f["binary"]),
            "binary_or_large_files": sum(1 for f in files if f["binary"]),
            "journey_records": len(journey),
            "synthetic_fixture_files": sum(1 for f in files if f["path"].startswith(".agent/atlas/examples/")),
            "edges": len(edges),
            "excluded_paths": len(exclusions),
        }
        coverage = {
            **counts,
            "eligible_scope": "all regular files under workspace root, excluding VCS/build/cache directories, setup evidence, generated Atlas database/snapshots, and filesystem symlinks; binary or >2 MiB files are inventoried and hashed but not text-indexed",
            "semantic_relationship_coverage": "Python AST imports and explicit Markdown local links only; dynamic/runtime/configuration relationships remain unknown unless asserted",
            "responsibility_summary_coverage": "all eligible text files receive a source-based heading/docstring/declaration summary; full human review is captured separately in setup coverage report",
            "fts5": fts,
            "excluded_examples": exclusions[:200],
        }
        manifest = {
            "snapshot_id": snapshot_id,
            "created_at": created_at,
            "repository_uuid": repo["repository_uuid"],
            "root": str(ROOT),
            "git": git_info(),
            "runtime": RUNTIME_VERSION,
            "coverage": coverage,
            "files": manifest_rows,
            "journey_entity_ids": [item["entity_id"] for item in journey],
        }
        con.execute("INSERT INTO snapshots(snapshot_id,created_at,repository_uuid,manifest_json) VALUES(?,?,?,?)", (snapshot_id, created_at, repo["repository_uuid"], json.dumps(manifest, ensure_ascii=False)))
        con.execute("INSERT INTO runtime_meta(key,value) VALUES('latest_snapshot',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (snapshot_id,))
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()
    safe_atomic_json(SNAPSHOT_PATH, manifest)
    return manifest


def latest_snapshot(con: sqlite3.Connection) -> dict | None:
    latest = con.execute("SELECT value FROM runtime_meta WHERE key='latest_snapshot'").fetchone()
    if latest:
        row = con.execute("SELECT manifest_json FROM snapshots WHERE snapshot_id=?", (latest[0],)).fetchone()
    else:
        row = con.execute("SELECT manifest_json FROM snapshots ORDER BY created_at DESC, rowid DESC LIMIT 1").fetchone()
    return json.loads(row[0]) if row else None


def status_report(refresh: bool = False) -> dict:
    if refresh:
        refresh_snapshot()
    if not DB_PATH.exists():
        return {"ready": False, "message": "No Atlas snapshot. Run refresh."}
    con, fts = db_connect()
    try:
        snapshot = latest_snapshot(con)
        rows = con.execute("SELECT layer,kind,status,COUNT(*) AS n FROM entities GROUP BY layer,kind,status").fetchall()
        return {
            "ready": snapshot is not None,
            "snapshot": snapshot,
            "entity_counts": [dict(row) for row in rows],
            "fts5": fts,
            "database_integrity": con.execute("PRAGMA integrity_check").fetchone()[0],
            "database_path": str(DB_PATH.relative_to(ROOT)),
        }
    finally:
        con.close()


def query_entities(query: str, layer: str = "both", limit: int = 20, focus_id: str | None = None, cursor: str | None = None) -> dict:
    if len(query) > MAX_QUERY:
        raise ValueError(f"Query is limited to {MAX_QUERY} characters")
    limit = max(1, min(int(limit), MAX_RESULTS))
    try:
        offset = max(0, min(int(cursor or "0"), 500))
    except ValueError as exc:
        raise ValueError("cursor must be a non-negative result offset") from exc
    scan_limit = min(550, offset + limit + 1)
    if layer not in {"journey", "code", "both"}:
        raise ValueError("layer must be journey, code, or both")
    con, fts = db_connect()
    try:
        snapshot = latest_snapshot(con)
        if not snapshot:
            return {"results": [], "snapshot_id": None, "fallback": True, "hint": "Run refresh, then search the canonical ledgers and live source."}
        filters = []
        params: list[object] = []
        if layer != "both":
            filters.append("e.layer=?")
            params.append(layer)
        if not query:
            order = "e.layer, e.classification, e.path"
            where = " AND ".join(filters) if filters else "1=1"
            sql = f"SELECT e.* FROM entities e WHERE {where} AND e.status='active' ORDER BY {order} LIMIT ?"
            rows = con.execute(sql, (*params, scan_limit)).fetchall()
        else:
            exact = con.execute(
                "SELECT DISTINCT e.* FROM entities e LEFT JOIN entity_aliases a ON a.entity_id=e.entity_id "
                "WHERE (e.entity_id=? OR e.path=? OR a.alias=? OR e.title=?) "
                + ("AND e.layer=? " if layer != "both" else "") + "ORDER BY CASE WHEN e.status='active' THEN 0 ELSE 1 END,e.path LIMIT ?",
                (query, query, query, query, *((layer,) if layer != "both" else ()), scan_limit),
            ).fetchall()
            rows = list(exact)
            seen = {row["entity_id"] for row in rows}
            remaining = scan_limit - len(rows)
            if remaining > 0 and fts:
                tokens = re.findall(r"[\w./:-]+", query, flags=re.UNICODE)
                match = " AND ".join('"' + token.replace('"', '""') + '"*' for token in tokens[:12])
                if match:
                    fts_sql = "SELECT e.* FROM entity_search s JOIN entities e ON e.entity_id=s.entity_id WHERE entity_search MATCH ?"
                    fts_params: list[object] = [match]
                    if layer != "both":
                        fts_sql += " AND e.layer=?"
                        fts_params.append(layer)
                    fts_sql += " AND e.status='active' ORDER BY bm25(entity_search), e.path LIMIT ?"
                    fts_params.append(remaining * 3)
                    try:
                        found = con.execute(fts_sql, fts_params).fetchall()
                    except sqlite3.OperationalError:
                        found = []
                    for row in found:
                        if row["entity_id"] not in seen:
                            rows.append(row)
                            seen.add(row["entity_id"])
                        if len(rows) >= scan_limit:
                            break
            if len(rows) < limit:
                like_terms = ["%" + token + "%" for token in re.findall(r"[\w./:-]+", query, flags=re.UNICODE)[:4]]
                if like_terms:
                    clauses = " AND ".join("(e.title LIKE ? OR e.path LIKE ? OR e.summary LIKE ? OR e.content LIKE ? OR e.aliases LIKE ?)" for _ in like_terms)
                    fallback_params: list[object] = []
                    for term in like_terms:
                        fallback_params.extend([term] * 5)
                    fallback_sql = "SELECT e.* FROM entities e WHERE " + clauses
                    if layer != "both":
                        fallback_sql += " AND e.layer=?"
                        fallback_params.append(layer)
                    fallback_sql += " AND e.status='active' ORDER BY e.layer,e.path LIMIT ?"
                    fallback_params.append(scan_limit)
                    for row in con.execute(fallback_sql, fallback_params).fetchall():
                        if row["entity_id"] not in seen:
                            rows.append(row)
                            seen.add(row["entity_id"])
                        if len(rows) >= scan_limit:
                            break
        has_more = len(rows) > offset + limit
        page = rows[offset:offset + limit]
        results = [serialize_entity(row, snapshot["snapshot_id"]) for row in page]
        if focus_id and focus_id not in {row["entity_id"] for row in page}:
            focused = con.execute("SELECT * FROM entities WHERE entity_id=? OR aliases LIKE ? LIMIT 1", (focus_id, "%" + focus_id + "%")).fetchone()
            if focused:
                results.insert(0, serialize_entity(focused, snapshot["snapshot_id"]))
                results = results[:limit]
        return {
            "query": query, "layer": layer, "snapshot_id": snapshot["snapshot_id"],
            "created_at": snapshot["created_at"], "results": results,
            "limit": limit, "result_count": len(results), "has_more": has_more,
            "next_cursor": str(offset + limit) if has_more else None,
            "cursor": str(offset), "fts5": fts,
            "fallback": not results,
            "hint": "No indexed hit is not proof of absence. Search the canonical ledgers with rg and inspect live source." if not results else None,
        }
    finally:
        con.close()


def serialize_entity(row: sqlite3.Row, snapshot_id: str) -> dict:
    try:
        evidence = json.loads(row["evidence"])
    except (json.JSONDecodeError, TypeError):
        evidence = {"raw": row["evidence"]}
    try:
        aliases = json.loads(row["aliases"])
    except (json.JSONDecodeError, TypeError):
        aliases = []
    return {
        "entity_id": row["entity_id"], "kind": row["kind"], "layer": row["layer"],
        "path": row["path"], "title": row["title"], "summary": row["summary"],
        "sha256": row["sha256"], "size": row["size"], "language": row["language"],
        "classification": row["classification"], "status": row["status"],
        "aliases": aliases, "evidence": evidence, "snapshot_id": snapshot_id,
        "source_missing": bool(row["source_missing"]),
    }


def graph_data(layer: str = "both", limit: int = 120, hide_closed: bool = True) -> dict:
    if layer not in {"journey", "code", "both"}:
        raise ValueError("layer must be journey, code, or both")
    limit = max(1, min(int(limit), 240))
    con, fts = db_connect()
    try:
        snapshot = latest_snapshot(con)
        if not snapshot:
            return {"nodes": [], "edges": [], "snapshot_id": None, "active_route": []}
        where = "status='active'"
        if layer != "both":
            where += " AND layer=?"
            params: tuple = (layer, limit)
            sql = f"SELECT * FROM entities WHERE {where} ORDER BY classification,path LIMIT ?"
        else:
            params = (limit,)
            sql = f"SELECT * FROM entities WHERE {where} ORDER BY layer,classification,path LIMIT ?"
        rows = con.execute(sql, params).fetchall()
        node_ids = {row["entity_id"] for row in rows}
        nodes = [serialize_entity(row, snapshot["snapshot_id"]) for row in rows]
        if hide_closed:
            nodes = [n for n in nodes if not (n["kind"] == JOURNEY_KIND and n["status"].upper() in {"ABANDONED", "CANCELLED", "SUPERSEDED", "MERGED"})]
            node_ids = {n["entity_id"] for n in nodes}
        edges = [dict(row) for row in con.execute("SELECT * FROM edges") if row["source_id"] in node_ids and row["target_id"] in node_ids]
        checkpoints = [r for r in nodes if r["path"].startswith(".agent/CURRENT.md#")]
        current = max(checkpoints, key=lambda r: r["evidence"].get("recorded_at", ""), default=None)
        active_route = []
        if current:
            active_task = next((a for a in current["aliases"] if a.startswith("D-")), None)
            if active_task:
                active_route = [n["entity_id"] for n in nodes if active_task in n["aliases"] or active_task in n["path"]]
        return {
            "snapshot_id": snapshot["snapshot_id"], "created_at": snapshot["created_at"],
            "nodes": nodes, "edges": edges, "active_route": active_route,
            "counts": snapshot["coverage"], "fts5": fts,
        }
    finally:
        con.close()


def context_packet(entity_id: str, depth: int = 1, limit: int = 20) -> dict:
    depth = max(0, min(int(depth), 2))
    limit = max(1, min(int(limit), 50))
    con, _ = db_connect()
    try:
        snapshot = latest_snapshot(con)
        first = con.execute("SELECT * FROM entities WHERE entity_id=? LIMIT 1", (entity_id,)).fetchone()
        if not first:
            first = con.execute("SELECT e.* FROM entities e LEFT JOIN entity_aliases a ON a.entity_id=e.entity_id WHERE e.kind=? AND (a.alias=? OR e.path=?) ORDER BY e.path LIMIT 1", (JOURNEY_KIND, entity_id, entity_id)).fetchone()
        if not first:
            first = con.execute("SELECT e.* FROM entities e LEFT JOIN entity_aliases a ON a.entity_id=e.entity_id WHERE e.status='active' AND (a.alias=? OR e.path=?) ORDER BY e.path LIMIT 1", (entity_id, entity_id)).fetchone()
        if not first:
            return {"found": False, "snapshot_id": snapshot.get("snapshot_id") if snapshot else None, "hint": "Search canonical ledgers and live source; no-hit is not absence."}
        selected = {first["entity_id"]: first}
        frontier = {first["entity_id"]}
        for _ in range(depth):
            next_frontier = set()
            for current in frontier:
                for edge in con.execute("SELECT source_id,target_id FROM edges WHERE source_id=? OR target_id=?", (current, current)):
                    other = edge["target_id"] if edge["source_id"] == current else edge["source_id"]
                    if other not in selected:
                        row = con.execute("SELECT * FROM entities WHERE entity_id=? AND status='active'", (other,)).fetchone()
                        if row:
                            selected[other] = row
                            next_frontier.add(other)
            frontier = next_frontier
            if not frontier or len(selected) >= limit:
                break
        ids = set(selected)
        edges = [dict(row) for row in con.execute("SELECT * FROM edges") if row["source_id"] in ids and row["target_id"] in ids]
        return {"found": True, "snapshot_id": snapshot["snapshot_id"] if snapshot else None, "depth": depth, "entities": [serialize_entity(r, snapshot["snapshot_id"]) for r in list(selected.values())[:limit]], "edges": edges}
    finally:
        con.close()


def append_markdown_record(ledger: str, entry_id: str, record_type: str, status: str, summary: str, evidence: str, next_step: str) -> bool:
    allowed = {
        "record": AGENT / "RECORD.md", "outcome": AGENT / "OUTCOMES.md",
        "directive": AGENT / "DIRECTIVES.md", "learning": AGENT / "LEARNINGS.md",
        "external": AGENT / "EXTERNAL.md",
    }
    if ledger not in allowed:
        raise ValueError("ledger must be one of: record, outcome, directive, learning, external")
    if not re.fullmatch(r"[A-Za-z0-9._:-]{1,100}", entry_id):
        raise ValueError("entry id contains unsupported characters")
    path = allowed[ledger]
    path.parent.mkdir(parents=True, exist_ok=True)
    block = (
        f"\n## {entry_id} — {record_type}\n\n"
        f"ENTRY_ID: {entry_id}\nUTC: {utc_now()}\nACTOR: authority-cli\nTYPE: {record_type}\n"
        f"GOAL: see .agent/GOAL.md\nTASK: see .agent/CURRENT.md\nSTATUS: {status}\nSUPERSEDES: none\n"
        f"EVIDENCE: {evidence}\nSUMMARY: {summary}\nNEXT: {next_step}\n"
    )
    encoded = block.encode("utf-8")
    with path.open("a+b") as stream:
        fcntl.flock(stream.fileno(), fcntl.LOCK_EX)
        stream.seek(0)
        existing = stream.read()
        if f"ENTRY_ID: {entry_id}".encode() in existing:
            return False
        if existing and not existing.endswith(b"\n"):
            raise RuntimeError(f"Refusing append because canonical ledger has an incomplete final line: {relative(path)}")
        stream.seek(0, os.SEEK_END)
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())
    return True


def audit_runtime() -> dict:
    status = status_report(refresh=True)
    issues = []
    for rel in CANONICAL_LEDGERS:
        path = ROOT / rel
        if path.exists() and path.read_bytes() and not path.read_bytes().endswith(b"\n"):
            issues.append({"path": rel, "issue": "missing trailing newline; history preserved"})
    rel_rows = jsonl_records(IDENTITY_LEDGER)
    identity_keys = [row.get("idempotency_key") for row in rel_rows if row.get("record_type") == "IDENTITY_BINDING"]
    if len(identity_keys) != len(set(identity_keys)):
        issues.append({"path": relative(IDENTITY_LEDGER), "issue": "duplicate identity idempotency key"})
    return {"status": "PASSED" if not issues and status.get("database_integrity") == "ok" else "FAILED", "issues": issues, "snapshot": status.get("snapshot"), "database_integrity": status.get("database_integrity")}


def receipt(phase: str, status: str, detail: str, evidence: list[str] | None = None) -> None:
    run = AUTH / "setup" / "setup-20261113T135057Z"
    event = {
        "event_id": str(uuid.uuid4()), "recorded_at": utc_now(), "phase": phase,
        "status": status, "detail": detail, "evidence": evidence or [],
        "runtime": RUNTIME_VERSION,
    }
    append_jsonl(run / "receipts.jsonl", event, f"setup:{phase}:{status}:{detail}")


def viewer_html() -> bytes:
    return VIEWER_FILE.read_bytes()


class Handler(BaseHTTPRequestHandler):
    server_version = "AuthorityAtlas/0.1"

    def _send(self, status: int, data: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _json(self, status: int, value: object) -> None:
        self._send(status, json.dumps(value, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

    def do_GET(self) -> None:  # noqa: N802
        try:
            parsed = urlparse(self.path)
            query = parse_qs(parsed.query)
            if parsed.path == "/":
                self._send(200, viewer_html(), "text/html; charset=utf-8")
            elif parsed.path == "/api/status":
                refresh_snapshot()
                self._json(200, status_report())
            elif parsed.path == "/api/search":
                refresh_snapshot()
                self._json(200, query_entities(query.get("query", [""])[0], query.get("layer", ["both"])[0], int(query.get("limit", ["20"])[0]), query.get("focus", [None])[0], query.get("cursor", [None])[0]))
            elif parsed.path == "/api/graph":
                refresh_snapshot()
                self._json(200, graph_data(query.get("layer", ["both"])[0], int(query.get("limit", ["120"])[0]), query.get("hide_closed", ["true"])[0].lower() != "false"))
            elif parsed.path == "/api/context":
                refresh_snapshot()
                self._json(200, context_packet(query.get("id", [""])[0], int(query.get("depth", ["1"])[0]), int(query.get("limit", ["20"])[0])))
            else:
                self._json(404, {"error": "not found"})
        except (ValueError, RuntimeError, sqlite3.Error, OSError) as exc:
            self._json(400, {"error": str(exc)})

    def log_message(self, fmt: str, *args: object) -> None:
        print("[atlas-viewer] " + (fmt % args), file=sys.stderr)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Authority 6.0 repo-local Atlas and record tools")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("refresh", help="hash eligible files and atomically publish a new Atlas snapshot")
    sub.add_parser("status", help="show current snapshot and coverage")
    audit = sub.add_parser("audit", help="refresh and audit local ledgers/database integrity")
    search = sub.add_parser("search", help="search Journey, Code, or Both layers")
    search.add_argument("--query", required=True)
    search.add_argument("--layer", choices=("journey", "code", "both"), default="both")
    search.add_argument("--limit", type=int, default=20)
    context = sub.add_parser("context", help="retrieve a bounded entity neighborhood")
    context.add_argument("--id", required=True)
    context.add_argument("--depth", type=int, default=1)
    context.add_argument("--limit", type=int, default=20)
    add = sub.add_parser("record", help="append a safe canonical event")
    add.add_argument("--ledger", required=True, choices=("record", "outcome", "directive", "learning", "external"))
    add.add_argument("--id", required=True)
    add.add_argument("--type", required=True)
    add.add_argument("--status", default="RECORDED")
    add.add_argument("--summary", required=True)
    add.add_argument("--evidence", default="not supplied")
    add.add_argument("--next", dest="next_step", default="none")
    serve = sub.add_parser("serve", help="serve the read-only local Atlas viewer")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=4317)
    args = parser.parse_args(argv)
    try:
        if args.command == "refresh":
            print(json.dumps(refresh_snapshot(), indent=2, ensure_ascii=False))
        elif args.command == "status":
            print(json.dumps(status_report(refresh=True), indent=2, ensure_ascii=False))
        elif args.command == "audit":
            result = audit_runtime()
            print(json.dumps(result, indent=2, ensure_ascii=False))
            return 0 if result.get("status") == "PASSED" else 1
        elif args.command == "search":
            refresh_snapshot()
            print(json.dumps(query_entities(args.query, args.layer, args.limit), indent=2, ensure_ascii=False))
        elif args.command == "context":
            refresh_snapshot()
            print(json.dumps(context_packet(args.id, args.depth, args.limit), indent=2, ensure_ascii=False))
        elif args.command == "record":
            created = append_markdown_record(args.ledger, args.id, args.type, args.status, args.summary, args.evidence, args.next_step)
            print(json.dumps({"appended": created, "id": args.id, "ledger": args.ledger}))
        elif args.command == "serve":
            if args.port < 1024 or args.port > 65535:
                raise ValueError("port must be between 1024 and 65535")
            if args.host not in {"127.0.0.1", "localhost", "::1"}:
                raise ValueError("viewer binds to loopback only")
            refresh_snapshot()
            server = ThreadingHTTPServer((args.host, args.port), Handler)
            print(f"Authority Atlas listening at http://{args.host}:{args.port}", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
            finally:
                server.server_close()
        return 0
    except (ValueError, RuntimeError, sqlite3.Error, OSError, json.JSONDecodeError) as exc:
        print(f"authority: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
