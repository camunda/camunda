#!/usr/bin/env python3
# owner: @camunda/reliability-testing
"""Derive the per-tenant traffic of the physical tenants load test.

Used by `.github/workflows/camunda-physical-tenants-load-test.yml`. Everything is relative to the
allowance of a tenant, the share of the cluster capacity it is entitled to:

    allowance(i) = capacity * weight(i) / sum(weights)
    rate(i)      = allowance(i) * multiplier(i)

Tenants are the default tenant and pt1..ptN; the tenant count of the workflow includes the default tenant. Roles:
  * saturating (pt1..pt<S>): offers `saturating-capacity-ratio * capacity` alone. Pinned.
  * overridden (tenant-overrides): offers allowance * given multiplier. Pinned.
  * quiet (all others, including the default tenant): allowance * quiet-multiplier.

Pinned tenants are never rescaled. When `total-rate` is set, the quiet tenants are scaled by one
common factor so that all rates sum to it; it is an error when that cannot be reached.

The cluster capacity is the throughput at which the cluster saturates. The daily `max` stress test
(300 PI/s, `load-tests/docs/scripts/optimal.json`) is not that: it is a rate the cluster sustains with
CPU at about half of its limit. Until a calibration run measures the capacity, an estimate exists for
Elasticsearch only (see ESTIMATED_CAPACITY); for any other secondary storage it must be given.

Worker replicas scale linearly with the rate (WORKER_REPLICAS_PER_100_PI, at least one), so workers
never limit a tenant. 3 workers sustain about 800 PI/s, so the default ratio keeps some margin.

Environment (set by the workflow):
  TENANT_COUNT (including the default tenant), WORKER_REPLICAS_PER_100_PI, SATURATING_COUNT, SATURATING_CAPACITY_RATIO, QUIET_MULTIPLIER,
  TENANT_WEIGHTS ("pt2=3,default=2" or empty), TENANT_OVERRIDES ("pt3=4,default=0.1" or empty),
  TOTAL_RATE (0 disables), STORAGE (secondary storage type), CAPACITY_RATE (empty = the estimate), GITHUB_OUTPUT, GITHUB_STEP_SUMMARY.

Writes the step outputs `profiles` (the physical_tenant_profiles make variable, one rate:replicas entry per tenant),
`physical-tenant-count` (the N of pt1..ptN), `default-tenant-flags` (Helm flags of the default tenant) and `overload` (true when one tenant alone
offers at least the cluster capacity), and a Markdown table to the step summary.
"""

from __future__ import annotations

import math
import os
import sys
from dataclasses import dataclass

# PI/s per secondary storage at which the max scenario saturates the cluster.
# TODO: replace with the result of a calibration run. This is an extrapolation: the daily stress test
# runs 300 PI/s at about 50% of the CPU limit, and the exporters make up the rest of the load.
ESTIMATED_CAPACITY = {"elasticsearch": 600.0}


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


def capacity_of(storage: str, given: str) -> float:
    """The given capacity, or the estimate for this storage."""
    if given.strip():
        return float(given)
    if storage not in ESTIMATED_CAPACITY:
        raise InputError(
            f"cluster-capacity-rate is required for secondary storage '{storage}': no estimate exists "
            f"(only for {', '.join(ESTIMATED_CAPACITY)}). Calibrate it first."
        )
    return ESTIMATED_CAPACITY[storage]


def compute(
    tenant_count: int,
    saturating_count: int,
    capacity: float,
    saturating_ratio: float,
    quiet_multiplier: float,
    weights: str,
    overrides: str,
    total_rate: float,
) -> list[Tenant]:
    if tenant_count < 0 or saturating_count < 0:
        raise InputError("tenant-count and saturating-tenant-count must not be negative")
    if saturating_count > tenant_count:
        raise InputError(f"saturating-tenant-count ({saturating_count}) exceeds the number of physical tenants ({tenant_count})")
    if capacity < 1 or saturating_ratio <= 0 or quiet_multiplier <= 0 or total_rate < 0:
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
            role, pinned = "saturating", True
            rate = capacity * saturating_ratio
            multiplier = rate / allowance
        else:
            role, multiplier, pinned = "quiet", quiet_multiplier, False
            rate = allowance * multiplier
        tenants.append(Tenant(name, weight_of[name], allowance, role, multiplier, rate, pinned))

    if total_rate > 0:
        pinned_sum = sum(t.rate for t in tenants if t.pinned)
        free = [t for t in tenants if not t.pinned]
        free_sum = sum(t.rate for t in free)
        remaining = total_rate - pinned_sum
        if remaining <= 0 and free:
            raise InputError(
                f"pinned tenants (saturating or overridden) offer {pinned_sum:.1f} PI/s, which is not below "
                f"total-rate ({total_rate:.1f}). Overload runs cannot use total-rate."
            )
        if not free and not math.isclose(pinned_sum, total_rate, rel_tol=0.01):
            raise InputError(
                f"all tenants are pinned and offer {pinned_sum:.1f} PI/s, which differs from "
                f"total-rate ({total_rate:.1f})"
            )
        if free:
            factor = remaining / free_sum
            for t in free:
                t.rate *= factor
                t.multiplier *= factor
                t.scaled = True
    return tenants


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
    overload = any(rates[t.name] >= capacity for t in tenants)

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
        "Run type: **overload** (one tenant alone offers at least the cluster capacity)."
        if overload
        else "Run type: **within capacity** (no tenant alone offers the cluster capacity).",
    ]
    return profiles, default_flags, overload, "\n".join(lines)


def main() -> int:
    env = os.environ
    try:
        total_tenants = int(env["TENANT_COUNT"])
        if total_tenants < 1:
            raise InputError("tenant-count includes the default tenant, so it must be at least 1")
        physical_tenants = total_tenants - 1
        capacity = capacity_of(env["STORAGE"], env.get("CAPACITY_RATE", ""))
        tenants = compute(
            tenant_count=physical_tenants,
            saturating_count=int(env["SATURATING_COUNT"]),
            capacity=capacity,
            saturating_ratio=float(env["SATURATING_CAPACITY_RATIO"]),
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
        out.write(f"overload={str(overload).lower()}\n")
    with open(env["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        source = "given" if env.get("CAPACITY_RATE", "").strip() else "estimate, not yet calibrated"
        summary.write(f"## Physical tenants load\n\nCluster capacity: {capacity:g} PI/s ({source}).\n\n{table}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
