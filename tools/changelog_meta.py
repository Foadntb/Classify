#!/usr/bin/env python3
"""ساخت meta (JSON) برای انتشار نسخه: python3 tools/changelog_meta.py <versionName> <versionCode> <student|manager>"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, "..", "app", "src", "main", "assets", "changelog.json")


def main():
    if len(sys.argv) != 4 or sys.argv[3] not in ("student", "manager"):
        sys.exit("usage: changelog_meta.py <versionName> <versionCode> <student|manager>")
    name, code, flavor = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    with open(PATH, encoding="utf-8") as f:
        entries = json.load(f)
    items = []
    for e in entries:
        if e.get("version") == name:
            items = list(e.get("common", [])) + list(e.get(flavor, []))
            break
    else:
        print(f"warning: no changelog entry for version {name}", file=sys.stderr)
    print(json.dumps({"versionCode": code, "versionName": name, "changelog": items}, ensure_ascii=False))


if __name__ == "__main__":
    main()
