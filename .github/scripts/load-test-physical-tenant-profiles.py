#!/usr/bin/env python3
# owner: @camunda/reliability-testing
"""Derive the per-tenant traffic of the physical tenants load test.

Used by `.github/workflows/camunda-physical-tenants-load-test.yml`. Everything is relative to the
allowance of a tenant, the share of the cluster capacity it is entitled to:

    allowance(i) = capacity * weight(i) / sum(weights)
    rate(i)      = allowance(i) * multiplier(i)

Tenants are the default tenant and pt1..ptN; the tenant count of the workflow includes the default tenant. Roles:
  * saturating (pt1..pt<S>): offers allowance * saturating-multiplier (2 to 5 in the user stories). Pinned.
  * overridden (tenant-overrides): offers allowance * given multiplier. Pinned.
  * quiet (all others, including the default tenant): allowance * quiet-multiplier.

Pinned tenants are never rescaled. When `total-rate` is set, the quiet tenants are scaled by one
common factor so that all rates sum to it; it is an error when that cannot be reached.

The cluster capacity is the throughput at which the cluster saturates. The daily `max` stress test
(300 PI/s, `load-tests/docs/scripts/optimal.json`) is not that: it is a rate the cluster sustains with
CPU at about half of its limit. Until a calibration run measures the capacity, an estimate exists for
Elasticsearch, OpenSearch and PostgreSQL (ESTIMATED_GRPC_CAPACITY); for any other secondary storage it
must be given.

Worker replicas scale linearly with the rate (WORKER_REPLICAS_PER_100_PI, at least one), so workers
never limit a tenant. The daily stress tests run 3 workers (the chart default) for 650 PI/s without secondary
storage, over gRPC and REST, and for 300 PI/s with Elasticsearch, so the default ratio of 0.4 per 100 PI/s
keeps margin.

Environment (set by the workflow):
  TENANT_COUNT (including the default tenant), SATURATING_COUNT, SATURATING_MULTIPLIER,
  QUIET_MULTIPLIER, WORKER_REPLICAS_PER_100_PI,
  TENANT_WEIGHTS ("pt2=3,default=2" or empty), TENANT_OVERRIDES ("pt3=4,default=0.1" or empty),
  TOTAL_RATE (0 disables), STORAGE (secondary storage type), PREFER_REST (true is REST, false
  gRPC), CAPACITY_RATE (empty = the estimate for that storage and API), GITHUB_OUTPUT, GITHUB_STEP_SUMMARY.

Writes the step outputs `profiles` (the physical_tenant_profiles make variable, one rate:replicas
entry per tenant), `physical-tenant-count` (the N of pt1..ptN), `default-tenant-flags` (Helm flags of
the default tenant), and a Markdown table, including the overload or within-capacity run type, to
the step summary.
"""

from __future__ import annotations

import math
import os
import sys
from dataclasses import dataclass

# PI/s per secondary storage used as the gRPC capacity on the default 4 CPU sizing. A 10 tenant gRPC run
# reached 600 to 650 PI/s with a lot of backpressure, so this is closer to the saturation throughput than
# to a rate sustained without backpressure. TODO: replace with the result of a calibration run.
# OpenSearch and PostgreSQL are assumed to match Elasticsearch.
ESTIMATED_GRPC_CAPACITY = {"elasticsearch": 600.0, "opensearch": 600.0, "postgresql": 600.0}
# REST spends a quarter to a third of the CPU in Tomcat and Spring request handling (profile of a
# 10 tenant run), so the same cluster sustains less over REST. Assumed 35% less, to be calibrated.
REST_CAPACITY_FACTOR = 0.65


class InputError(ValueError):
    """The workflow inputs cannot describe a valid run."""


@dataclass
class Tenant:
    name: str
    weight: float
    allowance: float
    role: str
    multiplier: float
    rate: float
    pinned: bool
    scaled: bool = False


def parse_map(raw: str, tenants: list[str], what: str) -> dict[str, float]:
    result: dict[str, float] = {}
    for entry in filter(None, (part.strip() for part in raw.split(","))):
        name, sep, value = entry.partition("=")
        name = name.strip()
        if not sep or name not in tenants:
            raise InputError(f"{what}: unknown tenant or bad entry '{entry}' (tenants: {', '.join(tenants)})")
        if name in result:
            raise InputError(f"{what}: tenant '{name}' is listed twice")
        try:
            number = float(value)
        except ValueError:
            raise InputError(f"{what}: '{value}' is not a number in '{entry}'") from None
        if not math.isfinite(number) or number <= 0:
            raise InputError(f"{what}: value for '{name}' must be greater than 0")
        result[name] = number
    return result


def capacity_of(storage: str, given: str, rest: bool = True) -> float:
    """The given capacity, or the estimate for this storage and API."""
    if given.strip():
        return float(given)
    if storage not in ESTIMATED_GRPC_CAPACITY:
        raise InputError(
            f"cluster-capacity-rate is required for secondary storage '{storage}': no estimate exists "
            f"(only for {', '.join(ESTIMATED_GRPC_CAPACITY)}). Calibrate it first."
        )
    grpc = ESTIMATED_GRPC_CAPACITY[storage]
    return grpc * REST_CAPACITY_FACTOR if rest else grpc


def compute(
    tenant_count: int,
    saturating_count: int,
    capacity: float,
    saturating_multiplier: float,
    quiet_multiplier: float,
    weights: str,
    overrides: str,
    total_rate: float,
) -> list[Tenant]:
    if tenant_count < 0 or saturating_count < 0:
        raise InputError("tenant-count and saturating-tenant-count must not be negative")
    if saturating_count > tenant_count:
        raise InputError(f"saturating-tenant-count ({saturating_count}) exceeds the number of physical tenants ({tenant_count}, which is tenant-count minus the default tenant)")
    if not all(math.isfinite(x) for x in (capacity, saturating_multiplier, quiet_multiplier, total_rate)):
        raise InputError("cluster-capacity-rate, the multipliers and total-rate must be finite numbers")
    if capacity < 1 or saturating_multiplier <= 0 or quiet_multiplier <= 0 or total_rate < 0:
        raise InputError(
            "cluster-capacity-rate must be at least 1, the multipliers above 0 and total-rate not negative"
        )

    names = ["default"] + [f"pt{i}" for i in range(1, tenant_count + 1)]
    weight_of = {name: 1.0 for name in names}
    weight_of.update(parse_map(weights, names, "tenant-weights"))
    override_of = parse_map(overrides, names, "tenant-overrides")
    weight_sum = sum(weight_of.values())

    tenants: list[Tenant] = []
    for name in names:
        allowance = capacity * weight_of[name] / weight_sum
        if name in override_of:
            role, multiplier, pinned = "overridden", override_of[name], True
            rate = allowance * multiplier
        elif name != "default" and int(name[2:]) <= saturating_count:
            role, multiplier, pinned = "saturating", saturating_multiplier, True
            rate = allowance * multiplier
        else:
            role, multiplier, pinned = "quiet", quiet_multiplier, False
            rate = allowance * multiplier
        tenants.append(Tenant(name, weight_of[name], allowance, role, multiplier, rate, pinned))

    if total_rate > 0:
        if total_rate != int(total_rate):
            raise InputError(f"total-rate ({total_rate:g}) must be a whole number: the starter takes whole PI/s")
        pinned_sum = sum(whole_rate(t.rate) for t in tenants if t.pinned)
        free = [t for t in tenants if not t.pinned]
        remaining = int(total_rate) - pinned_sum
        if not free and pinned_sum != int(total_rate):
            raise InputError(
                f"all tenants are pinned and offer {pinned_sum} PI/s, which differs from "
                f"total-rate ({total_rate:g})"
            )
        if free and remaining <= 0:
            raise InputError(
                f"pinned tenants (saturating or overridden) offer {pinned_sum} PI/s, which is not below "
                f"total-rate ({total_rate:g}). Raise total-rate or lower the pinned load."
            )
        if free and remaining < len(free):
            raise InputError(
                f"total-rate ({total_rate:g}) leaves {remaining} PI/s for {len(free)} unpinned tenants, "
                "which need at least 1 PI/s each. Raise total-rate."
            )
        if free:
            for t, rate in zip(free, split_whole(remaining, [t.rate for t in free])):
                t.rate = float(rate)
                t.multiplier = t.rate / t.allowance
                t.scaled = True
    return tenants


def split_whole(total: int, shares: list[float]) -> list[int]:
    """Split `total` whole PI/s in proportion to `shares`, each at least 1, adding up to exactly `total`."""
    exact = [total * share / sum(shares) for share in shares]
    rates = [max(1, math.floor(value)) for value in exact]
    by_remainder = sorted(range(len(shares)), key=lambda i: exact[i] - math.floor(exact[i]), reverse=True)
    step = 0
    while sum(rates) < total:
        rates[by_remainder[step % len(rates)]] += 1
        step += 1
    while sum(rates) > total:  # the minimum of 1 can overshoot: take it back from the largest
        rates[rates.index(max(rates))] -= 1
    return rates


def whole_rate(rate: float) -> int:
    """The starter takes whole process instances per second, at least one."""
    return max(1, math.ceil(rate))


def workers(rate: int, per_100_pi: float) -> int:
    return max(1, math.ceil(rate * per_100_pi / 100))


def render(tenants: list[Tenant], capacity: float, per_100_pi: float) -> tuple[str, str, bool, str]:
    rates = {t.name: whole_rate(t.rate) for t in tenants}
    profiles = " ".join(
        f"{rates[t.name]}:{workers(rates[t.name], per_100_pi)}" for t in tenants if t.name != "default"
    )
    default_flags = (
        f"--set load-tester.starter.rate={rates['default']} "
        f"--set load-tester.workers.worker.replicas={workers(rates['default'], per_100_pi)}"
    )
    total = sum(rates.values())
    overload = total >= capacity

    lines = [
        "| Tenant | Role | Weight | Multiplier | Rate (PI/s) | Offered load (% of capacity) | Workers | Derivation |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for t in tenants:
        lines.append(
            f"| {t.name} | {t.role} | {t.weight:g} | {t.multiplier:.2f} | {rates[t.name]} "
            f"| {100 * rates[t.name] / capacity:.0f}% | {workers(rates[t.name], per_100_pi)} | {'scaled' if t.scaled else 'pinned' if t.pinned else 'derived'} |"
        )
    lines += [
        "",
        f"Total offered load: **{total} PI/s = {100 * total / capacity:.0f}% of the cluster capacity "
        f"({capacity:g} PI/s)**.",
        "Run type: **overload** (the tenants together offer at least the cluster capacity)."
        if overload
        else "Run type: **within capacity** (the tenants together offer less than the cluster capacity).",
    ]
    return profiles, default_flags, overload, "\n".join(lines)


def main() -> int:
    env = os.environ
    try:
        total_tenants = int(env["TENANT_COUNT"])
        if total_tenants < 1:
            raise InputError("tenant-count includes the default tenant, so it must be at least 1")
        physical_tenants = total_tenants - 1
        prefer_rest = env.get("PREFER_REST", "true") == "true"
        capacity = capacity_of(env["STORAGE"], env.get("CAPACITY_RATE", ""), prefer_rest)
        tenants = compute(
            tenant_count=physical_tenants,
            saturating_count=int(env["SATURATING_COUNT"]),
            capacity=capacity,
            saturating_multiplier=float(env["SATURATING_MULTIPLIER"]),
            quiet_multiplier=float(env["QUIET_MULTIPLIER"]),
            weights=env.get("TENANT_WEIGHTS", ""),
            overrides=env.get("TENANT_OVERRIDES", ""),
            total_rate=float(env.get("TOTAL_RATE") or 0),
        )
    except (InputError, ValueError) as error:
        print(f"::error::{error}")
        return 1

    profiles, default_flags, overload, table = render(tenants, capacity, float(env["WORKER_REPLICAS_PER_100_PI"]))
    with open(env["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        out.write(f"physical-tenant-count={physical_tenants}\n")
        out.write(f"profiles={profiles}\n")
        out.write(f"default-tenant-flags={default_flags}\n")
    with open(env["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        api = "REST" if prefer_rest else "gRPC"
        source = "given" if env.get("CAPACITY_RATE", "").strip() else f"{api} estimate, not yet calibrated"
        summary.write(f"## Physical tenants load\n\nCluster capacity: {capacity:g} PI/s ({source}).\n\n{table}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
