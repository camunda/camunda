import json
import time
from collections import defaultdict
from pathlib import Path

from elasticsearch import Elasticsearch

# ── Configuration ─────────────────────────────────────────────────────────────
ES_HOST          = "http://localhost:9200"
SOURCE_INDEX     = "zeebe-record-variable"
DEST_INDEX       = "optimize-reporting-metrics"
RUN_EVERY_S      = 60
TOTAL_DURATION_S = 60 * 60
PAGE_SIZE        = 1000
STATE_FILE       = Path(__file__).parent / ".reporting_metrics_position.json"
# ──────────────────────────────────────────────────────────────────────────────


def parse_bool(value: str) -> bool:
    normalized = value.strip().strip('"').lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise ValueError(f"Invalid boolean value: {value}")


FIELD_MAP = {
    "REPORTING_PROCESS_baselineCost":              ("baselineCost",              float),
    "REPORTING_PROCESS_llmCost":                   ("llmCost",                   float),
    "REPORTING_PROCESS_automationCost":            ("automationCost",            float),
    "REPORTING_PROCESS_totalCost":                 ("totalCost",                 float),
    "REPORTING_PROCESS_valueCreated":              ("valueCreated",              float),
    "REPORTING_PROCESS_agentTaskCount":            ("agentTaskCount",            int),
    "REPORTING_PROCESS_humanTaskCount":            ("humanTaskCount",            int),
    "REPORTING_PROCESS_autoTaskCount":             ("autoTaskCount",             int),
    "REPORTING_PROCESS_tokenUsage":                ("tokenUsage",                int),
    "REPORTING_PROCESS_processLabel":              ("processLabel",              str),
    "REPORTING_PROCESS_startDate":                 ("startDate",                 str),
    "REPORTING_PROCESS_endDate":                   ("endDate",                   str),
    "REPORTING_PROCESS_errorCount":                ("errorCount",                int),
    "REPORTING_PROCESS_retryCount":                ("retryCount",                int),
    "REPORTING_PROCESS_processingTimeMs":          ("processingTimeMs",          int),
    "REPORTING_PROCESS_queueWaitTimeMs":           ("queueWaitTimeMs",           int),
    "REPORTING_PROCESS_apiCallCount":              ("apiCallCount",              int),
    "REPORTING_PROCESS_complianceChecksPassed":    ("complianceChecksPassed",    int),
    "REPORTING_PROCESS_dataVolumeMb":              ("dataVolumeMb",              float),
    "REPORTING_PROCESS_confidenceScore":           ("confidenceScore",           float),
    "REPORTING_PROCESS_co2EmissionsKg":            ("co2EmissionsKg",            float),
    "REPORTING_PROCESS_customerSatisfactionScore": ("customerSatisfactionScore", float),
    "REPORTING_PROCESS_fraudRiskScore":            ("fraudRiskScore",            float),
    "REPORTING_PROCESS_externalServiceCostUsd":    ("externalServiceCostUsd",    float),
    "REPORTING_PROCESS_slaBreached":               ("slaBreached",               parse_bool),
    "REPORTING_PROCESS_escalated":                 ("escalated",                 parse_bool),
    "REPORTING_PROCESS_manualOverride":            ("manualOverride",            parse_bool),
}


def load_position() -> int:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text()).get("position", -1)
    return -1


def save_position(position: int) -> None:
    STATE_FILE.write_text(json.dumps({"position": position}))


def ensure_index_exists(es: Elasticsearch) -> None:
    if es.indices.exists(index=DEST_INDEX):
        return
    es.indices.create(
        index=DEST_INDEX,
        mappings={
            "properties": {
                "processInstanceKey":           {"type": "keyword"},
                "processDefinitionKey":         {"type": "keyword"},
                "tenantId":                     {"type": "keyword"},
                "startDate":                    {"type": "date"},
                "endDate":                      {"type": "date"},
                "firstSeenAt":                  {"type": "date"},
                "lastSeenAt":                   {"type": "date"},
                "state":                        {"type": "keyword"},
                "processLabel":                 {"type": "keyword"},
                "baselineCost":                 {"type": "double"},
                "llmCost":                      {"type": "double"},
                "automationCost":               {"type": "double"},
                "totalCost":                    {"type": "double"},
                "valueCreated":                 {"type": "double"},
                "agentTaskCount":               {"type": "integer"},
                "humanTaskCount":               {"type": "integer"},
                "autoTaskCount":                {"type": "integer"},
                "tokenUsage":                   {"type": "long"},
                "errorCount":                   {"type": "integer"},
                "retryCount":                   {"type": "integer"},
                "processingTimeMs":             {"type": "integer"},
                "queueWaitTimeMs":              {"type": "integer"},
                "apiCallCount":                 {"type": "integer"},
                "complianceChecksPassed":       {"type": "integer"},
                "dataVolumeMb":                 {"type": "double"},
                "confidenceScore":              {"type": "double"},
                "co2EmissionsKg":               {"type": "double"},
                "customerSatisfactionScore":    {"type": "double"},
                "fraudRiskScore":               {"type": "double"},
                "externalServiceCostUsd":       {"type": "double"},
                "slaBreached":                  {"type": "boolean"},
                "escalated":                    {"type": "boolean"},
                "manualOverride":               {"type": "boolean"},
            }
        },
    )
    print(f"  Created index '{DEST_INDEX}'.")


def print_index_stats(es: Elasticsearch) -> None:
    """Prints live docs, deleted docs, store size and estimated wasted bytes for DEST_INDEX."""
    try:
        t0 = time.monotonic()
        stats = es.indices.stats(index=DEST_INDEX, metric=["docs", "store"])
        segs  = es.indices.segments(index=DEST_INDEX)
        elapsed = time.monotonic() - t0

        idx      = stats["indices"][DEST_INDEX]["total"]
        live     = idx["docs"]["count"]
        deleted  = idx["docs"]["deleted"]
        size_b   = idx["store"]["size_in_bytes"]

        # Estimate wasted bytes from segment data
        wasted_b = 0
        for _shard in segs["indices"][DEST_INDEX]["shards"].values():
            for seg in _shard[0]["segments"].values():
                num  = seg.get("num_docs", 0)
                dels = seg.get("deleted_docs", 0)
                seg_b = seg.get("size_in_bytes", 0)
                if num + dels > 0:
                    wasted_b += int(seg_b * dels / (num + dels))

        def fmt(b: int) -> str:
            for unit in ("B", "KB", "MB", "GB"):
                if b < 1024:
                    return f"{b:.1f} {unit}"
                b /= 1024
            return f"{b:.1f} TB"

        dirty_pct = 100 * deleted / (live + deleted) if (live + deleted) > 0 else 0
        print(
            f"  index stats ({elapsed:.2f}s): "
            f"live={live:,}  deleted={deleted:,} ({dirty_pct:.1f}% dirty)  "
            f"size={fmt(size_b)}  wasted≈{fmt(wasted_b)}"
        )
    except Exception as e:
        print(f"  index stats: unavailable ({e})")


def count_pending(es: Elasticsearch, from_position: int) -> int:
    """Returns the number of REPORTING_PROCESS_* variable records not yet imported."""
    t0 = time.monotonic()
    result = es.count(
        index=SOURCE_INDEX,
        query={
            "bool": {
                "must": [
                    {"prefix": {"value.name.keyword": "REPORTING_PROCESS_"}},
                    {"range": {"position": {"gt": from_position}}},
                ]
            }
        },
    )
    count = result["count"]
    print(f"  pending check: {time.monotonic() - t0:.2f}s — {count} variable records still pending in {SOURCE_INDEX}")
    return count


def run_import_round(es: Elasticsearch, from_position: int) -> tuple[int, int, int, int, int, int]:
    """
    Fetch REPORTING_PROCESS_* variable records with position > from_position,
    group by processInstanceKey, bulk-upsert into dest index.

    Returns (new_max_position, records_read, pis_upserted).
    """
    ensure_index_exists(es)

    # ── Fetch ──────────────────────────────────────────────────────────────
    t0 = time.monotonic()
    pi_docs: dict[str, dict] = defaultdict(dict)
    max_position = from_position
    records_read = 0
    page = 0

    resp = es.search(
        index=SOURCE_INDEX,
        query={
            "bool": {
                "must": [
                    {"prefix": {"value.name.keyword": "REPORTING_PROCESS_"}},
                    {"range": {"position": {"gt": from_position}}},
                ]
            }
        },
        _source=["position", "timestamp", "value"],
        sort=[{"position": "asc"}],
        scroll="2m",
        size=PAGE_SIZE,
    )
    scroll_id = resp["_scroll_id"]
    hits = resp["hits"]["hits"]

    while hits:
        page += 1
        for hit in hits:
            src = hit["_source"]
            pos = src["position"]
            if pos > max_position:
                max_position = pos
            v = src["value"]
            pi_key = str(v["processInstanceKey"])
            mapping = FIELD_MAP.get(v["name"])
            if mapping is None:
                continue
            records_read += 1
            field_name, cast = mapping
            ts = src.get("timestamp")  # epoch ms from the Zeebe record
            if pi_key not in pi_docs:
                pi_docs[pi_key] = {
                    "processInstanceKey": pi_key,
                    "processDefinitionKey": str(v.get("processDefinitionKey", "")),
                    "tenantId": v.get("tenantId", ""),
                }
            try:
                pi_docs[pi_key][field_name] = cast(v["value"].strip('"'))
            except (TypeError, ValueError) as e:
                print(
                    f"  skipping value for {v['name']} on PI {pi_key}: "
                    f"could not parse {v['value']!r} ({e})"
                )
                continue
            if ts is not None:
                prev_first = pi_docs[pi_key].get("firstSeenAt")
                prev_last  = pi_docs[pi_key].get("lastSeenAt")
                if prev_first is None or ts < prev_first:
                    pi_docs[pi_key]["firstSeenAt"] = ts
                if prev_last is None or ts > prev_last:
                    pi_docs[pi_key]["lastSeenAt"] = ts

        resp = es.scroll(scroll_id=scroll_id, scroll="2m")
        scroll_id = resp["_scroll_id"]
        hits = resp["hits"]["hits"]

    fetch_s = time.monotonic() - t0
    print(f"  fetch : {fetch_s:.2f}s — {records_read} REPORTING_PROCESS_* variable records from {SOURCE_INDEX} in {page} page(s)")

    if not pi_docs:
        return max_position, 0, 0, 0, 0, 0

    # ── Upsert ─────────────────────────────────────────────────────────────
    t1 = time.monotonic()
    created = updated = errors = 0
    bulk_ops = []

    def flush_bulk(ops: list) -> None:
        nonlocal created, updated, errors
        resp = es.bulk(operations=ops)
        for item in resp["items"]:
            result = item["update"].get("result")
            status = item["update"].get("status", 0)
            if status >= 400:
                errors += 1
            elif result == "created":
                created += 1
            else:
                updated += 1

    for pi_key, doc in pi_docs.items():
        bulk_ops.append({"update": {"_index": DEST_INDEX, "_id": pi_key}})
        bulk_ops.append({"doc": doc, "doc_as_upsert": True})
        if len(bulk_ops) >= 500:
            flush_bulk(bulk_ops)
            bulk_ops.clear()
    if bulk_ops:
        flush_bulk(bulk_ops)

    upsert_s = time.monotonic() - t1
    error_str = f"  ⚠ {errors} errors" if errors else ""
    print(f"  upsert: {upsert_s:.2f}s — {len(pi_docs)} PIs ({created} created, {updated} updated){error_str}")

    return max_position, records_read, len(pi_docs), created, updated, errors


def print_summary(round_stats: list[dict], total_elapsed: float) -> None:
    """Print a per-round summary table after the run ends."""
    print("\n" + "═" * 92)
    print("  SUMMARY REPORT")
    print("═" * 92)

    if not round_stats:
        print("  No rounds completed.")
        print("═" * 92)
        return

    header = (
        f"  {'Round':>5}  {'Records':>10}  {'PIs':>7}  {'Created':>8}  "
        f"{'Updated':>8}  {'Errors':>6}  {'Elapsed':>8}  Position"
    )
    print(header)
    print("  " + "-" * 88)

    total_records = total_pis = total_created = total_updated = total_errors = 0
    for r in round_stats:
        print(
            f"  {r['round']:>5}  {r['records']:>10,}  {r['pis']:>7,}  {r['created']:>8,}  "
            f"{r['updated']:>8,}  {r['errors']:>6,}  {r['elapsed']:>7.2f}s  {r['position']}"
        )
        total_records  += r["records"]
        total_pis      += r["pis"]
        total_created  += r["created"]
        total_updated  += r["updated"]
        total_errors   += r["errors"]

    print("  " + "-" * 88)
    active_elapsed = sum(r["elapsed"] for r in round_stats if r["pis"] > 0)
    pi_per_s = total_pis / active_elapsed if active_elapsed > 0 else 0
    print(
        f"  {'TOTAL':>5}  {total_records:>10,}  {total_pis:>7,}  {total_created:>8,}  "
        f"{total_updated:>8,}  {total_errors:>6,}  {total_elapsed:>7.2f}s"
    )
    print(f"\n  {len(round_stats)} round(s)  ·  {pi_per_s:.0f} PI/s overall"
          + (f"  ·  ⚠ {total_errors} total errors" if total_errors else ""))
    print("═" * 92)


def main() -> None:
    es = Elasticsearch(ES_HOST)
    overall_start = time.monotonic()
    round_num = 0
    round_stats: list[dict] = []

    print(f"Starting — interval {RUN_EVERY_S}s, total {TOTAL_DURATION_S}s")
    print(f"State file: {STATE_FILE}")
    if STATE_FILE.exists():
        print(f"  ⚠  Resuming from saved position. Delete {STATE_FILE.name} to reprocess from the beginning.")
    else:
        print(f"  No state file found — starting from position -1 (full reprocess).")
    print()

    try:
        while True:
            if time.monotonic() - overall_start >= TOTAL_DURATION_S:
                print(f"Total duration reached ({TOTAL_DURATION_S}s). Stopping.")
                break

            round_num += 1
            position = load_position()
            t0 = time.monotonic()
            print(f"[Round {round_num}] position={position}")
            print_index_stats(es)

            new_position, records_read, pis_upserted, created, updated, errors = run_import_round(es, position)

            if new_position > position:
                save_position(new_position)

            elapsed = time.monotonic() - t0
            pi_per_s = pis_upserted / elapsed if elapsed > 0 else 0
            print(
                f"[Round {round_num}] done in {elapsed:.2f}s — "
                f"{pis_upserted} PIs ({pi_per_s:.0f} PI/s), "
                f"new position: {new_position}"
            )
            print_index_stats(es)

            round_stats.append({
                "round":    round_num,
                "records":  records_read,
                "pis":      pis_upserted,
                "created":  created,
                "updated":  updated,
                "errors":   errors,
                "elapsed":  elapsed,
                "position": new_position,
            })

            remaining = TOTAL_DURATION_S - (time.monotonic() - overall_start)
            if remaining <= 0:
                continue

            pending = count_pending(es, new_position)
            if pending > 0:
                print(f"  {pending} records still pending — running next round immediately\n")
            else:
                sleep_s = min(RUN_EVERY_S, remaining)
                print(f"  caught up — sleeping {sleep_s:.0f}s...\n")
                time.sleep(sleep_s)

    except KeyboardInterrupt:
        print("\nInterrupted.")

    total_elapsed = time.monotonic() - overall_start
    print_summary(round_stats, total_elapsed)
    print(f"Finished. Total time: {total_elapsed:.2f}s across {round_num} round(s).")


if __name__ == "__main__":
    main()
