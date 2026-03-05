#!/usr/bin/env bash
set -euo pipefail

: "${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
AWS_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-us-east-1}}"
DRY_RUN="${DRY_RUN:-true}"
AGE_HOURS="${AGE_HOURS:-6}"

python3 - "$OPENSEARCH_URL" "$AWS_REGION" "$DRY_RUN" "$AGE_HOURS" <<'PYTHON_SCRIPT'
import sys, os, json, time, datetime, hashlib, hmac, urllib.request, urllib.parse, urllib.error, ssl

OPENSEARCH_URL = sys.argv[1].rstrip("/")
REGION = sys.argv[2]
DRY_RUN = sys.argv[3].lower() == "true"
AGE_HOURS = int(sys.argv[4])
THRESHOLD = AGE_HOURS * 3600
SERVICE = "es"

ACCESS_KEY = os.environ.get("AWS_ACCESS_KEY_ID", "")
SECRET_KEY = os.environ.get("AWS_SECRET_ACCESS_KEY", "")
SESSION_TOKEN = os.environ.get("AWS_SESSION_TOKEN", "")

if not ACCESS_KEY or not SECRET_KEY:
    print("ERROR: AWS_ACCESS_KEY_ID or AWS_SECRET_ACCESS_KEY not set")
    sys.exit(1)

# --- SigV4 signing (stdlib only) ---
def sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

def get_signature_key(key, date_stamp, region, service):
    k_date = sign(("AWS4" + key).encode("utf-8"), date_stamp)
    k_region = sign(k_date, region)
    k_service = sign(k_region, service)
    return sign(k_service, "aws4_request")

def sigv4_request(method, url, body=""):
    parsed = urllib.parse.urlparse(url)
    host = parsed.hostname
    raw_path = parsed.path or "/"
    segments = raw_path.split("/")
    canonical_path = "/".join(urllib.parse.quote(s, safe=",~-._") for s in segments)

    query_params = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    query_params.sort(key=lambda x: (x[0], x[1]))
    canonical_qs = urllib.parse.urlencode(query_params, quote_via=urllib.parse.quote)

    t = datetime.datetime.now(datetime.timezone.utc)
    amz_date = t.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = t.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()

    headers_map = {"host": host, "x-amz-date": amz_date, "x-amz-content-sha256": payload_hash}
    if SESSION_TOKEN:
        headers_map["x-amz-security-token"] = SESSION_TOKEN

    signed_header_keys = sorted(headers_map.keys())
    signed_headers = ";".join(signed_header_keys)
    canonical_headers = "".join(f"{k}:{headers_map[k]}\n" for k in signed_header_keys)

    canonical_request = "\n".join([method, canonical_path, canonical_qs, canonical_headers, signed_headers, payload_hash])
    credential_scope = f"{date_stamp}/{REGION}/{SERVICE}/aws4_request"
    string_to_sign = "\n".join(["AWS4-HMAC-SHA256", amz_date, credential_scope, hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()])

    signing_key = get_signature_key(SECRET_KEY, date_stamp, REGION, SERVICE)
    signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

    auth_header = f"AWS4-HMAC-SHA256 Credential={ACCESS_KEY}/{credential_scope}, SignedHeaders={signed_headers}, Signature={signature}"

    request_url = f"{parsed.scheme}://{parsed.netloc}{raw_path}"
    if parsed.query:
        request_url += f"?{parsed.query}"

    req = urllib.request.Request(request_url, data=body.encode("utf-8") if body else None, method=method)
    req.add_header("Host", host)
    req.add_header("x-amz-date", amz_date)
    req.add_header("x-amz-content-sha256", payload_hash)
    req.add_header("Authorization", auth_header)
    if SESSION_TOKEN:
        req.add_header("x-amz-security-token", SESSION_TOKEN)

    ctx = ssl.create_default_context()
    try:
        resp = urllib.request.urlopen(req, context=ctx)
        return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

# --- Main logic ---
status, body = sigv4_request("GET", f"{OPENSEARCH_URL}/_cat/indices?format=json&h=index")
if status != 200:
    print(f"ERROR: Failed to list indices (HTTP {status}): {body[:500]}")
    sys.exit(1)

indices = [idx["index"] for idx in json.loads(body) if not idx["index"].startswith(".")]
if not indices:
    print("INFO: No user indices found, nothing to delete.")
    sys.exit(0)

now = int(time.time())
deleted = 0
skipped_young = 0
skipped_already_gone = 0
skipped_error = 0

for idx_name in indices:
    status, body = sigv4_request("GET", f"{OPENSEARCH_URL}/{idx_name}/_settings?filter_path=*.settings.index.creation_date")
    if status == 404:
        skipped_already_gone += 1
        continue
    if status != 200:
        skipped_error += 1
        print(f"  WARN: Failed to fetch settings for {idx_name} (HTTP {status})")
        continue

    settings = json.loads(body)
    if idx_name not in settings:
        skipped_error += 1
        continue

    creation_ms = int(settings[idx_name]["settings"]["index"]["creation_date"])
    age_seconds = now - (creation_ms // 1000)

    if age_seconds <= THRESHOLD:
        skipped_young += 1
        continue

    if DRY_RUN:
        deleted += 1
        print(f"  [DRY RUN] Would delete: {idx_name} (age: {age_seconds // 3600}h {(age_seconds % 3600) // 60}m)")
    else:
        del_status, del_body = sigv4_request("DELETE", f"{OPENSEARCH_URL}/{idx_name}")
        if del_status == 200:
            deleted += 1
        elif del_status == 404:
            skipped_already_gone += 1
        else:
            skipped_error += 1
            print(f"  WARN: Failed to delete {idx_name} (HTTP {del_status})")

prefix = "[DRY RUN] " if DRY_RUN else ""
print(f"\n{prefix}Summary: {len(indices)} indices found | {deleted} deleted | {skipped_young} too young | {skipped_already_gone} already gone | {skipped_error} errors")
PYTHON_SCRIPT

exit_code=$?
exit ${exit_code}
