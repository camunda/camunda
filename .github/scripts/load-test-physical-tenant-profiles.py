#!/usr/bin/env python3
# owner: @camunda/reliability-testing
"""Derive the per-tenant traffic of the physical tenants load test.

Used by `.github/workflows/camunda-physical-tenants-load-test.yml`. Everything is relative to the
allowance of a tenant, the share of the cluster capacity it is entitled to:

    allowance(i) = capacity * weight(i) / sum(weights)
    rate(i)      = allowance(i) * multiplier(i)

Tenants are the default tenant and pt1..ptN. Roles:
  * saturating (pt1..pt<S>): offers `saturating-capacity-ratio * capacity` alone. Pinned.
  * overridden (tenant-overrides): offers allowance * given multiplier. Pinned.
  * quiet (all others, including the default tenant): allowance * quiet-multiplier.

Pinned tenants are never rescaled. When `total-rate` is set, the quiet tenants are scaled by one
common factor so that all rates sum to it; it is an error when that cannot be reached.

The cluster capacity is the throughput the daily `max` stress test validates every day, read from
`load-tests/docs/scripts/optimal.json`. It exists for Elasticsearch only; for any other secondary
storage the capacity must be given explicitly, as no tested maximum exists there yet.

Environment (set by the workflow):
  TENANT_COUNT, SATURATING_COUNT, SATURATING_CAPACITY_RATIO, QUIET_MULTIPLIER,
  TENANT_WEIGHTS ("pt2=3,default=2" or empty), TENANT_OVERRIDES ("pt3=4,default=0.1" or empty),
  TOTAL_RATE (0 disables), STORAGE (secondary storage type), CAPACITY_RATE (empty = the tested
  maximum), GITHUB_OUTPUT, GITHUB_STEP_SUMMARY.

Writes the step outputs `profiles` (the physical_tenant_profiles make variable, one rate per tenant),
`default-tenant-flags` (Helm flag of the default tenant) and `overload` (true when one tenant alone
offers at least the cluster capacity), and a Markdown table to the step summary.
"""

from __future__ import annotations

import json
import math
import os
import pathlib
import sys
from dataclasses import dataclass

OPTIMAL_JSON = pathlib.Path(__file__).resolve().parents[2] / "load-tests/docs/scripts/optimal.json"
# Storages for which the daily stress test validates the throughput in optimal.json.
TESTED_STORAGES = ("elasticsearch",)


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


def tested_capacity(storage: str, given: str, optimal_json: pathlib.Path = OPTIMAL_JSON) -> float:
    """The given capacity, or the throughput the daily stress test validates for this storage."""
    if given.strip():
        return float(given)
    if storage not in TESTED_STORAGES:
        raise InputError(
            f"cluster-capacity-rate is required for secondary storage '{storage}': "
            f"no tested maximum exists (only for {', '.join(TESTED_STORAGES)}). Calibrate it first."
        )
    with open(optimal_json, encoding="utf-8") as file:
        return float(json.load(file)["metrics"]["throughput-per-second"]["expected"])


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
        raise InputError(f"saturating-tenant-count ({saturating_count}) exceeds tenant-count ({tenant_count})")
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


def render(tenants: list[Tenant], capacity: float) -> tuple[str, str, bool, str]:
    rates = {t.name: whole_rate(t.rate) for t in tenants}
    profiles = " ".join(str(rates[t.name]) for t in tenants if t.name != "default")
    default_flags = f"--set load-tester.starter.rate={rates['default']}"
    total = sum(rates.values())
    overload = any(rates[t.name] >= capacity for t in tenants)

    lines = [
        "| Tenant | Role | Weight | Multiplier | Rate (PI/s) | Offered load (% of capacity) | Derivation |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for t in tenants:
        lines.append(
            f"| {t.name} | {t.role} | {t.weight:g} | {t.multiplier:.2f} | {rates[t.name]} "
            f"| {100 * rates[t.name] / capacity:.0f}% | {'scaled' if t.scaled else 'pinned' if t.pinned else 'derived'} |"
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
        capacity = tested_capacity(env["STORAGE"], env.get("CAPACITY_RATE", ""))
        tenants = compute(
            tenant_count=int(env["TENANT_COUNT"]),
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

    profiles, default_flags, overload, table = render(tenants, capacity)
    with open(env["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        out.write(f"profiles={profiles}\n")
        out.write(f"default-tenant-flags={default_flags}\n")
        out.write(f"overload={str(overload).lower()}\n")
    with open(env["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        source = "given" if env.get("CAPACITY_RATE", "").strip() else "tested maximum of the daily stress test"
        summary.write(f"## Physical tenants load\n\nCluster capacity: {capacity:g} PI/s ({source}).\n\n{table}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
