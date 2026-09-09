#!/usr/bin/env python3
"""Delete leftover test artifacts from the shared AWS OpenSearch CI cluster.

Integration tests create indices, index templates, component templates and ISM
policies under a per-test prefix, and nothing removes them when a run ends.

Indices are checked for their creation age and deleted appropriately. Templates
and policies carry no creation timestamp so there is nothing to age them by, but
this is a dedicated CI cluster, so everything that is not plugin-owned is dropped
instead. Dropping a template does not affect existing indices, since templates
only apply at index creation, and every run recreates what it needs during schema
startup. This runs as a PRE step behind the workflow's concurrency group, so no
pipeline test is in flight while it wipes them.
"""
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

prefix = "[DRY RUN] " if DRY_RUN else ""

try:
    all_indices = client.cat.indices(h="index,creation.date", format="json")
except Exception as exc:
    print(f"Failed to list indices: {exc}")
    sys.exit(1)

indices = [idx for idx in all_indices if not idx.get("index", "").startswith(".")]
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


# Most index/component template a Camunda app creates carries its app name
# somewhere in the name (e.g. "<prefix>-operate-list-view-8.3.0_"), so one
# wildcard delete clears the bulk of them in a single cluster state update.
APP_NAME_WORDS = ("camunda", "operate", "tasklist", "optimize", "zeebe")

# Dotted names are system-owned; the rest are prefixes OpenSearch plugins (ISM,
# observability, security analytics) register on install. Nothing here is created
# by a test run, and dropping one may break the cluster for every later run.
PROTECTED_NAME_PREFIXES = (".", "ss4o_", "ism_", "opensearch", "security-auditlog", "sample-")


def wipe(label, path, key):
    """Delete the test-created artifacts under path, returning (deleted, errors).

    Wildcard deletes clear everything carrying a camunda specific name in bulk
    (one request per app word — OpenSearch binds the template name as a single
    pattern, so comma-separated patterns are not supported), then stragglers are
    deleted by name. Not using bare "*", although the cluster is dedicated to CI,
    it's technically not exclusively ours. In addition, installed OpenSearch
    plugins may register templates of their own that a bare wildcard would delete.
    Note: Bulk deletions are not counted individually (the wildcard response
    carries no count).
    """
    deleted = errors = 0

    # Bulk pass first, before any listing: with tens of thousands of leftover
    # app-named templates, listing up front would page through them all just to
    # classify names the wildcards already cover.
    for word in APP_NAME_WORDS:
        if DRY_RUN:
            print(f"  [DRY RUN] Would bulk-delete {path}/*{word}*")
            continue
        try:
            client.transport.perform_request("DELETE", f"{path}/*{word}*")
        except NotFoundError:
            pass  # nothing matched this word
        except Exception as exc:
            errors += 1
            print(f"  WARN: Failed to bulk-delete {label} matching *{word}*: {exc}")

    # Straggler pass: whatever survived the wildcards.
    try:
        listing = client.transport.perform_request("GET", path, params={"filter_path": f"{key}.name"})
        names = [entry["name"] for entry in listing.get(key, [])]
    except Exception as exc:
        errors += 1
        print(f"  WARN: Failed to list {label}: {exc}")
        return deleted, errors

    app_named = [name for name in names if any(word in name for word in APP_NAME_WORDS)]
    if DRY_RUN:
        # The bulk pass did not run, so every app-named entry is still listed;
        # count them as the deletions that pass would have made.
        deleted += len(app_named)
    elif app_named:
        # A bulk delete must have failed; deleting tens of thousands serially
        # would run for hours, so surface the leftovers instead of retrying.
        errors += 1
        print(f"  WARN: {len(app_named)} app-named {label} survived the bulk pass")

    stragglers = [
        name
        for name in names
        if not any(word in name for word in APP_NAME_WORDS)
        and not name.startswith(PROTECTED_NAME_PREFIXES)
    ]

    for name in stragglers:
        if DRY_RUN:
            print(f"  [DRY RUN] Would delete {label} {name}")
            deleted += 1
            continue
        try:
            client.transport.perform_request("DELETE", f"{path}/{name}")
            deleted += 1
        except NotFoundError:
            deleted += 1
        except Exception as exc:
            errors += 1
            print(f"  WARN: Failed to delete {label} {name}: {exc}")
    return deleted, errors


# Index templates must go before component templates: OpenSearch refuses to drop
# a component template while an index template still composes it.
index_templates, index_template_errors = wipe("index templates", "/_index_template", "index_templates")
component_templates, component_template_errors = wipe(
    "component templates", "/_component_template", "component_templates"
)

ism_policies = ism_errors = 0
try:
    response = client.transport.perform_request("GET", "/_plugins/_ism/policies", params={"size": "10000"})
    # Test policies don't consistently carry an app name (e.g. "<uniqueId>-default-policy"
    # from OpenSearchArchiverRepositoryIT), so guard with the plugin-owned denylist
    # instead of an app-name allowlist.
    policies = [
        policy.get("_id", "")
        for policy in response.get("policies", [])
        if not policy.get("_id", "").startswith(PROTECTED_NAME_PREFIXES)
    ]
    for policy_id in policies:
        if DRY_RUN:
            ism_policies += 1
            print(f"  [DRY RUN] Would delete ISM policy: {policy_id}")
            continue
        try:
            client.transport.perform_request("DELETE", f"/_plugins/_ism/policies/{policy_id}")
            ism_policies += 1
        except NotFoundError:
            ism_policies += 1
        except Exception as exc:
            ism_errors += 1
            print(f"  WARN: Failed to delete ISM policy {policy_id}: {exc}")
except Exception as exc:
    print(f"  WARN: Failed to list ISM policies: {exc}")

template_errors = index_template_errors + component_template_errors + ism_errors

print(
    f"\n{prefix}Summary:"
    f"\n{prefix}indices_found={len(indices)}"
    f"\n{prefix}deleted={deleted}"
    f"\n{prefix}too_young={skipped_young}"
    f"\n{prefix}already_gone={skipped_already_gone}"
    f"\n{prefix}errors={skipped_error}"
    f"\n{prefix}index_templates_deleted_by_name={index_templates}"
    f"\n{prefix}component_templates_deleted_by_name={component_templates}"
    f"\n{prefix}ism_policies_deleted={ism_policies}"
    f"\n{prefix}template_errors={template_errors}"
)

# The job stays green even when cleanup partially fails - a red PRE step would
# block the test run over leftovers the next night retries anyway. A workflow
# annotation keeps the failure visible so recurring ones don't refill the
# cluster unnoticed (that is how it previously grew to 65k stale templates).
if skipped_error + template_errors > 0:
    print(
        f"::warning title=AWS OpenSearch cleanup incomplete::"
        f"{skipped_error} index and {template_errors} template/policy deletions failed; "
        f"see the step log for details"
    )
sys.exit(0)
