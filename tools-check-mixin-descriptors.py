import re, subprocess, sys, pathlib

import os
MC = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar")
CLIENT = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
CP = MC + ":" + CLIENT

def real_descriptors(cls):
    out = subprocess.run(["javap", "-p", "-s", "-cp", CP, cls],
                         capture_output=True, text=True).stdout
    result = {}
    pending = None
    for line in out.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("descriptor:"):
            d = line.split(":", 1)[1].strip()
            if pending:
                result.setdefault(pending, set()).add(d)
            pending = None
            continue
        m = re.search(r"([A-Za-z_$][\w$]*)\s*\(", line)
        if m and "Code:" not in line and not line.startswith(("public class", "class", "final")):
            pending = m.group(1)
    return result

MIXIN_DIR = pathlib.Path(__file__).resolve().parent / "src/main/java/argon/mixin"

# collect every (file, method, descriptor) triple the mixins claim
pattern = re.compile(r'method\s*=\s*"([^"(]+)([^"]*)"')
claims = []
for f in sorted(MIXIN_DIR.glob("*.java")):
    txt = f.read_text()
    for m in pattern.finditer(txt):
        name, desc = m.group(1), m.group(2) or None
        if desc and not desc.startswith("("):
            desc = None
        target = re.search(r'@Mixin\(targets\s*=\s*"([^"]+)"', txt)
        claims.append((f.name, target.group(1) if target else None, name, desc))

fails = 0
for fname, cls, name, desc in claims:
    if not desc:
        print(f"NO-DESC  {fname}: {name}  (name-only selector, left as-is)")
        continue
    have = real_descriptors(cls).get(name, set())
    ok = desc in have
    print(f"{'OK  ' if ok else 'FAIL'}  {fname}: {name}")
    if not ok:
        fails += 1
        print(f"      claimed: {desc}")
        for d in have:
            print(f"      actual : {d}")

print()
print("FAILURES:", fails)
sys.exit(1 if fails else 0)
