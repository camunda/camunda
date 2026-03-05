#!/usr/bin/env bash
set -euo pipefail

: "${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
DRY_RUN="${DRY_RUN:-true}"
AGE_HOURS="${AGE_HOURS:-6}"

# requests-aws4auth handles SigV4 signing; boto3 resolves AWS credentials automatically
pip install -q "requests-aws4auth==1.3.1"

python3 - <<'PYTHON_SCRIPT'
import os, json, time, sys

import boto3
import requests
from requests_aws4auth import AWS4Auth

OPENSEARCH_URL = os.environ["OPENSEARCH_URL"].rstrip("/")
REGION = os.environ.get("AWS_REGION", os.environ.get("AWS_DEFAULT_REGION", "us-east-1"))
DRY_RUN = os.environ.get("DRY_RUN", "true").lower() == "true"
AGE_HOURS = int(os.environ.get("AGE_HOURS", "6"))
THRESHOLD = AGE_HOURS * 3600

raw_creds = boto3.Session().get_credentials()
if raw_creds is None:
    print("ERROR: No AWS credentials found. Ensure the runner has a configured IAM role or credentials.")
    sys.exit(1)
creds = raw_creds.get_frozen_credentials()
awsauth = AWS4Auth(creds.access_key, creds.secret_key, REGION, "es", session_token=creds.token)

resp = requests.get(
    f"{OPENSEARCH_URL}/_cat/indices",
    params={"format": "json", "h": "index,creation.date"},
    auth=awsauth,
)
if resp.status_code != 200:
    print(f"ERROR: Failed to list indices (HTTP {resp.status_code}): {resp.text[:500]}")
    sys.exit(1)

indices = [idx for idx in resp.json() if not idx["index"].startswith(".")]
if not indices:
    print("INFO: No user indices found, nothing to delete.")
    sys.exit(0)

now = int(time.time())
deleted = skipped_young = skipped_already_gone = skipped_error = 0

for idx in indices:
    idx_name = idx["index"]
    creation_date = idx.get("creation.date")
    if creation_date is None:
        skipped_error += 1
        print(f"  WARN: Missing creation date for {idx_name}, skipping.")
        continue
    age_seconds = now - (int(creation_date) // 1000)

    if age_seconds <= THRESHOLD:
        skipped_young += 1
        continue

    if DRY_RUN:
        deleted += 1
        print(f"  [DRY RUN] Would delete: {idx_name} (age: {age_seconds // 3600}h {(age_seconds % 3600) // 60}m)")
    else:
        del_resp = requests.delete(f"{OPENSEARCH_URL}/{idx_name}", auth=awsauth)
        if del_resp.status_code == 200:
            deleted += 1
        elif del_resp.status_code == 404:
            skipped_already_gone += 1
        else:
            skipped_error += 1
            print(f"  WARN: Failed to delete {idx_name} (HTTP {del_resp.status_code})")

prefix = "[DRY RUN] " if DRY_RUN else ""
print(f"\n{prefix}Summary: {len(indices)} indices found | {deleted} deleted | {skipped_young} too young | {skipped_already_gone} already gone | {skipped_error} errors")
PYTHON_SCRIPT
