#!/usr/bin/env python3
"""Turn a folder of .txt/.md/.csv/.json files into ONE Mnemo-ready JSON file.
Usage:  python prepare_data.py ./my_notes  mnemo_data.json
Then in Mnemo: + > Add files > pick mnemo_data.json
"""
import json, sys, pathlib

src = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
out = sys.argv[2] if len(sys.argv) > 2 else "mnemo_data.json"
items = []
for p in sorted(src.rglob("*")):
    if p.suffix.lower() in {".txt", ".md", ".csv", ".json"} and p.is_file():
        items.append({"title": p.name, "text": p.read_text(encoding="utf-8", errors="ignore")})
pathlib.Path(out).write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"{len(items)} files -> {out}")
