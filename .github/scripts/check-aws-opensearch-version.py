#!/usr/bin/env python3
import os
import sys
from urllib.parse import urlparse

import boto3
from opensearchpy import OpenSearch, RequestsHttpConnection, AWSV4SignerAuth

# Validate and normalize environment variables first.
opensearch_url = os.environ.get("OPENSEARCH_URL", "").strip()
if not opensearch_url:
    print("ERROR: OPENSEARCH_URL is required")
    sys.exit(1)
OPENSEARCH_URL = opensearch_url.rstrip("/")

expected_version = os.environ.get("EXPECTED_OS_VERSION", "").strip()
if not expected_version:
    print("ERROR: EXPECTED_OS_VERSION is required")
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
    print(f"ERROR: Failed to reach OpenSearch cluster at {OPENSEARCH_URL}: {exc}")
    sys.exit(1)

actual_version = info.get("version", {}).get("number", "").strip()
if not actual_version:
    print(f"ERROR: Could not determine cluster version from response: {info}")
    sys.exit(1)

# Compare major.minor only; the patch version is allowed to differ.
actual_major_minor = ".".join(actual_version.split(".")[:2])
if actual_major_minor != expected_version:
    print(
        f"ERROR: Cluster at {OPENSEARCH_URL} reports version '{actual_version}' "
        f"(major.minor '{actual_major_minor}'), which does not match the requested "
        f"job version '{expected_version}'. Refusing to proceed with cleanup/tests "
        "against a mismatched cluster."
    )
    sys.exit(1)

print(f"OK: Cluster at {OPENSEARCH_URL} reports version '{actual_version}', matches expected '{expected_version}'.")
