"""Pure scoring of the node benchmark results."""

import re
from dataclasses import dataclass

# Idle fresh Spot nodes of the benchmark cluster measure about 2600 events/s (fast) or about 980 (slow, 2.7x
# slower per core) with `sysbench cpu --threads=1`. The threshold sits between the two classes.
DEFAULT_MIN_EVENTS_PER_SECOND = 1500.0

EVENTS_PATTERN = re.compile(r"events per second:\s*([0-9.]+)")


@dataclass(frozen=True)
class NodeResult:
    node: str
    events_per_second: float | None

    def is_slow(self, min_events_per_second: float) -> bool:
        return self.events_per_second is not None and self.events_per_second < min_events_per_second


def parse_events_per_second(output: str) -> float | None:
    """The `events per second` value of a `sysbench cpu` run, or None if the output has none."""
    match = EVENTS_PATTERN.search(output)
    return float(match.group(1)) if match else None


def slow_nodes(results: list[NodeResult], min_events_per_second: float) -> list[str]:
    return sorted(result.node for result in results if result.is_slow(min_events_per_second))


def format_table(results: list[NodeResult], min_events_per_second: float) -> str:
    lines = ["node | events/s | class"]
    for result in sorted(results, key=lambda r: r.node):
        if result.events_per_second is None:
            lines.append(f"{result.node} | n/a | unknown")
        else:
            label = "slow" if result.is_slow(min_events_per_second) else "ok"
            lines.append(f"{result.node} | {result.events_per_second:.0f} | {label}")
    return "\n".join(lines)
