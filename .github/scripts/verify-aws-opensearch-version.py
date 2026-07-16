#!/usr/bin/env python3
import os
import sys
from urllib.parse import urlparse

import boto3
from opensearchpy import AWSV4SignerAuth, OpenSearch, RequestsHttpConnection

# Validate and normalize environment variables first.
opensearch_url = os.environ.get("OPENSEARCH_URL", "").strip()
if not opensearch_url:
    print("ERROR: OPENSEARCH_URL is required")
    sys.exit(1)
OPENSEARCH_URL = opensearch_url.rstrip("/")

expected_version = os.environ.get("EXPECTED_OPENSEARCH_VERSION", "").strip()
if not expected_version:
    print("ERROR: EXPECTED_OPENSEARCH_VERSION is required")
    sys.exit(1)

REGION = os.environ.get("AWS_REGION", os.environ.get("AWS_DEFAULT_REGION", "us-east-1"))

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
    info = client.info()
except Exception as exc:
    print(f"ERROR: Failed to query cluster info: {exc}")
    sys.exit(1)

actual_version = info.get("version", {}).get("number", "")
if not actual_version:
    print(f"ERROR: Could not determine cluster version from response: {info}")
    sys.exit(1)

if actual_version == expected_version or actual_version.startswith(f"{expected_version}."):
    print(f"OK: Cluster version '{actual_version}' matches expected '{expected_version}'")
    sys.exit(0)

print(f"ERROR: Cluster version '{actual_version}' does not match expected '{expected_version}'")
sys.exit(1)
