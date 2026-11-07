"""Builds the iOS app's string catalog from the Android strings, so each text is written once.

    python3 tools/build_ios_strings.py

Reads shared/src/main/res/values*/strings.xml and writes ios/Adonde/Localizable.xcstrings. The keys are the
Android names; a string array becomes name_0, name_1, ... Android's %d and %s become %lld and %@.
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "shared/src/main/res"
OUT = ROOT / "ios/Adonde/Localizable.xcstrings"
# The folder of each language; English is the fallback.
LANGUAGES = {"en": "values", "pt": "values-pt", "es": "values-es", "fr": "values-fr"}


def unescape(text):
    """Android's rules: outer quotes keep spaces, and backslashes escape quotes and newlines."""
    text = text.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    text = re.sub(r"\\n", "\n", text)
    return re.sub(r"\\(.)", r"\1", text)


def to_ios(text):
    """Android format specifiers to Apple's: %d and %1$d to %lld and %1$lld, %s to %@."""
    return re.sub(r"%(\d+\$)?([ds])", lambda m: "%" + (m.group(1) or "") + ("lld" if m.group(2) == "d" else "@"), text)


def read(folder):
    strings = {}
    for element in ET.parse(RES / folder / "strings.xml").getroot():
        name = element.get("name")
        if element.get("translatable") == "false":
            continue
        if element.tag == "string":
            strings[name] = to_ios(unescape(element.text or ""))
        elif element.tag == "string-array":
            for index, item in enumerate(element.findall("item")):
                strings[f"{name}_{index}"] = to_ios(unescape(item.text or ""))
    return strings


def main():
    languages = {code: read(folder) for code, folder in LANGUAGES.items()}
    english = languages["en"]
    catalog = {"sourceLanguage": "en", "strings": {}, "version": "1.0"}
    for key in sorted(english):
        localizations = {}
        for code, strings in languages.items():
            if key in strings:
                localizations[code] = {"stringUnit": {"state": "translated", "value": strings[key]}}
            else:
                print(f"warning: {key} has no {code} translation", file=sys.stderr)
        catalog["strings"][key] = {"extractionState": "manual", "localizations": localizations}
    OUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    print(f"{len(english)} strings in {len(languages)} languages -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
