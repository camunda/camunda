#!/usr/bin/env python3
"""Generates c7-benchmark.json. Edit here, then run: python3 generate_dashboard.py"""
import json
import pathlib

NS = 'namespace=~"$namespace"'
RI = "$__rate_interval"
panels = []
y = 0
pid = 0


def next_id():
    global pid
    pid += 1
    return pid


def row(title):
    global y
    panels.append({"type": "row", "title": title, "id": next_id(), "collapsed": False,
                   "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}, "panels": []})
    y += 1


def ts(title, targets, unit="short", w=12, h=8, x=0, desc="", stack=False):
    panels.append({
        "type": "timeseries", "title": title, "id": next_id(), "description": desc,
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "fieldConfig": {"defaults": {"unit": unit, "custom": {
            "lineWidth": 1, "fillOpacity": 10, "showPoints": "never",
            "stacking": {"mode": "normal" if stack else "none"}}}, "overrides": []},
        "options": {"legend": {"displayMode": "table", "placement": "bottom",
                               "calcs": ["mean", "max", "lastNotNull"]},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
        "targets": [{"refId": chr(65 + i), "expr": e, "legendFormat": l,
                     "datasource": {"type": "prometheus", "uid": "prometheus"}}
                    for i, (e, l) in enumerate(targets)],
    })


def stat(title, expr, unit="short", w=4, x=0, decimals=None):
    p = {
        "type": "stat", "title": title, "id": next_id(),
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "gridPos": {"h": 4, "w": w, "x": x, "y": y},
        "fieldConfig": {"defaults": {"unit": unit, "color": {"mode": "fixed", "fixedColor": "blue"}},
                        "overrides": []},
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "colorMode": "value",
                    "graphMode": "area", "textMode": "value"},
        "targets": [{"refId": "A", "expr": expr,
                     "datasource": {"type": "prometheus", "uid": "prometheus"}}],
    }
    if decimals is not None:
        p["fieldConfig"]["defaults"]["decimals"] = decimals
    panels.append(p)


def advance(h=8):
    global y
    y += h


def q(quantile, metric, by="le", extra=""):
    sel = NS + (", " + extra if extra else "")
    return f'histogram_quantile({quantile}, sum by ({by}) (rate({metric}_bucket{{{sel}}}[{RI}])))'


HI = 'table="act_hi_procinst"'

# ---------------------------------------------------------------- summary
row("Summary")
stat("Started PI/s", f'sum(rate(c7_process_instances_total{{{NS}, event="started"}}[{RI}]))', "short", x=0, decimals=1)
stat("Completed PI/s", f'sum(rate(c7_process_instances_total{{{NS}, event="completed"}}[{RI}]))', "short", x=4, decimals=1)
stat("PI duration p50", q(0.5, "c7_process_instance_duration_seconds"), "s", x=8)
stat("PI duration p99", q(0.99, "c7_process_instance_duration_seconds"), "s", x=12)
stat("History PIs (act_hi_procinst)", f'sum(cnpg_c7_table_rows{{{NS}, {HI}}})', "short", x=16)
stat("Database size", f'sum(cnpg_c7_db_size_bytes{{{NS}}})', "bytes", x=20)
advance(4)

# ---------------------------------------------------------------- throughput
row("Throughput")
ts("Process instances per second", [
    (f'sum(rate(c7_process_instances_total{{{NS}, event="started"}}[{RI}]))', "started (engine)"),
    (f'sum(rate(c7_process_instances_total{{{NS}, event="completed"}}[{RI}]))', "completed (engine)"),
    (f'sum(rate(starter_process_instances_submissions_total{{{NS}, outcome="sent"}}[{RI}]))', "requested (starter)"),
], "short", x=0, desc="Engine counters are recorded on transaction commit. Completed is the C8 PI/s equivalent.")
ts("Start requests by outcome", [
    (f'sum by (outcome, error) (rate(starter_process_instances_started_total{{{NS}}}[{RI}]))', "{{outcome}} {{error}}"),
    (f'sum(rate(starter_process_instances_submissions_total{{{NS}, outcome="skipped"}}[{RI}]))', "skipped (in-flight limit)"),
], "short", x=12)
advance()
ts("External tasks per second", [
    (f'sum by (event) (rate(c7_external_tasks_total{{{NS}}}[{RI}]))', "{{event}} (engine)"),
    (f'sum(rate(worker_tasks_received_total{{{NS}}}[{RI}]))', "received (workers)"),
    (f'sum by (outcome) (rate(worker_tasks_completed_total{{{NS}}}[{RI}]))', "completed {{outcome}} (workers)"),
], "short", x=0)
ts("Backlog", [
    (f'sum(starter_process_instances_in_flight{{{NS}}})', "start requests in flight"),
    (f'sum(cnpg_c7_runtime_external_tasks{{{NS}}})', "open external tasks"),
    (f'sum(cnpg_c7_runtime_external_tasks_locked{{{NS}}})', "locked external tasks"),
    (f'sum(worker_tasks_in_progress{{{NS}}})', "tasks in progress (workers)"),
    (f'sum(cnpg_c7_runtime_process_instances{{{NS}}})', "running process instances"),
], "short", x=12, desc="Open external tasks growing = workers or engine cannot keep up.")
advance()

# ---------------------------------------------------------------- latency
row("Latency")
ts("Process instance duration", [
    (q(0.5, "c7_process_instance_duration_seconds"), "p50"),
    (q(0.9, "c7_process_instance_duration_seconds"), "p90"),
    (q(0.99, "c7_process_instance_duration_seconds"), "p99"),
], "s", x=0, desc="ACT_HI_PROCINST end - start. Includes worker completion delay. C8 equivalent: zeebe_process_instance_execution_time.")
ts("Start request latency (starter)", [
    (q(0.5, "starter_response_latency_seconds"), "p50"),
    (q(0.9, "starter_response_latency_seconds"), "p90"),
    (q(0.99, "starter_response_latency_seconds"), "p99"),
], "s", x=12)
advance()
ts("External task wait (created until fetched)", [
    (q(0.5, "worker_task_wait_seconds"), "p50"),
    (q(0.99, "worker_task_wait_seconds"), "p99"),
], "s", x=0)
ts("Complete call latency (worker)", [
    (q(0.5, "worker_complete_latency_seconds"), "p50"),
    (q(0.99, "worker_complete_latency_seconds"), "p99"),
], "s", x=12)
advance()

# ---------------------------------------------------------------- engine
row("Engine commands (one command = one DB transaction)")
ts("Commands per second", [
    (f'sum by (command) (rate(c7_command_duration_seconds_count{{{NS}}}[{RI}]))', "{{command}}"),
], "short", x=0)
ts("Command duration p99", [
    (q(0.99, "c7_command_duration_seconds", "le, command"), "{{command}}"),
], "s", x=12)
advance()
ts("Failed commands per second", [
    (f'sum by (command, outcome) (rate(c7_command_duration_seconds_count{{{NS}, outcome!="success"}}[{RI}]))', "{{command}} {{outcome}}"),
], "short", x=0, desc="optimistic_locking rises with several engines or contended process instances.")
ts("Connection pool (Hikari)", [
    (f'sum by (pod) (hikaricp_connections_active{{{NS}}})', "active {{pod}}"),
    (f'sum by (pod) (hikaricp_connections_pending{{{NS}}})', "pending {{pod}}"),
    (f'max by (pod) (hikaricp_connections_max{{{NS}}})', "max {{pod}}"),
], "short", x=12)
advance()

# ---------------------------------------------------------------- growing state
row("Growing state")
ts("Rows per table", [
    (f'sum by (table) (cnpg_c7_table_rows{{{NS}, table=~"act_hi_.*|act_ru_.*|act_ge_bytearray"}})', "{{table}}"),
], "short", x=0, desc="n_live_tup estimate. History tables grow unless history cleanup is enabled.")
ts("Size per table (incl. indexes)", [
    (f'topk(10, sum by (table) (cnpg_c7_table_total_bytes{{{NS}}}))', "{{table}}"),
], "bytes", x=12)
advance()
ts("Throughput vs. history size", [
    (f'sum(rate(c7_process_instances_total{{{NS}, event="completed"}}[{RI}]))', "completed PI/s"),
    (f'sum(cnpg_c7_table_rows{{{NS}, {HI}}}) / 1e6', "act_hi_procinst rows (millions)"),
], "short", x=0, desc="Overlay to spot throughput degradation as history grows.")
ts("Latency vs. history size", [
    (q(0.99, "c7_process_instance_duration_seconds"), "PI duration p99 (s)"),
    (f'sum(cnpg_c7_table_rows{{{NS}, {HI}}}) / 1e6', "act_hi_procinst rows (millions)"),
], "short", x=12)
advance()

# ---------------------------------------------------------------- database
row("PostgreSQL")
ts("Transactions per second", [
    (f'sum(rate(cnpg_c7_db_xact_commit{{{NS}}}[{RI}]))', "commit"),
    (f'sum(rate(cnpg_c7_db_xact_rollback{{{NS}}}[{RI}]))', "rollback"),
], "short", x=0)
ts("Rows written per second", [
    (f'sum(rate(cnpg_c7_db_tup_inserted{{{NS}}}[{RI}]))', "inserted"),
    (f'sum(rate(cnpg_c7_db_tup_updated{{{NS}}}[{RI}]))', "updated"),
    (f'sum(rate(cnpg_c7_db_tup_deleted{{{NS}}}[{RI}]))', "deleted"),
], "short", x=12)
advance()
ts("WAL bytes per second", [
    (f'sum(rate(cnpg_c7_wal_wal_bytes{{{NS}}}[{RI}]))', "WAL"),
], "Bps", x=0)
ts("WAL bytes per completed process instance", [
    (f'sum(rate(cnpg_c7_wal_wal_bytes{{{NS}}}[{RI}])) / sum(rate(c7_process_instances_total{{{NS}, event="completed"}}[{RI}]))', "bytes/PI"),
    (f'sum(rate(cnpg_c7_wal_wal_bytes{{{NS}}}[{RI}])) / sum(rate(c7_command_duration_seconds_count{{{NS}}}[{RI}]))', "bytes/engine transaction"),
], "bytes", x=12, desc="Input for the single-instance capacity estimate (WAL bytes and transactions per command).")
advance()
ts("Connections by state / wait event", [
    (f'sum by (state, wait_event_type) (cnpg_c7_activity_connections{{{NS}}})', "{{state}} / {{wait_event_type}}"),
], "short", x=0, desc="active / Lock = row lock contention.")
ts("Buffer cache hit ratio", [
    (f'sum(rate(cnpg_c7_db_blks_hit{{{NS}}}[{RI}])) / (sum(rate(cnpg_c7_db_blks_hit{{{NS}}}[{RI}])) + sum(rate(cnpg_c7_db_blks_read{{{NS}}}[{RI}])))', "hit ratio"),
], "percentunit", x=12)
advance()
ts("Dead rows (vacuum pressure)", [
    (f'topk(10, sum by (table) (cnpg_c7_table_dead_rows{{{NS}}}))', "{{table}}"),
], "short", x=0)
ts("Sequential scans per second", [
    (f'topk(10, sum by (table) (rate(cnpg_c7_table_seq_scan{{{NS}}}[{RI}])))', "{{table}}"),
], "short", x=12, desc="Seq scans on large history tables hint at missing indexes.")
advance()

# ---------------------------------------------------------------- resources
row("Resources")
ts("CPU cores used (JVM)", [
    (f'sum by (pod) (process_cpu_usage{{{NS}}} * system_cpu_count{{{NS}}})', "{{pod}}"),
], "short", x=0)
ts("CPU cores used (containers, Kubernetes only)", [
    (f'sum by (pod) (rate(container_cpu_usage_seconds_total{{{NS}, container!=""}}[{RI}]))', "{{pod}}"),
], "short", x=12)
advance()
ts("JVM heap used", [
    (f'sum by (pod) (jvm_memory_used_bytes{{{NS}, area="heap"}})', "{{pod}}"),
], "bytes", x=0)
ts("GC pause time per second", [
    (f'sum by (pod) (rate(jvm_gc_pause_seconds_sum{{{NS}}}[{RI}]))', "{{pod}}"),
], "s", x=12)
advance()

dashboard = {
    "uid": "c7-benchmark",
    "title": "C7 Benchmark",
    "tags": ["c7", "benchmark"],
    "timezone": "browser",
    "schemaVersion": 39,
    "refresh": "30s",
    "time": {"from": "now-1h", "to": "now"},
    "templating": {"list": [{
        "name": "namespace", "label": "namespace", "type": "query",
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "query": {"query": "label_values(c7_process_instances_total, namespace)", "refId": "ns"},
        "definition": "label_values(c7_process_instances_total, namespace)",
        "refresh": 2, "includeAll": False, "multi": True, "sort": 1,
    }]},
    "panels": panels,
}

out = pathlib.Path(__file__).with_name("c7-benchmark.json")
out.write_text(json.dumps(dashboard, indent=2) + "\n")
print(f"wrote {out} ({len(panels)} panels)")
