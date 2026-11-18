#!/usr/bin/env python3
"""Apply the minimal standalone identity changes to the checked-out AOSP LatinIME source."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / "LatinIME"
gradle = root / "build.gradle"
app_name = root / "java/res/values/strings-appname.xml"

if not gradle.is_file() or not app_name.is_file():
    raise SystemExit("AOSP LatinIME submodule is missing or incomplete")

g = gradle.read_text(encoding="utf-8")
old_id = "applicationId 'com.android.inputmethod.latin'"
new_id = "applicationId 'com.praveen.newgboard'"
if old_id not in g:
    if new_id not in g:
        raise SystemExit("Could not locate the expected LatinIME applicationId in build.gradle")
else:
    g = g.replace(old_id, new_id, 1)

# Use the normal local Android debug keystore rather than the upstream shared signing key.
g = re.sub(
    r"\n\s*signingConfigs\s*\{\s*debug\s*\{\s*storeFile\s+file\("java/shared\.keystore"\)\s*\}\s*\}\s*",
    "\n",
    g,
    count=1,
)
gradle.write_text(g, encoding="utf-8")

s = app_name.read_text(encoding="utf-8")
s, count = re.subn(
    r'(<string\s+name="english_ime_name"\s*>)[\s\S]*?(</string>)',
    r'\1NEW GBOARD\2',
    s,
    count=1,
)
if count != 1:
    raise SystemExit("Could not locate english_ime_name in strings-appname.xml")
app_name.write_text(s, encoding="utf-8")

print("Prepared standalone NEW GBOARD identity: com.praveen.newgboard")
