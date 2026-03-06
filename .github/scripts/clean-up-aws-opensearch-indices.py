#!/usr/bin/env python3
import os
import sys
import time
from urllib.parse import urlparse

import boto3
from opensearchpy import OpenSearch, NotFoundError, RequestsHttpConnection, AWSV4SignerAuth

# Validate and normalize environment variables first.
opensearch_url = os.environ.get("OPENSEARCH_URL", "").strip()
if not opensearch_url:
    print("ERROR: OPENSEARCH_URL is required")
    sys.exit(1)
OPENSEARCH_URL = opensearch_url.rstrip("/")

dry_run = os.environ.get("DRY_RUN", "true").strip().lower()
if dry_run not in {"true", "false"}:
    print("ERROR: DRY_RUN must be 'true' or 'false'")
    sys.exit(1)
DRY_RUN = dry_run == "true"

age_hours = os.environ.get("AGE_HOURS", "6").strip()
try:
    AGE_HOURS = int(age_hours)
except ValueError:
    print("ERROR: AGE_HOURS must be an integer")
    sys.exit(1)
if AGE_HOURS < 0:
    print("ERROR: AGE_HOURS must be >= 0")
    sys.exit(1)

REGION = os.environ.get("AWS_REGION", os.environ.get("AWS_DEFAULT_REGION", "us-east-1"))
THRESHOLD = AGE_HOURS * 3600

raw_creds = boto3.Session().get_credentials()
if raw_creds is None:
    print("ERROR: No AWS credentials found. Ensure the runner has a configured IAM role or credentials.")
    sys.exit(1)

parsed = urlparse(OPENSEARCH_URL)
if not parsed.hostname:
    print("ERROR: OPENSEARCH_URL must be a valid URL with hostname")
    sys.exit(1)

awsauth = AWSV4SignerAuth(raw_creds, REGION, "es")
client = OpenSearch(
    hosts=[{"host": parsed.hostname, "port": parsed.port or (443 if parsed.scheme == "https" else 80)}],
    http_auth=awsauth,
    use_ssl=parsed.scheme == "https",
    verify_certs=True,
    connection_class=RequestsHttpConnection,
)

try:
    all_indices = client.cat.indices(h="index,creation.date", format="json")
except Exception as exc:
    print(f"Failed to list indices: {exc}")
    sys.exit(1)

indices = [idx for idx in all_indices if not idx.get("index", "").startswith(".")]
if not indices:
    print("INFO: No user indices found, nothing to delete.")
    sys.exit(0)

now = int(time.time())
deleted = skipped_young = skipped_already_gone = skipped_error = 0

for idx in indices:
    idx_name = idx.get("index", "<unknown>")
    creation_date = idx.get("creation.date")
    if creation_date is None:
        skipped_error += 1
        print(f"  WARN: Missing creation date for {idx_name}, skipping.")
        continue

    try:
        age_seconds = now - (int(creation_date) // 1000)
    except (TypeError, ValueError):
        skipped_error += 1
        print(f"  WARN: Invalid creation date for {idx_name}, skipping.")
        continue

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
        except Exception as exc:
            skipped_error += 1
            print(f"  WARN: Failed to delete {idx_name}: {exc}")

prefix = "[DRY RUN] " if DRY_RUN else ""
print(
    f"\n{prefix}Summary:"
    f"\n{prefix}indices_found={len(indices)}"
    f"\n{prefix}deleted={deleted}"
    f"\n{prefix}too_young={skipped_young}"
    f"\n{prefix}already_gone={skipped_already_gone}"
    f"\n{prefix}errors={skipped_error}"
)
sys.exit(0)
