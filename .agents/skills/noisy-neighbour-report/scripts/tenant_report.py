#!/usr/bin/env python3
# owner: @camunda/reliability-testing
"""Per physical tenant report of a load test, for the noisy neighbour work.

Answers, for one namespace and one time window: how is the load shared between the tenants, how many
tenants still get the service their own load asks for, and how much do load, latency, secondary storage
latency and the export queue vary between the tenants.

Data comes from Prometheus (the load test one, see `loadtestctl prometheus`):
  * the load tester pods `starter[-ptN]-*` and `worker[-ptN]-*`, one set per tenant, aggregated by tenant
    name (no tenant in the pod name means the default tenant);
  * the broker metrics, which carry the `physicalTenant` label, for the export queue and the context.

Usage:
  tenant_report.py <namespace> [--duration 900] [--end <rfc3339|epoch>] [--reference <namespace>]
                   [--saturating pt1,pt2] [--weights pt2=3] [--output report.md] [--json report.json]

The Prometheus URL, user and password come from LOADTESTCTL_PROMETHEUS_URL, _USER and _PASSWORD, like the
other loadtestctl commands, or from --prometheus-url.
"""

from __future__ import annotations

import argparse
import base64
import json
import math
import os
import re
import statistics
import sys
import time
import urllib.parse
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime

QueryFn = Callable[[str, float | None], list[dict]]

QUANTILES = (0.5, 0.9, 0.99)
SLO_QUANTILE = 0.99
DEFAULT_SLO_SECONDS = 5.0
BACKPRESSURE_ERRORS = ("resource_exhausted", "503", "429")
NAMESPACE_RE = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
# A tenant that offers at least this many times the median load of all tenants is the noisy one.
SATURATING_FACTOR = 2.0


class InputError(ValueError):
    """The arguments cannot describe a valid report."""


@dataclass
class Tenant:
    name: str
    weight: float = 1.0
    role: str = "quiet"
    submitted: float = 0.0
    skipped: float = 0.0
    success: float = 0.0
    failed: float = 0.0
    backpressured: float = 0.0
    response: dict[float, float] = field(default_factory=dict)
    availability: dict[float, float] = field(default_factory=dict)
    # share of the requests, resp. instances, that finished within the latency target (None: not measurable)
    response_within: float | None = None
    availability_within: float | None = None
    pending_avg: float | None = None
    in_flight_avg: float | None = None
    jobs_handled: float = 0.0
    jobs_activated: float = 0.0
    export_backlog: float | None = None
    export_backlog_start: float | None = None
    export_backlog_max: float | None = None
    export_rate: float | None = None
    # partition -> {"backlog": records not yet exported, "rate": exported per second (slowest exporter)}
    export_partitions: dict[str, dict[str, float]] = field(default_factory=dict)

    @property
    def offered(self) -> float:
        return self.submitted + self.skipped or self.success + self.failed

    @property
    def service_ratio(self) -> float | None:
        return self.success / self.offered if self.offered > 0 else None

    @property
    def rejected_ratio(self) -> float | None:
        answered = self.success + self.failed
        return self.failed / answered if answered > 0 else None

    @property
    def skipped_ratio(self) -> float | None:
        scheduled = self.submitted + self.skipped
        return self.skipped / scheduled if scheduled > 0 else None

    @property
    def export_growth(self) -> float | None:
        if self.export_backlog is None or self.export_backlog_start is None:
            return None
        return self.export_backlog - self.export_backlog_start

    def partition_lag_seconds(self, partition: str) -> float | None:
        values = self.export_partitions.get(partition, {})
        return lag_seconds(values.get("backlog"), values.get("rate"))

    @property
    def export_lag_seconds(self) -> float | None:
        """Time the slowest partition needs to clear its queue at the rate it exports at.

        The worst partition decides: dividing the summed queue by the summed rate lets the fast
        partitions hide a slow one."""
        lags = [lag for p in self.export_partitions if (lag := self.partition_lag_seconds(p)) is not None]
        if lags:
            return max(lags)
        return lag_seconds(self.export_backlog, self.export_rate)


def lag_seconds(backlog: float | None, rate: float | None) -> float | None:
    """Seconds to clear a queue. Infinite when records wait and nothing is exported (a stall), None when
    the queue or the rate was not measured."""
    if backlog is None or rate is None:
        return None
    if backlog <= 0:
        return 0.0
    return backlog / rate if rate > 0 else math.inf


def check_namespace(namespace: str) -> str:
    if not NAMESPACE_RE.match(namespace):
        raise InputError(f"'{namespace}' is not a valid namespace name")
    return namespace


def tenant_expr(inner: str, kind: str) -> str:
    """Add a `tenant` label to the series of `kind` pods: pt<N> from the pod name, else default."""
    return (
        f'label_replace(label_replace({inner}, "tenant", "$1", "pod", "{kind}-(pt[0-9]+)-.*"), '
        f'"tenant", "default", "tenant", "")'
    )


def sample_value(result: dict) -> float | None:
    try:
        value = float(result["value"][1])
    except (KeyError, IndexError, TypeError, ValueError):
        return None
    return None if math.isnan(value) or math.isinf(value) else value


def parse_weights(raw: str, tenants: list[str]) -> dict[str, float]:
    weights: dict[str, float] = {}
    for entry in filter(None, (part.strip() for part in raw.split(","))):
        name, sep, value = entry.partition("=")
        name = name.strip()
        try:
            number = float(value)
        except ValueError:
            number = 0.0
        if not sep or name not in tenants or number <= 0:
            raise InputError(f"--weights: bad entry '{entry}' (tenants: {', '.join(sorted(tenants))})")
        weights[name] = number
    return weights


def collect_client(
    query: QueryFn, namespace: str, window_s: int, at: float | None, slo_seconds: float = DEFAULT_SLO_SECONDS
) -> dict[str, Tenant]:
    """Per tenant load, outcome, latency and storage availability, as the load testers saw them."""
    sel = f'namespace="{check_namespace(namespace)}"'
    w = f"{window_s}s"
    tenants: dict[str, Tenant] = {}

    def tenant(result: dict) -> Tenant:
        name = result["metric"].get("tenant") or "default"
        return tenants.setdefault(name, Tenant(name))

    def by_tenant(inner: str, kind: str, by: str, func: str = "sum") -> list[dict]:
        return query(f"{func} by ({by}) ({tenant_expr(inner, kind)})", at)

    starter = f'{{{sel}, pod=~"starter-.*"}}'
    worker = f'{{{sel}, pod=~"worker-.*"}}'

    for r in by_tenant(
        f"rate(starter_process_instances_submissions_total{starter}[{w}])", "starter", "tenant, outcome"
    ):
        if (v := sample_value(r)) is not None:
            if r["metric"].get("outcome") == "skipped":
                tenant(r).skipped += v
            else:
                tenant(r).submitted += v

    for r in by_tenant(
        f"rate(starter_process_instances_started_total{starter}[{w}])", "starter", "tenant, outcome, error"
    ):
        if (v := sample_value(r)) is None:
            continue
        t = tenant(r)
        if r["metric"].get("outcome") == "success":
            t.success += v
        else:
            t.failed += v
            if any(k in r["metric"].get("error", "").lower() for k in BACKPRESSURE_ERRORS):
                t.backpressured += v

    for q in QUANTILES:
        for metric, target in (
            ("starter_response_latency_seconds_bucket", "response"),
            ("starter_data_availability_latency_seconds_bucket", "availability"),
        ):
            inner = f"rate({metric}{starter}[{w}])"
            for r in query(
                f"histogram_quantile({q}, sum by (tenant, le) ({tenant_expr(inner, 'starter')}))", at
            ):
                if (v := sample_value(r)) is not None:
                    getattr(tenant(r), target)[q] = v

    # A quantile in the last bucket is reported as the bound of the last finite bucket, so it cannot tell
    # whether a target at or beyond that bound is met. The share of observations within the target can:
    # it is only there when a bucket ends at the target.
    bound = re.escape(f"{slo_seconds:g}") + r"(\.0+)?"  # the le label is "5" or "5.0" depending on the library
    target_bucket = 'le=~"' + bound.replace("\\", "\\\\") + '"'  # backslashes doubled for the PromQL string

    def bucket_sum(metric: str, matcher: str) -> str:
        inner = f'rate({metric}{{{sel}, pod=~"starter-.*", {matcher}}}[{w}])'
        return f"sum by (tenant) ({tenant_expr(inner, 'starter')})"

    for metric, target in (
        ("starter_response_latency_seconds_bucket", "response_within"),
        ("starter_data_availability_latency_seconds_bucket", "availability_within"),
    ):
        within, total = bucket_sum(metric, target_bucket), bucket_sum(metric, 'le="+Inf"')
        for r in query(f"{within} / {total}", at):
            if (v := sample_value(r)) is not None:
                setattr(tenant(r), target, v)

    for r in by_tenant(
        f"avg_over_time(starter_data_availability_pending_process_instances{starter}[{w}])",
        "starter",
        "tenant",
    ):
        tenant(r).pending_avg = sample_value(r)
    for r in by_tenant(f"avg_over_time(starter_process_instances_in_flight{starter}[{w}])", "starter", "tenant"):
        tenant(r).in_flight_avg = sample_value(r)

    for metric, target in (
        ("camunda_client_worker_job_handled_total", "jobs_handled"),
        ("camunda_client_worker_job_activated_total", "jobs_activated"),
    ):
        for r in by_tenant(f"rate({metric}{worker}[{w}])", "worker", "tenant"):
            if (v := sample_value(r)) is not None:
                setattr(tenant(r), target, v)
    return tenants


def collect_export(query: QueryFn, namespace: str, window_s: int, at: float, tenants: dict[str, Tenant]) -> list[str]:
    """Queue of records committed but not yet exported to the secondary storage, per tenant.

    Returns notes about what could not be measured."""
    sel = f'namespace="{check_namespace(namespace)}"'
    w = f"{window_s}s"
    backlog = (
        f"sum by (physicalTenant) (max by (physicalTenant, partition) (zeebe_log_appender_last_committed_position{{{sel}}})"
        f" - max by (physicalTenant, partition) (zeebe_exporter_last_exported_position{{{sel}}}))"
    )
    notes: list[str] = []

    def apply(results: list[dict], attribute: str) -> int:
        count = 0
        for r in results:
            name = r["metric"].get("physicalTenant")
            if name is None or (v := sample_value(r)) is None:
                continue
            setattr(tenants.setdefault(name, Tenant(name)), attribute, v)
            count += 1
        return count

    found = apply(query(backlog, at), "export_backlog")
    apply(query(backlog, at - window_s), "export_backlog_start")
    apply(query(f"max_over_time(({backlog})[{w}:1m])", at), "export_backlog_max")
    apply(
        query(
            f"sum by (physicalTenant) (rate(zeebe_exporter_last_exported_position{{{sel}}}[{w}]))",
            at,
        ),
        "export_rate",
    )
    for attribute, promql in (
        (
            "backlog",
            f"max by (physicalTenant, partition) (zeebe_log_appender_last_committed_position{{{sel}}})"
            f" - min by (physicalTenant, partition) (zeebe_exporter_last_exported_position{{{sel}}})",
        ),
        ("rate", f"min by (physicalTenant, partition) (rate(zeebe_exporter_last_exported_position{{{sel}}}[{w}]))"),
    ):
        for r in query(promql, at):
            name, partition = r["metric"].get("physicalTenant"), r["metric"].get("partition")
            if name is None or partition is None or (v := sample_value(r)) is None:
                continue
            tenants.setdefault(name, Tenant(name)).export_partitions.setdefault(partition, {})[attribute] = v
    for t in tenants.values():
        rates = [v["rate"] for v in t.export_partitions.values() if "rate" in v]
        if rates:  # the slowest exporter of each partition, so the exporters of a partition are not added up
            t.export_rate = sum(rates)
    if not found:
        notes.append(
            "No export queue with a `physicalTenant` label was found: the broker metrics of this build or "
            "namespace carry no tenant label (or the exporter is not running). Check "
            "`count by (physicalTenant) (zeebe_exporter_last_exported_position)`."
        )
    return notes


def collect_context(query: QueryFn, namespace: str, window_s: int, at: float) -> dict:
    """Cluster wide numbers that explain the per tenant ones: backpressure, CPU and throttling."""
    sel = f'namespace="{check_namespace(namespace)}"'
    w = f"{window_s}s"

    def first(promql: str) -> float | None:
        results = query(promql, at)
        return sample_value(results[0]) if results else None

    def per_pod(promql: str) -> dict[str, float]:
        return {r["metric"].get("pod", "?"): v for r in query(promql, at) if (v := sample_value(r)) is not None}

    brokers = f'{sel}, pod=~"camunda-[0-9]+", container!=""'
    return {
        "dropped_ratio": first(
            f"sum(rate(zeebe_dropped_request_count_total{{{sel}}}[{w}]))"
            f" / sum(rate(zeebe_received_request_count_total{{{sel}}}[{w}]))"
        ),
        "cpu_cores": per_pod(f"sum by (pod) (rate(container_cpu_usage_seconds_total{{{brokers}}}[{w}]))"),
        "throttled_ratio": per_pod(
            f"sum by (pod) (rate(container_cpu_cfs_throttled_periods_total{{{brokers}}}[{w}]))"
            f" / sum by (pod) (rate(container_cpu_cfs_periods_total{{{brokers}}}[{w}]))"
        ),
    }


def classify(tenants: dict[str, Tenant], saturating: list[str] | None) -> None:
    """Mark the noisy tenants: the given ones, else those offering far more than the median load."""
    for t in tenants.values():
        if t.offered <= 0 and not t.response:
            t.role = "no load"  # a tenant without a load tester: only its export queue is known
    if saturating is not None:
        for name in saturating:
            if name not in tenants:
                raise InputError(f"--saturating: unknown tenant '{name}' (tenants: {', '.join(sorted(tenants))})")
            tenants[name].role = "saturating"
        return
    offered = [t.offered for t in tenants.values() if t.role != "no load"]
    if len(offered) < 3:
        return
    median = statistics.median(offered)
    for t in tenants.values():
        if median > 0 and t.offered >= SATURATING_FACTOR * median:
            t.role = "saturating"


@dataclass
class Verdict:
    failing: dict[str, list[str]]

    def passing(self, criterion: str | None = None) -> list[str]:
        return sorted(n for n, reasons in self.failing.items() if (not reasons if criterion is None else criterion not in reasons))


def evaluate(tenants: dict[str, Tenant], min_service: float, max_lag_seconds: float) -> Verdict:
    failing: dict[str, list[str]] = {}
    for name, t in tenants.items():
        reasons = []
        if t.role != "no load":
            if t.service_ratio is None or t.service_ratio < min_service:
                reasons.append("served")
            if t.response_within is None or t.response_within < SLO_QUANTILE:
                reasons.append("response")
            if t.availability_within is None or t.availability_within < SLO_QUANTILE:
                reasons.append("availability")
        lag = t.export_lag_seconds
        if lag is None or lag > max_lag_seconds:  # an export queue that was not measured is not a pass
            reasons.append("export")
        failing[name] = reasons
    return Verdict(failing)


def spread(values: list[float]) -> dict[str, float] | None:
    if not values:
        return None
    mean = statistics.fmean(values)
    stdev = statistics.pstdev(values)
    return {
        "n": len(values),
        "mean": mean,
        "min": min(values),
        "max": max(values),
        "stdev": stdev,
        "cv": stdev / mean if mean > 0 else 0.0,
    }


def jain(values: list[float]) -> float | None:
    """Jain's fairness index: 1 when all equal, 1/n when one gets everything."""
    if not values or sum(v * v for v in values) == 0:
        return None
    return sum(values) ** 2 / (len(values) * sum(v * v for v in values))


def variance_table(tenants: list[Tenant]) -> dict[str, dict[str, float] | None]:
    def values(getter: Callable[[Tenant], float | None]) -> list[float]:
        return [v for t in tenants if (v := getter(t)) is not None]

    return {
        "offered load (PI/s)": spread(values(lambda t: t.offered)),
        "served ratio": spread(values(lambda t: t.service_ratio)),
        "response latency p99 (s)": spread(values(lambda t: t.response.get(0.99))),
        "data availability p99 (s)": spread(values(lambda t: t.availability.get(0.99))),
        "instances waiting for storage (avg)": spread(values(lambda t: t.pending_avg)),
        "export queue (records)": spread(values(lambda t: t.export_backlog)),
        "export lag (s)": spread([v for v in values(lambda t: t.export_lag_seconds) if math.isfinite(v)]),
    }


def interference(noisy: dict[str, Tenant], reference: dict[str, Tenant]) -> list[dict]:
    """How much worse each quiet tenant is than in the reference run (the same tenant, no noise)."""
    rows = []
    for name, t in sorted(noisy.items()):
        ref = reference.get(name)
        if ref is None or t.role == "saturating":
            continue

        def ratio(now: float | None, before: float | None) -> float | None:
            if now is None or before in (None, 0) or math.isinf(now) or math.isinf(before):
                return None
            return now / before

        rows.append(
            {
                "tenant": name,
                "served": ratio(t.service_ratio, ref.service_ratio),
                "response_p99": ratio(t.response.get(0.99), ref.response.get(0.99)),
                "availability_p99": ratio(t.availability.get(0.99), ref.availability.get(0.99)),
                "export_lag": ratio(t.export_lag_seconds, ref.export_lag_seconds),
                "export_stalled": t.export_lag_seconds == math.inf,
            }
        )
    return rows


def fmt(value: float | None, unit: str = "", digits: int = 1) -> str:
    if value is None:
        return "n/a"
    if math.isinf(value):
        return "stalled"
    if unit == "%":
        return f"{100 * value:.0f}%"
    if unit == "s":
        return f"{value * 1000:.0f} ms" if value < 1 else f"{value:.1f} s"
    if unit == "x":
        return f"{value:.2f}x"
    return f"{value:.{digits}f}" if abs(value) < 1000 else f"{value:,.0f}"


def export_throughput_section(ordered: list[Tenant]) -> list[str]:
    """Export rate of every tenant (the sum of its partitions) and the total over the tenants."""
    rated = [t for t in ordered if t.export_rate is not None]
    if not rated:
        return []
    total = sum(t.export_rate for t in rated)
    out = [
        "## Export throughput",
        "",
        "Positions exported per second, averaged over the window: the sum of the partitions of the tenant, "
        "each at the speed of its slowest exporter. Every tenant has its own partitions, and a position is not "
        "a record, so the numbers compare tenants but are not a count of records.",
        "",
        "| Tenant | Role | Export rate (/s) | Share of the total |",
        "| --- | --- | --- | --- |",
    ]
    for t in rated:
        out.append(f"| {t.name} | {t.role} | {fmt(t.export_rate)} | {fmt(t.export_rate / total if total else None, '%')} |")
    out.append(f"| **All tenants** | | {fmt(total)} | 100% |")
    stats = spread([t.export_rate for t in rated])
    if stats and len(rated) > 1:
        out += [
            "",
            f"Per tenant: mean {fmt(stats['mean'])}/s, min {fmt(stats['min'])}/s, max {fmt(stats['max'])}/s, CV {stats['cv']:.2f}.",
        ]
    out.append("")
    return out


def render(
    namespace: str,
    window: str,
    tenants: dict[str, Tenant],
    verdict: Verdict,
    context: dict,
    notes: list[str],
    args: argparse.Namespace,
    reference_name: str | None,
    comparison: list[dict],
) -> str:
    ordered = sorted(tenants.values(), key=lambda t: (t.role != "saturating", t.name != "default", int(t.name[2:]) if t.name.startswith("pt") and t.name[2:].isdigit() else 0))
    quiet = [t for t in ordered if t.role == "quiet"]
    n = len(ordered)
    total_offered = sum(t.offered for t in ordered)
    total_served = sum(t.success for t in ordered)
    total_weight = sum(t.weight for t in ordered if t.role != "no load")
    out = [f"# Noisy neighbour report: `{namespace}`", "", f"Window: {window}.", ""]

    out += ["## Do the tenants get the service their own load asks for?", ""]
    labels = {
        "served": f"served at least {args.min_service:.0%} of their load",
        "response": f"response latency p99 within {args.slo_seconds:g} s",
        "availability": f"data in the secondary storage within {args.slo_seconds:g} s (p99)",
        "export": f"export queue clears within {args.lag_seconds:g} s",
    }
    for key, text in labels.items():
        ok = verdict.passing(key)
        out.append(f"- **{len(ok)} of {n}** tenants {text}." + ("" if len(ok) == n else f" Not: {', '.join(sorted(set(tenants) - set(ok)))}."))
    everything = verdict.passing()
    out += [f"- **{len(everything)} of {n}** tenants meet all of them.", ""]
    failing_quiet = [t.name for t in quiet if verdict.failing[t.name]]
    if failing_quiet:
        out += ["Quiet tenants that miss a target:", ""]
        out += [f"- `{name}`: {', '.join(verdict.failing[name])}" for name in failing_quiet]
        out.append("")

    out += [
        "## Per tenant",
        "",
        "| Tenant | Role | Offered (PI/s) | Served (PI/s) | Served | Rejected | Skipped | Response p50 / p99 | Data in storage p50 / p99 | Waiting for storage | Export queue | Export lag | Jobs handled (/s) |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for t in ordered:
        growth = t.export_growth
        queue = fmt(t.export_backlog) + (f" ({'+' if growth >= 0 else ''}{fmt(growth)})" if growth is not None else "")
        out.append(
            f"| {t.name} | {t.role} | {fmt(t.offered)} | {fmt(t.success)} | {fmt(t.service_ratio, '%')} | "
            f"{fmt(t.rejected_ratio, '%')} | {fmt(t.skipped_ratio, '%')} | "
            f"{fmt(t.response.get(0.5), 's')} / {fmt(t.response.get(0.99), 's')} | "
            f"{fmt(t.availability.get(0.5), 's')} / {fmt(t.availability.get(0.99), 's')} | "
            f"{fmt(t.pending_avg, digits=0)} | {queue} | {fmt(t.export_lag_seconds, 's')} | {fmt(t.jobs_handled)} |"
        )
    out += ["", "Served is the share of the offered load answered with success. Rejected is the share of answered requests that failed (backpressure is `resource exhausted` or HTTP 503/429). Skipped is load the starter could not even send. The export queue is records committed but not yet exported, with its change over the window in brackets.", ""]

    out += export_throughput_section(ordered)

    partitioned = [t for t in ordered if t.role == "saturating" and t.export_partitions]
    if partitioned:
        out += ["## Export per partition", ""]
        for t in partitioned:
            out += [
                f"`{t.name}`: the export lag of a tenant is its slowest partition.",
                "",
                "| Partition | Export queue | Export rate (/s) | Export lag |",
                "| --- | --- | --- | --- |",
            ]
            for partition in sorted(t.export_partitions, key=lambda p: int(p) if p.isdigit() else 0):
                values = t.export_partitions[partition]
                out.append(
                    f"| {partition} | {fmt(values.get('backlog'))} | {fmt(values.get('rate'))} | "
                    f"{fmt(t.partition_lag_seconds(partition), 's')} |"
                )
            out.append("")

    out += ["## How the load is shared", "", "| Tenant | Offered share | Served share | Fair share (weight) |", "| --- | --- | --- | --- |"]
    for t in ordered:
        out.append(
            f"| {t.name} | {fmt(t.offered / total_offered if total_offered else None, '%')} | "
            f"{fmt(t.success / total_served if total_served else None, '%')} | {fmt(t.weight / total_weight if t.role != 'no load' else None, '%')} |"
        )
    saturating = [t for t in ordered if t.role == "saturating"]
    if saturating and total_served:
        taken = sum(t.success for t in saturating) / total_served
        out += ["", f"The saturating tenants take {taken:.0%} of the served load and offer {sum(t.offered for t in saturating) / total_offered:.0%} of it." if total_offered else ""]
    out.append("")

    out += ["## Variance between the quiet tenants", ""]
    jain_served = jain([t.service_ratio for t in quiet if t.service_ratio is not None])
    if len(quiet) < 2:
        out += ["Fewer than two quiet tenants, so there is nothing to compare.", ""]
    else:
        out += [
            f"Quiet tenants: {len(quiet)}. Jain's fairness index of their served ratio: {fmt(jain_served, digits=3)} (1 is perfectly even).",
            "",
            "| Metric | Mean | Min | Max | Std dev | CV |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
        for name, s in variance_table(quiet).items():
            if s is None:
                out.append(f"| {name} | n/a | | | | |")
                continue
            unit = "%" if name == "served ratio" else "s" if "(s)" in name else ""
            out.append(
                f"| {name} | {fmt(s['mean'], unit)} | {fmt(s['min'], unit)} | {fmt(s['max'], unit)} | "
                f"{fmt(s['stdev'], unit)} | {s['cv']:.2f} |"
            )
        stalled = [t.name for t in quiet if t.export_lag_seconds == math.inf]
        if stalled:
            out += ["", f"Export stalled (a queue and no export) and left out of the export lag row: {', '.join(f'`{n}`' for n in stalled)}."]
        out += ["", "CV is the standard deviation divided by the mean. A high CV means the tenants are not treated alike: some get the service, others do not.", ""]

    if comparison:
        out += [
            f"## Against the reference run `{reference_name}`",
            "",
            "Ratio of the quiet tenants in this run to the same tenants in the reference run. Served below 1 and the other columns above 1 mean the noisy tenant hurt them.",
            "",
            "| Tenant | Served | Response p99 | Data in storage p99 | Export lag |",
            "| --- | --- | --- | --- | --- |",
        ]
        for row in comparison:
            out.append(
                f"| {row['tenant']} | {fmt(row['served'], 'x')} | {fmt(row['response_p99'], 'x')} | "
                f"{fmt(row['availability_p99'], 'x')} | "
                f"{'stalled' if row['export_stalled'] else fmt(row['export_lag'], 'x')} |"
            )
        out.append("")

    out += ["## Cluster context", ""]
    out.append(f"- Requests dropped by backpressure: {fmt(context.get('dropped_ratio'), '%')}")
    cpu, throttled = context.get("cpu_cores", {}), context.get("throttled_ratio", {})
    for pod in sorted(set(cpu) | set(throttled)):
        out.append(f"- `{pod}`: {fmt(cpu.get(pod), digits=2)} CPU cores, throttled {fmt(throttled.get(pod), '%')} of the time")
    if notes:
        out += ["", "## Notes", ""] + [f"- {note}" for note in notes]
    return "\n".join(out) + "\n"


def parse_time(raw: str | None) -> float:
    if raw is None:
        return time.time()
    try:
        return float(raw)
    except ValueError:
        return datetime.fromisoformat(raw.replace("Z", "+00:00")).timestamp()


def http_query(url: str, user: str | None, password: str | None) -> QueryFn:
    def query(promql: str, at: float | None) -> list[dict]:
        params = {"query": promql}
        if at is not None:
            params["time"] = f"{at:.3f}"
        request = urllib.request.Request(f"{url.rstrip('/')}/api/v1/query?{urllib.parse.urlencode(params)}")
        if user:
            token = base64.b64encode(f"{user}:{password or ''}".encode()).decode()
            request.add_header("Authorization", f"Basic {token}")
        with urllib.request.urlopen(request, timeout=60) as response:  # noqa: S310 (URL given by the user)
            body = json.load(response)
        if body.get("status") != "success":
            raise RuntimeError(f"Prometheus error for {promql[:80]}: {body}")
        return body["data"]["result"]

    return query


def build_report(query: QueryFn, args: argparse.Namespace) -> tuple[str, dict]:
    end = parse_time(args.end)
    window_s = args.duration
    window = f"{datetime.fromtimestamp(end - window_s):%Y-%m-%d %H:%M} to {datetime.fromtimestamp(end):%H:%M} ({window_s // 60} min)"
    tenants = collect_client(query, args.namespace, window_s, end, args.slo_seconds)
    if not tenants:
        raise InputError(f"No load tester metrics found for namespace '{args.namespace}' in this window")
    notes = collect_export(query, args.namespace, window_s, end, tenants)
    weights = parse_weights(args.weights, list(tenants))
    for name, weight in weights.items():
        tenants[name].weight = weight
    classify(tenants, args.saturating.split(",") if args.saturating else None)
    verdict = evaluate(tenants, args.min_service, args.lag_seconds)
    context = collect_context(query, args.namespace, window_s, end)
    for t in tenants.values():
        if not t.response:
            notes.append(f"No response latency for `{t.name}`: the starter metrics are missing in this window.")
        for what, within in (("response latency", t.response_within), ("data availability", t.availability_within)):
            if t.role != "no load" and within is None:
                notes.append(
                    f"The {what} of `{t.name}` was not measured against {args.slo_seconds:g} s (no data, or no histogram "
                    "bucket ends at that bound): the target counts as missed."
                )
        if t.export_lag_seconds is None:
            notes.append(f"No export queue or rate for `{t.name}`: the export target counts as missed.")
        if t.jobs_handled == 0 and t.jobs_activated == 0:
            notes.append(f"No worker job metrics for `{t.name}` (the client metrics may not be scraped).")
    comparison: list[dict] = []
    if args.reference:
        ref_end = parse_time(args.reference_end) if args.reference_end else end
        reference = collect_client(query, args.reference, window_s, ref_end, args.slo_seconds)
        collect_export(query, args.reference, window_s, ref_end, reference)
        comparison = interference(tenants, reference)
    markdown = render(args.namespace, window, tenants, verdict, context, sorted(set(notes)), args, args.reference, comparison)
    data = {
        "namespace": args.namespace,
        "window_seconds": window_s,
        "tenants": {n: {**t.__dict__, "offered": t.offered, "service_ratio": t.service_ratio} for n, t in tenants.items()},
        "failing": verdict.failing,
        "context": context,
        "comparison": comparison,
    }
    return markdown, data


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("namespace")
    p.add_argument("--duration", type=int, default=900, help="window length in seconds (default 900)")
    p.add_argument("--end", help="end of the window, RFC3339 or epoch seconds (default now)")
    p.add_argument("--reference", help="namespace of the run without a noisy tenant, to compare against")
    p.add_argument("--reference-end", help="end of the reference window (default: same as --end)")
    p.add_argument("--saturating", help="comma separated noisy tenants (default: detected by their load)")
    p.add_argument("--weights", default="", help="relative shares, e.g. pt2=3,default=2")
    p.add_argument("--min-service", type=float, default=0.95, help="served share that counts as served")
    p.add_argument(
        "--slo-seconds",
        type=float,
        default=DEFAULT_SLO_SECONDS,
        help="p99 limit of response and storage latency; must be the upper bound of a histogram bucket to be measurable",
    )
    p.add_argument("--lag-seconds", type=float, default=5.0, help="export queue limit, in seconds to clear")
    p.add_argument("--prometheus-url", default=os.environ.get("LOADTESTCTL_PROMETHEUS_URL", "http://localhost:9090"))
    p.add_argument("--output", help="write the Markdown report to this file")
    p.add_argument("--json", dest="json_path", help="also write the numbers as JSON to this file")
    return p


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    query = http_query(args.prometheus_url, os.environ.get("LOADTESTCTL_PROMETHEUS_USER"), os.environ.get("LOADTESTCTL_PROMETHEUS_PASSWORD"))
    try:
        markdown, data = build_report(query, args)
    except (InputError, RuntimeError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    if args.output:
        with open(args.output, "w", encoding="utf-8") as file:
            file.write(markdown)
    else:
        sys.stdout.write(markdown)
    if args.json_path:
        with open(args.json_path, "w", encoding="utf-8") as file:
            json.dump(data, file, indent=2, default=str)
    return 0


if __name__ == "__main__":
    sys.exit(main())
