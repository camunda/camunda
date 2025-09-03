#!/usr/bin/env python3
import json
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from collections import defaultdict

ES_URL = "http://localhost:9200"


def load_json(path: Path):
    with open(path) as f:
        return json.load(f)


def es_request(method, path, body=None):
    url = f"{ES_URL}/{path.lstrip('/')}"
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def test_mapping(old_mapping, new_mapping, test_index):
    # 1. Create test index with old mapping
    status, text = es_request("PUT", test_index, body=old_mapping)
    if status >= 300:
        return False, f"Failed to create test index: {text}"

    # 2. Try applying new mapping (only inner mappings allowed here)
    status, text = es_request("PUT", f"{test_index}/_mapping", body=new_mapping["mappings"])
    if status < 300:
        result = True
        details = "Mapping changes are compatible"
    else:
        result = False
        details = text

    # 3. Cleanup
    es_request("DELETE", test_index)
    return result, details


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <directory>")
        sys.exit(1)

    dir_path = Path(sys.argv[1])
    if not dir_path.is_dir():
        print(f"❌ Provided path is not a directory: {dir_path}")
        sys.exit(1)

    # Collect mapping files into groups by suffix
    groups = defaultdict(dict)
    for mapping_file in dir_path.glob("*.json"):
        name = mapping_file.name
        if name.startswith("old-"):
            suffix = name[len("old-") :]
            groups[suffix]["old"] = mapping_file
        elif name.startswith("new-"):
            suffix = name[len("new-") :]
            groups[suffix]["new"] = mapping_file

    if not groups:
        print("❌ No mapping files with 'old-' or 'new-' prefix found.")
        sys.exit(1)

    any_failure = False  # Track failures

    for suffix, files in sorted(groups.items()):
        if "old" not in files or "new" not in files:
            print(f"⚠️  Skipping {suffix} (missing old/new pair)")
            continue

        old_mapping = load_json(files["old"])
        new_mapping = load_json(files["new"])

        test_index = f"mapping-validation-{suffix}-{int(time.time())}"
        ok, details = test_mapping(old_mapping, new_mapping, test_index)

        if ok:
            print(f"✅ {suffix}: SUCCESS — {details}")
        else:
            print(f"❌ {suffix}: FAILED — {details}")
            any_failure = True

    if any_failure:
        print("\n❌ Some mappings failed validation.")
        sys.exit(1)
    else:
        print("\n✅ All mappings validated successfully.")


if __name__ == "__main__":
    main()
