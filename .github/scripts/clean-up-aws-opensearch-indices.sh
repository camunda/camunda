#!/usr/bin/env bash
set -euo pipefail

: "${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
DRY_RUN="${DRY_RUN:-true}"
AGE_HOURS="${AGE_HOURS:-6}"

# Install runtime deps into the same interpreter used below.
# boto3 is required for AWS credential resolution used by AWSV4SignerAuth.
# opensearch-py is the official OpenSearch Python client with built-in AWS SigV4 auth support
python3 -m pip install -q --disable-pip-version-check \
  "boto3" \
  "opensearch-py==3.1.0"

python3 - <<'PYTHON_SCRIPT'
import os, sys, time
from urllib.parse import urlparse

import boto3
from opensearchpy import OpenSearch, NotFoundError, RequestsHttpConnection, AWSV4SignerAuth

OPENSEARCH_URL = os.environ["OPENSEARCH_URL"].rstrip("/")
REGION = os.environ.get("AWS_REGION", os.environ.get("AWS_DEFAULT_REGION", "us-east-1"))
DRY_RUN = os.environ.get("DRY_RUN", "true").lower() == "true"
AGE_HOURS = int(os.environ.get("AGE_HOURS", "6"))
THRESHOLD = AGE_HOURS * 3600

raw_creds = boto3.Session().get_credentials()
if raw_creds is None:
    print("ERROR: No AWS credentials found. Ensure the runner has a configured IAM role or credentials.")
    sys.exit(1)

awsauth = AWSV4SignerAuth(raw_creds, REGION, "es")

parsed = urlparse(OPENSEARCH_URL)
client = OpenSearch(
    hosts=[{"host": parsed.hostname, "port": parsed.port or (443 if parsed.scheme == "https" else 80)}],
    http_auth=awsauth,
    use_ssl=parsed.scheme == "https",
    verify_certs=True,
    connection_class=RequestsHttpConnection,
)

try:
    all_indices = client.cat.indices(h="index,creation.date", format="json")
except Exception as e:
    print(f"ERROR: Failed to list indices: {e}")
    sys.exit(1)
indices = [idx for idx in all_indices if not idx["index"].startswith(".")]
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
        try:
            client.indices.delete(index=idx_name)
            deleted += 1
        except NotFoundError:
            skipped_already_gone += 1
        except Exception as e:
            skipped_error += 1
            print(f"  WARN: Failed to delete {idx_name}: {e}")

prefix = "[DRY RUN] " if DRY_RUN else ""
print(f"\n{prefix}Summary: {len(indices)} indices found | {deleted} deleted | {skipped_young} too young | {skipped_already_gone} already gone | {skipped_error} errors")
PYTHON_SCRIPT
