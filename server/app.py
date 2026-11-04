# -*- coding: utf-8 -*-
"""
سرور «Classify» — Flask + SQLite

سه نقش:
  - super   : مدیر کل. کلاس می‌سازد و رمز مدیر هر کلاس را تعیین می‌کند.
  - manager : مدیر یک کلاس. لیست دانش‌آموزان، درس‌ها، برنامه، تکالیف و اعلان‌های همان کلاس را مدیریت می‌کند.
  - student : دانش‌آموز. با نام و نام خانوادگی (که مدیر کلاس در لیست وارد کرده) و انتخاب کلاس وارد می‌شود.
              هر نام به اولین دستگاهی که واردش شود قفل می‌شود؛ ورود از دستگاه دیگر کد تأیید می‌خواهد.

اجرا:  python app.py
"""
import hashlib
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import unicodedata
import uuid
from datetime import date
from functools import wraps

from flask import Flask, Response, g, jsonify, request, send_file
from itsdangerous import BadSignature, URLSafeSerializer
from werkzeug.security import check_password_hash, generate_password_hash

BASE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("CL_DB", os.path.join(BASE, "classify.db"))
CONFIG_PATH = os.environ.get("CL_CONFIG", os.path.join(BASE, "config.json"))
APPS_DIR = os.environ.get("CL_APPS_DIR", os.path.join(BASE, "apps"))

MAX_ITEMS = 1000
MAX_STUDENTS = 400
MAX_ANNOUNCEMENTS = 100
MAX_PHOTO_BYTES = 1_500_000
MAX_BLOCK_MINUTES = 365 * 24 * 60
CODE_TTL_SECONDS = 120
CODE_MAX_ATTEMPTS = 5
PRIORITIES = {"LOW", "NORMAL", "HIGH"}
REMINDERS = {"NONE", "DAY_BEFORE", "DUE_MORNING"}
PW_METHOD = "pbkdf2:sha256"
APP_FLAVORS = ("student", "manager")
MAX_APK_BYTES = 80 * 1024 * 1024
MAX_JSON_BYTES = 2 * 1024 * 1024
VERSION_NAME_RE = re.compile(r"\d+(\.\d+){0,3}")


# ---------------------------------------------------------------- config
def load_config():
    cfg = {}
    created = False
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, encoding="utf-8") as f:
            cfg = json.load(f)
    if "secret_key" not in cfg:
        cfg["secret_key"] = secrets.token_hex(32)
        created = True
    if "super_password" not in cfg:
        alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        cfg["super_password"] = "".join(secrets.choice(alphabet) for _ in range(10))
        created = True
    if "publish_token" not in cfg:
        cfg["publish_token"] = secrets.token_urlsafe(32)
        created = True
    if created:
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
        try:
            os.chmod(CONFIG_PATH, 0o600)
        except OSError:
            pass
    cfg["super_password"] = os.environ.get("CL_SUPER_PASSWORD", cfg["super_password"])
    cfg["publish_token"] = os.environ.get("CL_PUBLISH_TOKEN", cfg["publish_token"])
    cfg["_created"] = created
    return cfg


CONFIG = load_config()
signer = URLSafeSerializer(CONFIG["secret_key"], salt="classify-token")

app = Flask(__name__)
# بدنهٔ بزرگ فقط برای انتشار APK مجاز است؛ بقیهٔ مسیرها در before_request به ۲ مگابایت محدود می‌شوند
app.config["MAX_CONTENT_LENGTH"] = MAX_APK_BYTES + 5 * 1024 * 1024
app.json.ensure_ascii = False


# ---------------------------------------------------------------- helpers
@app.before_request
def limit_body_size():
    if request.method in ("POST", "PUT", "PATCH"):
        if request.path.startswith("/api/app/") and request.path.endswith("/publish"):
            return None
        cl = request.content_length
        if (cl is not None and cl > MAX_JSON_BYTES) or (cl is None and request.headers.get("Transfer-Encoding")):
            return err("too_large", 413)
    return None


def now():
    return int(time.time())


def err(key, status, **extra):
    return jsonify(error=key, **extra), status


def body_dict():
    b = request.get_json(silent=True)
    return b if isinstance(b, dict) else {}


def client_ip():
    if os.environ.get("CL_TRUST_PROXY") == "1":
        return request.headers.get("X-Forwarded-For", request.remote_addr or "?").split(",")[0].strip()
    return request.remote_addr or "?"


# --- name matching: اختلاف «ی/ي»، «ک/ك»، نیم‌فاصله، فاصله، آ/ا و اعراب نادیده گرفته می‌شود
_STRIP = re.compile("[\u064b-\u065f\u0670\u0640\u200c\u200d\u200e\u200f\\s]+")
_MAP = str.maketrans({
    "ي": "ی", "ى": "ی", "ك": "ک", "ۀ": "ه", "ة": "ه",
    "آ": "ا", "أ": "ا", "إ": "ا",
})


def name_key(first, last):
    s = unicodedata.normalize("NFKC", f"{first} {last}")
    s = s.translate(_MAP).casefold()
    return _STRIP.sub("", s)


def clean_name(v):
    if not isinstance(v, str):
        return ""
    return " ".join(v.split())


# ---------------------------------------------------------------- brute-force guard
_fail_lock = threading.Lock()
_fails = {}  # (ip, bucket) -> [timestamps]
LIMITS = {"pw": 10, "name": 60, "code": 30}


def too_many(bucket):
    key = (client_ip(), bucket)
    t = time.time()
    with _fail_lock:
        lst = [x for x in _fails.get(key, []) if t - x < 300]
        _fails[key] = lst
        return len(lst) >= LIMITS[bucket]


def record_fail(bucket):
    with _fail_lock:
        _fails.setdefault((client_ip(), bucket), []).append(time.time())


# ---------------------------------------------------------------- db
SCHEMA = """
CREATE TABLE IF NOT EXISTS classes(
    id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, pw_hash TEXT NOT NULL,
    rev INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS students(
    id TEXT PRIMARY KEY, class_id TEXT NOT NULL, first_name TEXT NOT NULL, last_name TEXT NOT NULL,
    name_key TEXT NOT NULL, device TEXT, blocked_until INTEGER NOT NULL DEFAULT 0,
    UNIQUE(class_id, name_key));
CREATE TABLE IF NOT EXISTS pair_codes(
    student_id TEXT PRIMARY KEY, code TEXT NOT NULL, expires INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS courses(
    class_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, teacher TEXT NOT NULL DEFAULT '',
    color INTEGER NOT NULL DEFAULT 0, photo_ver INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(class_id, id));
CREATE TABLE IF NOT EXISTS slots(
    class_id TEXT NOT NULL, id TEXT NOT NULL, course_id TEXT NOT NULL, day INTEGER NOT NULL,
    start INTEGER NOT NULL, "end" INTEGER NOT NULL, room TEXT NOT NULL DEFAULT '', PRIMARY KEY(class_id, id));
CREATE TABLE IF NOT EXISTS homework(
    class_id TEXT NOT NULL, id TEXT NOT NULL, course_id TEXT NOT NULL, title TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '', due TEXT NOT NULL, priority TEXT NOT NULL DEFAULT 'NORMAL',
    reminder TEXT NOT NULL DEFAULT 'NONE', PRIMARY KEY(class_id, id));
CREATE TABLE IF NOT EXISTS announcements(
    class_id TEXT NOT NULL, id TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL,
    PRIMARY KEY(class_id, id));
CREATE TABLE IF NOT EXISTS photos(
    class_id TEXT NOT NULL, course_id TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(class_id, course_id));
"""


def connect():
    con = sqlite3.connect(DB_PATH, timeout=10, isolation_level=None)
    con.row_factory = sqlite3.Row
    return con


def db():
    if "db" not in g:
        g.db = connect()
    return g.db


@app.teardown_appcontext
def close_db(_exc):
    con = g.pop("db", None)
    if con is not None:
        con.close()


def init_db():
    con = connect()
    con.executescript(SCHEMA)
    con.close()


def pw_fingerprint(pw_hash):
    return hashlib.sha256(pw_hash.encode()).hexdigest()[:16]


def super_fingerprint():
    return hashlib.sha256(("super|" + CONFIG["super_password"]).encode()).hexdigest()[:16]


def make_token(role, class_id=None, student_id=None, device=None, fp=""):
    return signer.dumps({"r": role, "c": class_id, "s": student_id, "d": device, "f": fp})


def read_all(con, cid):
    cls = con.execute("SELECT name, rev FROM classes WHERE id=?", (cid,)).fetchone()
    return {
        "rev": cls["rev"],
        "className": cls["name"],
        "courses": [
            {"id": r["id"], "name": r["name"], "teacher": r["teacher"], "color": r["color"], "photoVer": r["photo_ver"]}
            for r in con.execute("SELECT * FROM courses WHERE class_id=? ORDER BY rowid", (cid,))
        ],
        "slots": [
            {"id": r["id"], "courseId": r["course_id"], "day": r["day"],
             "start": r["start"], "end": r["end"], "room": r["room"]}
            for r in con.execute("SELECT * FROM slots WHERE class_id=? ORDER BY rowid", (cid,))
        ],
        "homework": [
            {"id": r["id"], "courseId": r["course_id"], "title": r["title"], "details": r["details"],
             "due": r["due"], "priority": r["priority"], "reminder": r["reminder"]}
            for r in con.execute("SELECT * FROM homework WHERE class_id=? ORDER BY rowid", (cid,))
        ],
        "announcements": [
            {"id": r["id"], "text": r["text"], "createdAt": r["created"] * 1000}
            for r in con.execute("SELECT * FROM announcements WHERE class_id=? ORDER BY created DESC", (cid,))
        ],
    }


# ---------------------------------------------------------------- auth
def auth(*roles):
    def deco(fn):
        @wraps(fn)
        def wrapper(*a, **kw):
            h = request.headers.get("Authorization", "")
            if not h.startswith("Bearer "):
                return err("unauthorized", 401)
            try:
                p = signer.loads(h[7:])
            except BadSignature:
                return err("unauthorized", 401)
            role = p.get("r")
            if role not in ("super", "manager", "student"):
                return err("unauthorized", 401)
            con = db()
            if role == "super":
                if not secrets.compare_digest(str(p.get("f", "")), super_fingerprint()):
                    return err("unauthorized", 401)
            else:
                cls = con.execute("SELECT * FROM classes WHERE id=?", (p.get("c"),)).fetchone()
                if cls is None:
                    return err("removed", 401)
                if role == "manager":
                    if not secrets.compare_digest(str(p.get("f", "")), pw_fingerprint(cls["pw_hash"])):
                        return err("unauthorized", 401)
                else:
                    st = con.execute("SELECT * FROM students WHERE id=? AND class_id=?",
                                     (p.get("s"), cls["id"])).fetchone()
                    if st is None:
                        return err("removed", 401)
                    if not st["device"] or st["device"] != p.get("d"):
                        return err("device_replaced", 401)
                    left = st["blocked_until"] - now()
                    if left > 0:
                        return err("blocked", 403, seconds=left)
                    g.student = st
                g.class_id = cls["id"]
            if role not in roles:
                return err("forbidden", 403)
            g.role = role
            return fn(*a, **kw)

        return wrapper

    return deco


# ---------------------------------------------------------------- validation
class Invalid(Exception):
    pass


def s(obj, key, maxlen, required=False):
    v = obj.get(key, "")
    if v is None:
        v = ""
    if not isinstance(v, str):
        raise Invalid(f"{key} must be a string")
    v = v.strip()
    if required and not v:
        raise Invalid(f"{key} is required")
    if len(v) > maxlen:
        raise Invalid(f"{key} too long")
    return v


def i(obj, key, lo, hi):
    v = obj.get(key)
    if isinstance(v, bool) or not isinstance(v, int) or not (lo <= v <= hi):
        raise Invalid(f"{key} must be an int in [{lo},{hi}]")
    return v


def validate(body):
    if not isinstance(body, dict):
        raise Invalid("body must be an object")
    lists = {}
    for k in ("courses", "slots", "homework", "announcements"):
        lst = body.get(k, [])
        limit = MAX_ANNOUNCEMENTS if k == "announcements" else MAX_ITEMS
        if not isinstance(lst, list) or len(lst) > limit:
            raise Invalid(f"{k} must be a list (max {limit})")
        if any(not isinstance(x, dict) for x in lst):
            raise Invalid(f"{k} items must be objects")
        lists[k] = lst

    courses, slots, hw, ann = [], [], [], []
    seen = set()
    for c in lists["courses"]:
        cid = s(c, "id", 64, True)
        if cid in seen:
            raise Invalid("duplicate course id")
        seen.add(cid)
        color = c.get("color", 0)
        courses.append((cid, s(c, "name", 100, True), s(c, "teacher", 100), color if isinstance(color, int) else 0))
    course_ids = seen

    seen = set()
    for x in lists["slots"]:
        sid = s(x, "id", 64, True)
        if sid in seen:
            raise Invalid("duplicate slot id")
        seen.add(sid)
        cid = s(x, "courseId", 64, True)
        if cid not in course_ids:
            continue
        st, en = i(x, "start", 0, 1440), i(x, "end", 0, 1440)
        slots.append((sid, cid, i(x, "day", 0, 6), st, en, s(x, "room", 100)))

    seen = set()
    for h in lists["homework"]:
        hid = s(h, "id", 64, True)
        if hid in seen:
            raise Invalid("duplicate homework id")
        seen.add(hid)
        cid = s(h, "courseId", 64, True)
        if cid not in course_ids:
            continue
        due = s(h, "due", 10, True)
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", due):
            raise Invalid("due must be YYYY-MM-DD")
        try:
            date.fromisoformat(due)
        except ValueError:
            raise Invalid("invalid due date")
        pr = h.get("priority", "NORMAL")
        rm = h.get("reminder", "NONE")
        hw.append((hid, cid, s(h, "title", 200, True), s(h, "details", 4000), due,
                   pr if pr in PRIORITIES else "NORMAL", rm if rm in REMINDERS else "NONE"))

    seen = set()
    for a in lists["announcements"]:
        aid = s(a, "id", 64, True)
        if aid in seen:
            raise Invalid("duplicate announcement id")
        seen.add(aid)
        created = a.get("createdAt", 0)
        if isinstance(created, bool) or not isinstance(created, int):
            created = 0
        # createdAt از دستگاه به میلی‌ثانیه می‌آید؛ به ثانیه ذخیره می‌شود
        ann.append((aid, s(a, "text", 1000, True), created // 1000 if created > 10**11 else created))
    return courses, slots, hw, ann


# ---------------------------------------------------------------- public
@app.get("/api/ping")
def ping():
    return jsonify(app="classify", ok=True)


@app.get("/api/classes")
def public_classes():
    rows = db().execute("SELECT id, name FROM classes ORDER BY name").fetchall()
    return jsonify(classes=[{"id": r["id"], "name": r["name"]} for r in rows])


# ---------------------------------------------------------------- login
@app.post("/api/login/super")
def login_super():
    if too_many("pw"):
        return err("too_many_attempts", 429)
    pw = body_dict().get("password")
    if not isinstance(pw, str) or not secrets.compare_digest(pw.strip().encode(), CONFIG["super_password"].encode()):
        record_fail("pw")
        return err("wrong_secret", 401)
    return jsonify(token=make_token("super", fp=super_fingerprint()), role="super", className="", name="")


@app.post("/api/login/manager")
def login_manager():
    if too_many("pw"):
        return err("too_many_attempts", 429)
    b = body_dict()
    pw = b.get("password")
    cls = db().execute("SELECT * FROM classes WHERE id=?", (b.get("classId"),)).fetchone()
    if cls is None:
        return err("class_not_found", 404)
    if not isinstance(pw, str) or not check_password_hash(cls["pw_hash"], pw.strip()):
        record_fail("pw")
        return err("wrong_secret", 401)
    token = make_token("manager", class_id=cls["id"], fp=pw_fingerprint(cls["pw_hash"]))
    return jsonify(token=token, role="manager", className=cls["name"], name="")


def _student_from_body(b):
    """(class_row, student_row, device) یا یک پاسخ خطا."""
    first, last = clean_name(b.get("firstName")), clean_name(b.get("lastName"))
    device = b.get("deviceId")
    if not first or not last or len(first) > 60 or len(last) > 60:
        return err("bad_request", 400)
    if not isinstance(device, str) or not re.fullmatch(r"[A-Za-z0-9-]{8,64}", device):
        return err("bad_request", 400)
    cls = db().execute("SELECT * FROM classes WHERE id=?", (b.get("classId"),)).fetchone()
    if cls is None:
        return err("class_not_found", 404)
    st = db().execute("SELECT * FROM students WHERE class_id=? AND name_key=?",
                      (cls["id"], name_key(first, last))).fetchone()
    if st is None:
        record_fail("name")
        return err("not_in_list", 404)
    left = st["blocked_until"] - now()
    if left > 0:
        return err("blocked", 403, seconds=left)
    return cls, st, device


def _student_token(cls, st, device):
    token = make_token("student", class_id=cls["id"], student_id=st["id"], device=device)
    return jsonify(token=token, role="student", className=cls["name"],
                   name=f'{st["first_name"]} {st["last_name"]}')


@app.post("/api/login/student")
def login_student():
    if too_many("name"):
        return err("too_many_attempts", 429)
    r = _student_from_body(body_dict())
    if not isinstance(r, tuple) or len(r) != 3:
        return r
    cls, st, device = r
    if not st["device"]:
        db().execute("UPDATE students SET device=? WHERE id=?", (device, st["id"]))
    elif st["device"] != device:
        return err("device_locked", 409)
    return _student_token(cls, st, device)


@app.post("/api/student/verify")
def student_verify():
    if too_many("code"):
        return err("too_many_attempts", 429)
    b = body_dict()
    r = _student_from_body(b)
    if not isinstance(r, tuple) or len(r) != 3:
        return r
    cls, st, device = r
    con = db()
    row = con.execute("SELECT * FROM pair_codes WHERE student_id=?", (st["id"],)).fetchone()
    code = str(b.get("code", "")).strip()
    if row is None or row["expires"] < now() or row["attempts"] >= CODE_MAX_ATTEMPTS:
        if row is not None:
            con.execute("DELETE FROM pair_codes WHERE student_id=?", (st["id"],))
        record_fail("code")
        return err("bad_code", 400)
    if not secrets.compare_digest(code.encode(), row["code"].encode()):
        con.execute("UPDATE pair_codes SET attempts=attempts+1 WHERE student_id=?", (st["id"],))
        record_fail("code")
        return err("bad_code", 400)
    con.execute("DELETE FROM pair_codes WHERE student_id=?", (st["id"],))
    con.execute("UPDATE students SET device=? WHERE id=?", (device, st["id"]))
    return _student_token(cls, st, device)


@app.post("/api/student/pair-code")
@auth("student")
def student_pair_code():
    code = "".join(secrets.choice("0123456789") for _ in range(6))
    db().execute(
        "INSERT OR REPLACE INTO pair_codes(student_id, code, expires, attempts) VALUES(?,?,?,0)",
        (g.student["id"], code, now() + CODE_TTL_SECONDS),
    )
    return jsonify(code=code, expiresIn=CODE_TTL_SECONDS)


# ---------------------------------------------------------------- class data
@app.get("/api/class/data")
@auth("student", "manager")
def get_data():
    return jsonify(read_all(db(), g.class_id))


@app.put("/api/class/data")
@auth("manager")
def put_data():
    try:
        courses, slots, hw, ann = validate(request.get_json(silent=True))
    except Invalid as e:
        return err("invalid", 400, detail=str(e))
    con = db()
    cid = g.class_id
    try:
        con.execute("BEGIN IMMEDIATE")
        old_ver = {r["id"]: r["photo_ver"] for r in con.execute(
            "SELECT id, photo_ver FROM courses WHERE class_id=?", (cid,))}
        for t in ("courses", "slots", "homework", "announcements"):
            con.execute(f"DELETE FROM {t} WHERE class_id=?", (cid,))
        con.executemany(
            "INSERT INTO courses(class_id,id,name,teacher,color,photo_ver) VALUES(?,?,?,?,?,?)",
            [(cid, c[0], c[1], c[2], c[3], old_ver.get(c[0], 0)) for c in courses])
        con.executemany('INSERT INTO slots(class_id,id,course_id,day,start,"end",room) VALUES(?,?,?,?,?,?,?)',
                        [(cid,) + x for x in slots])
        con.executemany("INSERT INTO homework(class_id,id,course_id,title,details,due,priority,reminder)"
                        " VALUES(?,?,?,?,?,?,?,?)", [(cid,) + x for x in hw])
        con.executemany("INSERT INTO announcements(class_id,id,text,created) VALUES(?,?,?,?)",
                        [(cid,) + x for x in ann])
        keep = {c[0] for c in courses}
        for (course_id,) in con.execute("SELECT course_id FROM photos WHERE class_id=?", (cid,)).fetchall():
            if course_id not in keep:
                con.execute("DELETE FROM photos WHERE class_id=? AND course_id=?", (cid, course_id))
        con.execute("UPDATE classes SET rev=rev+1 WHERE id=?", (cid,))
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    return jsonify(read_all(con, cid))


@app.get("/api/class/courses/<course_id>/photo")
@auth("student", "manager")
def get_photo(course_id):
    row = db().execute("SELECT data FROM photos WHERE class_id=? AND course_id=?",
                       (g.class_id, course_id)).fetchone()
    if row is None:
        return err("not_found", 404)
    return Response(bytes(row["data"]), mimetype="image/jpeg")


@app.put("/api/class/courses/<course_id>/photo")
@auth("manager")
def put_photo(course_id):
    data = request.get_data()
    if not data or len(data) > MAX_PHOTO_BYTES:
        return err("too_large", 413)
    if data[:3] != b"\xff\xd8\xff":
        return err("invalid", 400, detail="JPEG required")
    con = db()
    if con.execute("SELECT 1 FROM courses WHERE class_id=? AND id=?", (g.class_id, course_id)).fetchone() is None:
        return err("not_found", 404)
    ver = now()
    try:
        con.execute("BEGIN IMMEDIATE")
        con.execute("INSERT OR REPLACE INTO photos(class_id, course_id, data) VALUES(?,?,?)",
                    (g.class_id, course_id, sqlite3.Binary(data)))
        con.execute("UPDATE courses SET photo_ver=? WHERE class_id=? AND id=?", (ver, g.class_id, course_id))
        con.execute("UPDATE classes SET rev=rev+1 WHERE id=?", (g.class_id,))
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    return jsonify(photoVer=ver)


# ---------------------------------------------------------------- students (manager)
def student_json(r):
    return {"id": r["id"], "firstName": r["first_name"], "lastName": r["last_name"],
            "deviceLocked": bool(r["device"]), "blockedUntil": r["blocked_until"]}


@app.get("/api/class/students")
@auth("manager")
def list_students():
    rows = db().execute("SELECT * FROM students WHERE class_id=? ORDER BY last_name, first_name",
                        (g.class_id,)).fetchall()
    return jsonify(students=[student_json(r) for r in rows])


@app.post("/api/class/students")
@auth("manager")
def add_students():
    items = body_dict().get("students")
    if not isinstance(items, list) or len(items) > MAX_STUDENTS:
        return err("invalid", 400)
    con = db()
    count = con.execute("SELECT COUNT(*) FROM students WHERE class_id=?", (g.class_id,)).fetchone()[0]
    added, duplicates, invalid = 0, [], 0
    for it in items:
        if not isinstance(it, dict):
            invalid += 1
            continue
        first, last = clean_name(it.get("firstName")), clean_name(it.get("lastName"))
        if not first or not last or len(first) > 60 or len(last) > 60:
            invalid += 1
            continue
        if count + added >= MAX_STUDENTS:
            invalid += 1
            continue
        try:
            con.execute(
                "INSERT INTO students(id, class_id, first_name, last_name, name_key) VALUES(?,?,?,?,?)",
                (uuid.uuid4().hex, g.class_id, first, last, name_key(first, last)))
            added += 1
        except sqlite3.IntegrityError:
            duplicates.append(f"{first} {last}")
    return jsonify(added=added, duplicates=duplicates, invalid=invalid)


def _own_student(sid):
    return db().execute("SELECT * FROM students WHERE id=? AND class_id=?", (sid, g.class_id)).fetchone()


@app.delete("/api/class/students/<sid>")
@auth("manager")
def delete_student(sid):
    if _own_student(sid) is None:
        return err("not_found", 404)
    con = db()
    con.execute("DELETE FROM pair_codes WHERE student_id=?", (sid,))
    con.execute("DELETE FROM students WHERE id=?", (sid,))
    return jsonify(ok=True)


@app.post("/api/class/students/<sid>/release")
@auth("manager")
def release_student(sid):
    if _own_student(sid) is None:
        return err("not_found", 404)
    con = db()
    con.execute("UPDATE students SET device=NULL WHERE id=?", (sid,))
    con.execute("DELETE FROM pair_codes WHERE student_id=?", (sid,))
    return jsonify(ok=True)


@app.post("/api/class/students/<sid>/block")
@auth("manager")
def block_student(sid):
    if _own_student(sid) is None:
        return err("not_found", 404)
    minutes = body_dict().get("minutes")
    if isinstance(minutes, bool) or not isinstance(minutes, int) or not (0 <= minutes <= MAX_BLOCK_MINUTES):
        return err("invalid", 400)
    until = now() + minutes * 60 if minutes > 0 else 0
    db().execute("UPDATE students SET blocked_until=? WHERE id=?", (until, sid))
    return jsonify(blockedUntil=until)


# ---------------------------------------------------------------- classes (super)
@app.get("/api/admin/classes")
@auth("super")
def admin_list():
    rows = db().execute(
        "SELECT c.id, c.name, (SELECT COUNT(*) FROM students s WHERE s.class_id=c.id) AS n "
        "FROM classes c ORDER BY c.name").fetchall()
    return jsonify(classes=[{"id": r["id"], "name": r["name"], "students": r["n"]} for r in rows])


def _valid_class_fields(b, require_all):
    name = clean_name(b.get("name"))
    pw = b.get("password")
    if name and len(name) > 60:
        return None, None, "name too long"
    if require_all and not name:
        return None, None, "name required"
    if pw is not None:
        if not isinstance(pw, str) or len(pw.strip()) < 4 or len(pw) > 100:
            return None, None, "password must be 4-100 characters"
        pw = pw.strip()
    elif require_all:
        return None, None, "password required"
    return name, pw, None


@app.post("/api/admin/classes")
@auth("super")
def admin_create():
    name, pw, bad = _valid_class_fields(body_dict(), True)
    if bad:
        return err("invalid", 400, detail=bad)
    cid = uuid.uuid4().hex[:12]
    try:
        db().execute("INSERT INTO classes(id, name, pw_hash, created) VALUES(?,?,?,?)",
                     (cid, name, generate_password_hash(pw, method=PW_METHOD), now()))
    except sqlite3.IntegrityError:
        return err("name_taken", 409)
    return jsonify(id=cid, name=name)


@app.put("/api/admin/classes/<cid>")
@auth("super")
def admin_update(cid):
    con = db()
    if con.execute("SELECT 1 FROM classes WHERE id=?", (cid,)).fetchone() is None:
        return err("not_found", 404)
    name, pw, bad = _valid_class_fields(body_dict(), False)
    if bad:
        return err("invalid", 400, detail=bad)
    try:
        if name:
            con.execute("UPDATE classes SET name=? WHERE id=?", (name, cid))
        if pw:
            con.execute("UPDATE classes SET pw_hash=? WHERE id=?",
                        (generate_password_hash(pw, method=PW_METHOD), cid))
    except sqlite3.IntegrityError:
        return err("name_taken", 409)
    return jsonify(ok=True)


@app.delete("/api/admin/classes/<cid>")
@auth("super")
def admin_delete(cid):
    con = db()
    if con.execute("SELECT 1 FROM classes WHERE id=?", (cid,)).fetchone() is None:
        return err("not_found", 404)
    try:
        con.execute("BEGIN IMMEDIATE")
        con.execute("DELETE FROM pair_codes WHERE student_id IN (SELECT id FROM students WHERE class_id=?)", (cid,))
        for t in ("students", "courses", "slots", "homework", "announcements", "photos"):
            con.execute(f"DELETE FROM {t} WHERE class_id=?", (cid,))
        con.execute("DELETE FROM classes WHERE id=?", (cid,))
        con.execute("COMMIT")
    except Exception:
        con.execute("ROLLBACK")
        raise
    return jsonify(ok=True)


# ---------------------------------------------------------------- app updates
def _flavor_dir(flavor):
    return os.path.join(APPS_DIR, flavor)


def _latest(flavor):
    """اطلاعات آخرین نسخهٔ منتشرشده یا None (فایل APK باید هم‌اندازهٔ ثبت‌شده باشد)."""
    d = _flavor_dir(flavor)
    try:
        with open(os.path.join(d, "latest.json"), encoding="utf-8") as f:
            meta = json.load(f)
        apk = os.path.join(d, f"classify-{flavor}.apk")
        if os.path.getsize(apk) != meta["size"]:
            return None
        return meta
    except (OSError, ValueError, KeyError):
        return None


@app.get("/api/app/<flavor>/latest")
def app_latest(flavor):
    if flavor not in APP_FLAVORS:
        return err("not_found", 404)
    meta = _latest(flavor)
    if meta is None:
        return err("no_release", 404)
    resp = jsonify(
        versionCode=meta["versionCode"], versionName=meta["versionName"], sha256=meta["sha256"],
        size=meta["size"], changelog=meta.get("changelog", []), publishedAt=meta.get("publishedAt", 0),
        url=f"/api/app/{flavor}/download",
    )
    resp.headers["Cache-Control"] = "no-store"
    return resp


@app.get("/api/app/<flavor>/download")
def app_download(flavor):
    if flavor not in APP_FLAVORS or _latest(flavor) is None:
        return err("not_found", 404)
    # در سرور واقعی Caddy همین مسیر را مستقیم از دیسک می‌دهد؛ این نسخه برای تست (Termux) است
    return send_file(
        os.path.join(_flavor_dir(flavor), f"classify-{flavor}.apk"),
        mimetype="application/vnd.android.package-archive",
        as_attachment=True, download_name=f"Classify-{flavor}.apk", conditional=True, max_age=0,
    )


def _clean_changelog(items):
    if not isinstance(items, list) or len(items) > 30:
        raise Invalid("changelog must be a list (max 30)")
    out = []
    for it in items:
        if not isinstance(it, str):
            raise Invalid("changelog items must be strings")
        it = " ".join(it.split())
        if it:
            if len(it) > 300:
                raise Invalid("changelog item too long")
            out.append(it)
    return out


@app.post("/api/app/<flavor>/publish")
def app_publish(flavor):
    """انتشار نسخهٔ جدید (از GitHub Actions یا دستی). فیلدها: apk (فایل) و meta (JSON)."""
    if flavor not in APP_FLAVORS:
        return err("not_found", 404)
    if too_many("pw"):
        return err("too_many_attempts", 429)
    h = request.headers.get("Authorization", "")
    token = h[7:] if h.startswith("Bearer ") else ""
    if not secrets.compare_digest(token.encode(), CONFIG["publish_token"].encode()):
        record_fail("pw")
        return err("unauthorized", 401)
    f = request.files.get("apk")
    if f is None:
        return err("invalid", 400, detail="apk file missing")
    try:
        meta = json.loads(request.form.get("meta", ""))
        if not isinstance(meta, dict):
            raise ValueError
        code = meta.get("versionCode")
        name = meta.get("versionName")
        if isinstance(code, bool) or not isinstance(code, int) or not (0 < code < 2_000_000_000):
            raise Invalid("versionCode must be a positive int")
        if not isinstance(name, str) or not VERSION_NAME_RE.fullmatch(name):
            raise Invalid("versionName must look like 1.4 or 1.4.2")
        changelog = _clean_changelog(meta.get("changelog", []))
    except Invalid as e:
        return err("invalid", 400, detail=str(e))
    except ValueError:
        return err("invalid", 400, detail="meta must be a JSON object")

    current = _latest(flavor)
    if current is not None and code < current["versionCode"]:
        return err("older_version", 409, current=current["versionCode"])

    d = _flavor_dir(flavor)
    os.makedirs(d, exist_ok=True)
    tmp_apk = os.path.join(d, f".upload-{secrets.token_hex(6)}.apk")
    try:
        sha, size, head = hashlib.sha256(), 0, b""
        with open(tmp_apk, "wb") as out:
            while True:
                chunk = f.stream.read(1024 * 1024)
                if not chunk:
                    break
                if not head:
                    head = chunk[:4]
                size += len(chunk)
                if size > MAX_APK_BYTES:
                    raise Invalid("apk too large")
                sha.update(chunk)
                out.write(chunk)
        if head != b"PK\x03\x04":
            raise Invalid("file is not an APK")
        if size < 1024:
            raise Invalid("apk too small")
        info = {"versionCode": code, "versionName": name, "sha256": sha.hexdigest(), "size": size,
                "changelog": changelog, "publishedAt": now()}
        # اول APK، بعد latest.json؛ تا هیچ‌وقت JSONِ جدید به APKِ قدیمی اشاره نکند
        os.replace(tmp_apk, os.path.join(d, f"classify-{flavor}.apk"))
        tmp_json = os.path.join(d, f".latest-{secrets.token_hex(6)}.json")
        with open(tmp_json, "w", encoding="utf-8") as jf:
            json.dump(info, jf, ensure_ascii=False)
        os.replace(tmp_json, os.path.join(d, "latest.json"))
        for p in os.listdir(d):  # آپلودهای نیمه‌کاره
            if p.startswith(".upload-") or p.startswith(".latest-"):
                try:
                    os.remove(os.path.join(d, p))
                except OSError:
                    pass
    except Invalid as e:
        try:
            os.remove(tmp_apk)
        except OSError:
            pass
        return err("invalid", 400, detail=str(e))
    return jsonify(ok=True, versionCode=code, versionName=name, size=size, sha256=info["sha256"])


# ---------------------------------------------------------------- errors
@app.errorhandler(413)
def too_large(_e):
    return err("too_large", 413)


@app.errorhandler(404)
def not_found(_e):
    return err("not_found", 404)


@app.errorhandler(405)
def bad_method(_e):
    return err("bad_request", 405)


init_db()

if __name__ == "__main__":
    print("=" * 50)
    if CONFIG["_created"]:
        print("اولین اجرا — رمز مدیر کل در config.json ذخیره شد.")
    print("رمز مدیر کل:", CONFIG["super_password"])
    print("توکن انتشار آپدیت (برای GitHub Secret به اسم PUBLISH_TOKEN):", CONFIG["publish_token"])
    print("با این رمز در اپ وارد شوید و کلاس‌ها و رمز مدیر هر کلاس را بسازید.")
    print("=" * 50)
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5000)))
