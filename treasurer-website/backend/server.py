from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import time
import uuid
from pathlib import Path
from typing import Optional, Tuple
from flask_cors import CORS
import bcrypt

from flask import Flask, abort, jsonify, request, send_file
import os





app = Flask(__name__)
#CORS(app, resources={r"/*": {"origins": ["http://127.0.0.1:8000", "http://localhost:8000"]}})

# ----------------------------
# Configuration (simple + explicite)
# ----------------------------
LOCAL=False
# Dossier où se trouve ce fichier server.py
SCRIPT_DIR = Path(__file__).resolve().parent
# Tous les chemins deviennent relatifs à server.py
BASE_DIR = SCRIPT_DIR / "data"
EXPENSES_DIR = BASE_DIR / "expenses"
TRASH_DIR = BASE_DIR / "trash"
TMP_DIR = BASE_DIR / "tmp"

MEMBER_PASSWORD_HASH = os.environ.get("MEMBER_PASSWORD_HASH", "$2b$12$OqIVek8ubCzrTlepovIFnOGqJ/m0xdF0yoDt6fdb2sTcFgZ8//O02")
TREASURER_PASSWORD_HASH = os.environ.get("TREASURER_PASSWORD_HASH", "$2b$12$tjKBKo/AEVmDMtPWkslDdelMJiZmlahPmPfI8NfAwn3P.LKXUaEne")

# Limit upload size (protects you from accidental huge files / basic DoS)
# Example: 20 MB
app.config["MAX_CONTENT_LENGTH"] = int(os.environ.get("MAX_CONTENT_LENGTH", str(20 * 1024 * 1024)))

TRASH_MAX_ITEMS = 5
TRASH_NAME_RE = re.compile(r"^(?P<ts>\d{14})__(?P<id>.+)$")

# Allowed MIME types (keep it small and safe)
ALLOWED_SIGNATURE_MIME = {
    "image/png": "png",
    "image/jpeg": "jpg",
}
ALLOWED_INVOICE_MIME = {
    "application/pdf": "pdf",
    "image/png": "png",
    "image/jpeg": "jpg",
}

# Required fields in the member JSON payload (you can expand later)
REQUIRED_TEXT_FIELDS = [
    "last_name",     # nom
    "first_name",    # prénom
    "date",          # date string (you can enforce ISO later)
    "purpose",       # motif
    "designation",   # désignation
    "amount",        # montant (string or number)
]


# ----------------------------
# Helpers (filesystem + auth)
# ----------------------------
def ensure_dirs() -> None:
    BASE_DIR.mkdir(parents=True, exist_ok=True)
    EXPENSES_DIR.mkdir(parents=True, exist_ok=True)
    TRASH_DIR.mkdir(parents=True, exist_ok=True)
    TMP_DIR.mkdir(parents=True, exist_ok=True)


def now_ts() -> str:
    # YYYYMMDDHHMMSS - easy to sort for FIFO
    return time.strftime("%Y%m%d%H%M%S", time.localtime())


def require_password_hash(expected_hash: str) -> None:
    provided = request.headers.get("X-Api-Key", "")

    if not expected_hash:
        abort(500, description="Server misconfigured: password hash is missing")

    if not provided:
        abort(401, description="Unauthorized")

    ok = bcrypt.checkpw(provided.encode("utf-8"), expected_hash.encode("utf-8"))
    if not ok:
        abort(401, description="Unauthorized")


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def safe_load_member_json(data_str: str) -> dict:
    try:
        data = json.loads(data_str)
    except Exception:
        abort(400, description="Field 'data' must be valid JSON")

    if not isinstance(data, dict):
        abort(400, description="Field 'data' must be a JSON object")

    missing = [k for k in REQUIRED_TEXT_FIELDS if k not in data or data[k] in (None, "")]
    if missing:
        abort(400, description=f"Missing required text fields: {', '.join(missing)}")

    return data


def get_extension_from_mime(mime: str, allowed: dict) -> str:
    ext = allowed.get(mime)
    if not ext:
        abort(400, description=f"Unsupported file type: {mime}")
    return ext


def find_file_by_prefix(dir_path: Path, prefix: str) -> Optional[Path]:
    # Example: prefix="invoice." finds invoice.pdf or invoice.png
    for p in dir_path.iterdir():
        if p.is_file() and p.name.startswith(prefix):
            return p
    return None


def extract_id_from_trash_folder(name: str) -> Optional[str]:
    m = TRASH_NAME_RE.match(name)
    if not m:
        return None
    return m.group("id")


def list_trash_items_sorted_oldest_first() -> list[Path]:
    items = [p for p in TRASH_DIR.iterdir() if p.is_dir()]

    def key(p: Path) -> str:
        m = TRASH_NAME_RE.match(p.name)
        return m.group("ts") if m else "00000000000000"

    return sorted(items, key=key)


def trim_trash_fifo(max_items: int = TRASH_MAX_ITEMS) -> int:
    items = list_trash_items_sorted_oldest_first()
    while len(items) > max_items:
        victim = items.pop(0)
        shutil.rmtree(victim, ignore_errors=True)
    return len(items)


def remove_existing_trash_for_id(expense_id: str) -> None:
    # "Ultra simpliste": if trash already contains an entry with same id, delete it.
    for p in TRASH_DIR.iterdir():
        if not p.is_dir():
            continue
        pid = extract_id_from_trash_folder(p.name)
        if pid == expense_id:
            shutil.rmtree(p, ignore_errors=True)


def atomic_rename(src: Path, dst: Path) -> None:
    # On same filesystem, os.rename is atomic -> the folder is either fully moved or not moved.
    try:
        os.rename(src, dst)
    except OSError as e:
        abort(500, description=f"Filesystem rename failed: {e}")


# ----------------------------
# Member endpoints
# ----------------------------
@app.post("/member/expenses")
def member_create_expense_json_only():
    """
    Step 1 (robust): receive ONLY JSON (application/json).
    Creates:
      data/expenses/<id>/text.json
      data/expenses/<id>/meta.json (optional)
    Files are uploaded in step 2.
    """
    require_password_hash(MEMBER_PASSWORD_HASH)

    if not request.is_json:
        abort(415, description="Content-Type must be application/json")

    payload = request.get_json(silent=True)
    if payload is None:
        abort(400, description="Invalid JSON body")

    if not isinstance(payload, dict):
        abort(400, description="JSON body must be an object")

    missing = [k for k in REQUIRED_TEXT_FIELDS if k not in payload or payload[k] in (None, "")]
    if missing:
        abort(400, description=f"Missing required text fields: {', '.join(missing)}")

    expense_id = str(uuid.uuid4())
    folder = EXPENSES_DIR / expense_id
    folder.mkdir(parents=True, exist_ok=False)

    text_path = folder / "text.json"
    with text_path.open("w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    meta = {"id": expense_id, "created_at": now_ts()}
    meta_path = folder / "meta.json"
    with meta_path.open("w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    return jsonify({"id": expense_id})


@app.post("/member/expenses/<expense_id>/files")
def member_upload_files(expense_id: str):
    """
    Step 2: upload signature + invoice as multipart/form-data.
    Stores:
      data/expenses/<id>/signature.<ext>
      data/expenses/<id>/invoice.<ext>
    """
    require_password_hash(MEMBER_PASSWORD_HASH)

    folder = EXPENSES_DIR / expense_id
    if not folder.exists() or not folder.is_dir():
        abort(404, description="Expense not found")

    if "signature" not in request.files:
        abort(400, description="Missing file field: signature")
    if "invoice" not in request.files:
        abort(400, description="Missing file field: invoice")

    signature = request.files["signature"]
    invoice = request.files["invoice"]

    sig_mime = signature.mimetype or ""
    inv_mime = invoice.mimetype or ""

    sig_ext = get_extension_from_mime(sig_mime, ALLOWED_SIGNATURE_MIME)
    inv_ext = get_extension_from_mime(inv_mime, ALLOWED_INVOICE_MIME)

    # Remove previous files if re-upload (simple & deterministic)
    old_sig = find_file_by_prefix(folder, "signature.")
    old_inv = find_file_by_prefix(folder, "invoice.")
    if old_sig:
        old_sig.unlink(missing_ok=True)
    if old_inv:
        old_inv.unlink(missing_ok=True)

    sig_path = folder / f"signature.{sig_ext}"
    inv_path = folder / f"invoice.{inv_ext}"

    signature.save(sig_path)
    invoice.save(inv_path)

    # Update meta (optional)
    meta_path = folder / "meta.json"
    if meta_path.exists():
        try:
            with meta_path.open("r", encoding="utf-8") as f:
                meta = json.load(f)
        except Exception:
            meta = {}
    else:
        meta = {}

    meta["signature"] = {"mime": sig_mime, "stored_name": sig_path.name}
    meta["invoice"] = {"mime": inv_mime, "stored_name": inv_path.name}

    with meta_path.open("w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    return jsonify({"id": expense_id, "uploaded": True})


# ----------------------------
# Treasurer endpoints (list / detail / download)
# ----------------------------
@app.get("/treasurer/expenses")
def treasurer_list_expenses():
    """
    Returns: list of { id, text } only (no files).
    This is what your software uses to decide which expenses to approve.
    """
    require_password_hash(TREASURER_PASSWORD_HASH)

    out = []
    for p in EXPENSES_DIR.iterdir():
        if not p.is_dir():
            continue
        text_path = p / "text.json"
        if not text_path.exists():
            # Skip malformed entries
            continue
        try:
            with text_path.open("r", encoding="utf-8") as f:
                text = json.load(f)
            out.append({"id": p.name, "text": text})
        except Exception:
            continue

    # You can sort by folder name or by meta.json created_at; keep it simple for now
    out.sort(key=lambda x: x["id"])
    return jsonify(out)


@app.get("/treasurer/expenses/<expense_id>")
def treasurer_get_expense(expense_id: str):
    """
    Returns: { id, text, files: { signature_url, invoice_url } }

    This keeps the response lightweight while still telling the client where to GET the binaries.
    """
    require_password_hash(TREASURER_PASSWORD_HASH)

    folder = EXPENSES_DIR / expense_id
    if not folder.exists() or not folder.is_dir():
        abort(404, description="Expense not found")

    text_path = folder / "text.json"
    if not text_path.exists():
        abort(404, description="text.json not found")

    with text_path.open("r", encoding="utf-8") as f:
        text = json.load(f)

    sig_file = find_file_by_prefix(folder, "signature.")
    inv_file = find_file_by_prefix(folder, "invoice.")

    return jsonify(
        {
            "id": expense_id,
            "text": text,
            "files": {
                "signature_url": f"/treasurer/expenses/{expense_id}/signature" if sig_file else None,
                "invoice_url": f"/treasurer/expenses/{expense_id}/invoice" if inv_file else None,
            },
        }
    )


@app.get("/treasurer/expenses/<expense_id>/signature")
def treasurer_download_signature(expense_id: str):
    require_password_hash(TREASURER_PASSWORD_HASH)

    folder = EXPENSES_DIR / expense_id
    if not folder.exists() or not folder.is_dir():
        abort(404, description="Expense not found")

    sig_file = find_file_by_prefix(folder, "signature.")
    if not sig_file:
        abort(404, description="Signature not found")

    return send_file(sig_file, as_attachment=True, download_name=sig_file.name)


@app.get("/treasurer/expenses/<expense_id>/invoice")
def treasurer_download_invoice(expense_id: str):
    require_password_hash(TREASURER_PASSWORD_HASH)

    folder = EXPENSES_DIR / expense_id
    if not folder.exists() or not folder.is_dir():
        abort(404, description="Expense not found")

    inv_file = find_file_by_prefix(folder, "invoice.")
    if not inv_file:
        abort(404, description="Invoice not found")

    return send_file(inv_file, as_attachment=True, download_name=inv_file.name)


# ----------------------------
# Treasurer endpoints (trash)
# ----------------------------
@app.delete("/treasurer/expenses/<expense_id>")
def treasurer_delete_expense(expense_id: str):
    """
    Soft delete:
      expenses/<id>  -->  trash/<ts>__<id>

    "Ultra simpliste":
      - if trash contains an older entry for same <id>, we delete it (overwrite behavior)
      - keep only the last 5 deleted folders (FIFO by timestamp)
    """
    require_password_hash(TREASURER_PASSWORD_HASH)

    src = EXPENSES_DIR / expense_id
    if not src.exists() or not src.is_dir():
        abort(404, description="Expense not found")

    remove_existing_trash_for_id(expense_id)

    dst = TRASH_DIR / f"{now_ts()}__{expense_id}"
    atomic_rename(src, dst)

    trash_size = trim_trash_fifo(TRASH_MAX_ITEMS)
    return jsonify({"deleted_id": expense_id, "trash_size": trash_size})


@app.post("/treasurer/trash/restore")
def treasurer_restore_trash():
    """
    Restore everything from trash back to expenses.

    Refusal rule (as requested):
      - if expenses/<id> already exists, we do NOT restore that item (leave it in trash).
    """
    require_password_hash(TREASURER_PASSWORD_HASH)

    restored = []
    conflicts = []

    items = list_trash_items_sorted_oldest_first()
    for src in items:
        expense_id = extract_id_from_trash_folder(src.name)
        if not expense_id:
            continue

        dst = EXPENSES_DIR / expense_id
        if dst.exists():
            conflicts.append(expense_id)
            continue

        try:
            atomic_rename(src, dst)
            restored.append(expense_id)
        except Exception:
            conflicts.append(expense_id)

    return jsonify({"restored": restored, "conflicts": conflicts})


# ----------------------------
# Error formatting (clean JSON errors)
# ----------------------------
@app.errorhandler(400)
@app.errorhandler(401)
@app.errorhandler(404)
@app.errorhandler(413)
@app.errorhandler(500)
def handle_error(e):
    code = getattr(e, "code", 500)
    desc = getattr(e, "description", "Server error")
    return jsonify({"error": desc, "status": code}), code


# ----------------------------
# Local HTTPS run (dev-friendly)
# ----------------------------
if __name__ == "__main__":
    print("Running server from:", SCRIPT_DIR)
    print("Data directory:", BASE_DIR.resolve())
    ensure_dirs()
    if LOCAL:
        host = os.environ.get("HOST", "127.0.0.1")
    else:
        host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "8443"))

    cert = os.environ.get("SSL_CERT_FILE")
    key = os.environ.get("SSL_KEY_FILE")

    # In dev: if you don't provide a cert/key, Flask can generate an adhoc self-signed cert.
    # In prod: you usually run behind a reverse proxy (nginx/caddy/traefik) doing HTTPS.
    if cert and key:
        ssl_context = (cert, key)
    else:
        ssl_context = "adhoc"
    if LOCAL:
        app.run(host="127.0.0.1", port=5000, debug=True)
    else:
        app.run(host=host, port=port, ssl_context=ssl_context)