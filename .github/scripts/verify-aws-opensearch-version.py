#!/usr/bin/env python3
import os
import sys
from urllib.parse import urlparse

import boto3
from opensearchpy import OpenSearch, RequestsHttpConnection, AWSV4SignerAuth

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
    print(f"ERROR: Failed to query cluster info at {OPENSEARCH_URL}: {exc}")
    sys.exit(1)

version_block = info.get("version", {})
actual_version = version_block.get("number", "")
if not actual_version:
    print(f"ERROR: Could not determine cluster version from response: {info}")
    sys.exit(1)

# AWS OpenSearch reports patch-level numbers (e.g. "2.19.1.0"); compare major.minor only.
actual_major_minor = ".".join(actual_version.split(".")[:2])
distribution = version_block.get("distribution", "unknown")

if actual_major_minor != expected_version:
    print(
        f"ERROR: Cluster version mismatch at {OPENSEARCH_URL}. "
        f"Expected '{expected_version}', but cluster is running '{actual_version}' (distribution: {distribution})."
    )
    sys.exit(1)

print(
    f"OK: Cluster at {OPENSEARCH_URL} is running expected version '{actual_version}' (distribution: {distribution})."
)
sys.exit(0)
