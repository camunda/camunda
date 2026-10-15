#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/coverage.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""The coverage block a fix PR carries, listing the fingerprints it claims.

Part of alwaysgreen_core, the code every AlwaysGreen instance shares verbatim.
Do not edit a vendored copy of this file: edit it in
camunda/c8-cross-component-e2e-tests and re-vendor. `vendored-copy-drift.yml`
reports a copy that stops matching the revision its vendor.json pins.
"""


from __future__ import annotations


COVERAGE_BEGIN = "<!-- alwaysgreen-fixed"


COVERAGE_END = "-->"


def parse_coverage_block(body: str | None) -> set[str]:
    """Extract fingerprints a fix PR claims to cover.

    Format, one per line inside an HTML comment so it renders invisibly:

        <!-- alwaysgreen-fixed
        fp=1a2b3c4d
        fp=5e6f7a8b
        -->
    """
    text = body or ""
    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return set()
    end = text.find(COVERAGE_END, start)
    block = text[start : end if end != -1 else len(text)]
    out: set[str] = set()
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("fp="):
            value = line[3:].strip()
            if value:
                out.add(value)
    return out


def render_coverage_block(fingerprints: set[str]) -> str:
    """Render a coverage block, sorted so repeated updates produce a stable diff."""
    lines = [COVERAGE_BEGIN] + [f"fp={fp}" for fp in sorted(fingerprints)] + [COVERAGE_END]
    return "\n".join(lines)


def merge_coverage_block(body: str | None, new_fingerprints: set[str]) -> str:
    """Return the body with the coverage block replaced by the union of fingerprints.

    Existing entries are never dropped: an accumulating PR must keep claiming every
    test it already fixed, or a later triage run would re-dispatch them.
    """
    text = (body or "").rstrip()
    existing = parse_coverage_block(text)
    merged = existing | set(new_fingerprints)

    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return f"{text}\n\n{render_coverage_block(merged)}\n"

    end = text.find(COVERAGE_END, start)
    tail = text[end + len(COVERAGE_END) :] if end != -1 else ""
    return f"{text[:start]}{render_coverage_block(merged)}{tail}".rstrip() + "\n"
