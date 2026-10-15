#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/__init__.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""Code every AlwaysGreen instance shares, held in one place and vendored out.

Several repositories run their own AlwaysGreen triage — camunda/camunda,
camunda/connectors and camunda/camunda-operator among them — and each needs the
same answers to the same questions: what does `gh` say, what is in this run's
artifacts, what is the stable identity of this failing spec, and which
fingerprints does an open fix PR already claim.

Those parts were byte-identical in all three copies, which is why they are here
and why moving them was safe: nothing was reconciled, only relocated.

What is deliberately NOT here is everything that differs per instance — the
job-name-to-surface table, the dispatchable set, the base-ref normalisation, the
repositories a fix may land in, and the dedupe label namespace. Those are not
incidental differences to be smoothed away: the label namespace in particular
MUST stay per-instance, because several instances open fix PRs into the same
repositories and a shared key would make them suppress each other's dispatches.

Distribution is by vendoring, not by a package index. Each consumer keeps a copy
under its own .github/scripts/alwaysgreen/ with a vendor.json pinning the
revision it came from. That keeps triage's own code arriving with the repository
checkout, so classification needs no credential and cannot be stopped by a failed
fetch — the failure mode that matters most on the path whose whole job is
noticing failures. See camunda/team-test-automation#163.
"""
