#!/usr/bin/env python3
"""Render the Slack message AlwaysGreen posts about its own triage decisions.

Pure functions plus a thin `main`, like `classify` and `plan`: the workflow does the
HTTP, everything that decides what the message *says* lives here so it is unit-tested
without a token or a channel.

The reader is the test-automation medic on shift, scrolling a channel on a phone. They
need three things per failing area, in one line: what failed, how much of it, and why
no agent is working on it. The message this replaced gave them the third one only, as
the internal reason code — a run reporting `open-pr-covers-all-specs x2` named neither
the surface that failed nor the PR that covers it, so the only way to learn either was
to open the triage run and read the plan JSON.

So: one headline, one line per area, every reason spelled out as a sentence, and the
PR or issue that accounts for the failure linked by number. The reason codes stay in
the job summary and the plan artifact, which is where a code belongs.

Usage:
    notify.py --report /tmp/alwaysgreen-plan.json --base-ref main \\
              --triage-run-url URL --state dispatched

Writes the message text to stdout.
"""

from __future__ import annotations

import argparse
import json
import sys

ASK_CHANNEL_URL = "https://camunda.slack.com/archives/C0AQ378VBEV"
ASK_CHANNEL_NAME = "#ask-alwaysgreen"

#: What actually happened to the planned dispatches. The plan alone cannot say:
#: `discover.py` computes it with no knowledge of the run mode, the workflow withholds
#: dispatch in dry-run, and the dispatch step can fail after planning.
STATE_DISPATCHED = "dispatched"
STATE_DRY_RUN = "dry-run"
STATE_FAILED = "failed"
STATE_TRIAGE_FAILED = "triage-failed"
STATES = (STATE_DISPATCHED, STATE_DRY_RUN, STATE_FAILED, STATE_TRIAGE_FAILED)

#: Surface ids are dispatch-key material and appear in labels and fingerprints, so they
#: are not renamed. They are translated here instead: a medic reading the channel knows
#: what "SaaS E2E" is without knowing that `saas-smoke-e2e` also covers the full suite.
#: The table is the union across every repo running AlwaysGreen, so this module stays
#: copy-identical between them — five diverging renderers is how the channel ends up
#: describing the same decision five ways.
SURFACE_LABELS = {
    "sm-smoke-e2e": "Self-Managed E2E",
    "sm-helm-e2e": "Self-Managed E2E",
    "saas-smoke-e2e": "SaaS E2E",
    "saas-downstream-e2e": "SaaS E2E",
    "saas-ci": "SaaS test-run setup",
    "saas-setup": "SaaS test-run setup",
    "saas-provisioning": "SaaS test-run setup",
    "saas-infra": "SaaS test infrastructure",
    "sm-helm-setup": "Self-Managed test-run setup",
    "sm-helm-report-merge": "Self-Managed report merge",
    "helm-install": "Helm install",
    "helm-cleanup": "Helm cleanup",
    "build": "Build",
    "ci-infra": "CI infrastructure",
    "sm-owned-unrecognised-job": "an unrecognised job in a stage we own",
    "owned-stage-unrecognised-job": "an unrecognised job in a stage we own",
    "out-of-scope": "a stage we do not own",
}

#: One sentence per suppression reason, in the medic's terms. `{refs}` is filled with
#: the PRs or issues that account for the failure and dropped when there are none, so
#: the sentence still reads correctly when the lookup produced no reference.
#:
#: Keys are the `SUPPRESSED_*` values in `plan.py`. An unknown reason falls back to the
#: raw code rather than being hidden: a new suppression path must be visible in the
#: channel from the day it first fires, not from the day someone remembers to add it
#: here.
REASON_SENTENCES = {
    "open-pr-covers-all-specs": "already covered by an open fix PR{refs}",
    "spec-path-claimed-by-open-pr": "an open fix PR already touches these test files{refs}",
    "open-fix-pr-for-surface": "a fix PR for this area is open and still unmerged{refs}",
    "agent-already-running": "a fix agent is already working on this area",
    "tracked-by-open-product-bug": "tracked as a known product bug{refs}",
    "no-fix-verdict-within-cooldown": (
        "an agent looked at this recently and could not fix it safely, "
        "so it is on cooldown"
    ),
    "fixed-upstream-after-run-started": (
        "already fixed in the e2e repo — this run used the older published suite"
    ),
    "surface-not-in-increment": "the fix agent does not handle this area yet",
    "surface-not-actionable": "the fix agent does not handle this area yet",
    "base-ref-not-supported-by-fix-agent": "the fix agent does not run on this branch",
    "event-not-dispatchable": "this trigger is not one the fix agent runs on",
    "all-specs-already-accounted-for": "every failing test is already accounted for{refs}",
    "no-failing-specs-extracted": "no failing test could be read from the report",
    "per-run-cap-reached": "the per-run agent limit for this run was already reached",
    "daily-cap-reached": "the daily agent limit was already reached",
}


#: Reasons whose `detail` from `plan.py` is already written for a human — currently
#: only the path-claim one, which reads `tests/x.spec.ts (#123)`. Every other detail is
#: a fingerprint list or a comma-joined set of reason codes, which is summary material.
HUMAN_DETAIL_REASONS = frozenset({"spec-path-claimed-by-open-pr"})


def escape(text: str) -> str:
    """Neutralise the characters that let a repo-controlled string escape its context.

    Branch, job and test names reach this message, so they are escaped rather than
    trusted. Slack specifies `&`, `<` and `>`; the ampersand must be replaced first or
    it would double-escape the other two.

    Backticks are STRIPPED rather than escaped, because Slack mrkdwn offers no escape
    for them and every value here is rendered inside a code span. A name containing one
    would close the span early and re-enable formatting for the rest of the line — a git
    ref and a Playwright test title may both legally contain a backtick.
    """
    return (
        (text or "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("`", "")
    )


def surface_label(surface: str) -> str:
    return SURFACE_LABELS.get(surface, surface or "unknown area")


def pr_link(ref: str) -> str:
    """One reference as a Slack link labelled `repo#123`.

    `discover.py` emits PR references as `owner/repo#number` and product-bug references
    as the issue URL the search returned. Both are shortened to `repo#number` for the
    bullet: unambiguous enough in a channel where the owner is always `camunda`, and
    short enough that three of them still fit on one line.
    """
    text = (ref or "").strip()
    if text.startswith("http"):
        parts = [p for p in text.split("/") if p]
        if len(parts) >= 4 and parts[-2] in ("pull", "issues"):
            return f"<{text}|{escape(parts[-3])}#{escape(parts[-1])}>"
        return f"<{text}|{escape(parts[-1] if parts else text)}>"
    if "#" in text:
        repo, _, number = text.partition("#")
        short = repo.split("/")[-1]
        return f"<https://github.com/{repo}/pull/{number}|{escape(short)}#{escape(number)}>"
    return escape(text)


def refs_clause(refs: list[str], limit: int = 3) -> str:
    """`: a#1, b#2 (+1 more)`, or empty. Prefixed so it can be dropped cleanly."""
    kept = [r for r in refs if r][:limit]
    if not kept:
        return ""
    extra = len([r for r in refs if r]) - len(kept)
    tail = f" (+{extra} more)" if extra > 0 else ""
    return ": " + ", ".join(pr_link(r) for r in kept) + tail


def amount_phrase(entry: dict, spec_count: int) -> str:
    """How much failed, in words.

    A job-level candidate (a Helm install, a job that died before Playwright ran) has
    no specs at all, and reporting it as "0 failing tests" reads as "nothing failed".
    """
    jobs = 1 + len([j for j in (entry.get("also_failing_jobs") or []) if j])
    if entry.get("job_level") or spec_count == 0:
        if jobs > 1:
            return f"{jobs} failing jobs, no test results"
        return "the job failed before any test ran"
    return f"{spec_count} failing test{'s' if spec_count != 1 else ''}"


def references_for(entry: dict, references: dict) -> list[str]:
    """PRs or issues that account for one suppressed candidate.

    Resolved here rather than in `plan.py` so the planner keeps deciding in
    fingerprints only. `discover.py` emits the two indexes it already built while
    deciding — fingerprint to claiming PR, dispatch key to the PR holding it — and the
    reason picks which one answers "why is nobody working on this?".
    """
    reason = entry.get("reason") or ""
    if reason in ("open-fix-pr-for-surface", "spec-path-claimed-by-open-pr"):
        by_key = references.get("keys") or {}
        return list(by_key.get(entry.get("dispatch_key") or "") or [])
    by_fp = {
        **(references.get("product_bugs") or {}),
        **(references.get("covered_by") or {}),
    }
    out: list[str] = []
    for fp in entry.get("fingerprints") or []:
        ref = by_fp.get(fp)
        if ref and ref not in out:
            out.append(ref)
    return out


def area_line(entry: dict, references: dict) -> str:
    """One bullet: what failed, how much, and why no agent is on it."""
    reason = entry.get("reason") or "unknown"
    sentence = REASON_SENTENCES.get(reason)
    refs = refs_clause(references_for(entry, references))
    if not refs and reason in HUMAN_DETAIL_REASONS and (entry.get("detail") or "").strip():
        refs = ": " + escape(entry["detail"].strip())
    if sentence is None:
        # No translation yet. Name the code so the channel still shows the new path.
        why = f"held back ({escape(reason)}){refs}"
    else:
        why = sentence.format(refs=refs)
    amount = amount_phrase(entry, int(entry.get("spec_count") or 0))
    return f"• *{escape(surface_label(entry.get('surface') or ''))}* — {amount}, {why}"


def dispatch_line(entry: dict) -> str:
    """One bullet per dispatched agent, naming the first test it was given."""
    specs = entry.get("test_specs") or []
    amount = amount_phrase(entry, len(specs))
    line = f"• *{escape(surface_label(entry.get('surface') or ''))}* — {amount}"
    if specs:
        first = specs[0]
        more = f" (+{len(specs) - 1} more)" if len(specs) > 1 else ""
        line += (
            f": `{escape(first.get('file') or '')}` › "
            f"{escape(first.get('test_name') or '')}{more}"
        )
    elif entry.get("job_name"):
        line += f": `{escape(entry['job_name'])}`"
    return line


def noise_line(noise: list[dict]) -> str:
    """Failing jobs the classifier deliberately does not hand over.

    Worth a line only when nothing else is reported: otherwise it is a run where
    AlwaysGreen decided something, and the undiagnosable jobs are detail.
    """
    verdicts: dict[str, int] = {}
    for item in noise:
        verdict = (item.get("verdict") or "unknown").split(":")[0]
        verdicts[verdict] = verdicts.get(verdict, 0) + 1
    detail = ", ".join(f"{escape(v)} x{n}" for v, n in sorted(verdicts.items()))
    jobs = len(noise)
    return (
        f"• {jobs} failing job{'s' if jobs != 1 else ''} could not be diagnosed "
        f"automatically, so nothing was dispatched ({detail})."
    )


def footer(payload: dict, triage_run_url: str) -> str:
    links = []
    if payload.get("run_url"):
        links.append(f"<{payload['run_url']}|Failing run ↗>")
    if triage_run_url:
        links.append(f"<{triage_run_url}|Triage run ↗>")
    links.append(f"<{ASK_CHANNEL_URL}|{ASK_CHANNEL_NAME}>")
    return " · ".join(links)


def text(
    payload: dict,
    base_ref: str,
    triage_run_url: str,
    state: str = STATE_DISPATCHED,
) -> str:
    on = f"on `{escape(base_ref)}`"
    dispatches = payload.get("dispatches") or []
    suppressed = payload.get("suppressed") or []
    noise = payload.get("noise") or []
    lines: list[str] = []

    if state == STATE_TRIAGE_FAILED:
        return "\n".join(
            [
                f":warning: *AlwaysGreen triage failed* {on} — no fix agent was "
                f"dispatched, so this failure is unattended.",
                footer(payload, triage_run_url),
            ]
        )

    if dispatches:
        n = len(dispatches)
        agents = f"{n} fix agent{'s' if n != 1 else ''}"
        # The verb comes from what happened, not from the plan: announcing an agent
        # that never ran reads as "someone is on it" while nothing is.
        if state == STATE_DRY_RUN:
            lines.append(
                f":mag: *AlwaysGreen would dispatch {agents}* {on} — held back "
                f"because fix-agent dispatch is switched off."
            )
        elif state == STATE_FAILED:
            lines.append(
                f":warning: *AlwaysGreen planned {agents} but failed to start "
                f"them* {on} — nobody is working on this yet."
            )
        else:
            lines.append(f":robot_face: *AlwaysGreen dispatched {agents}* {on}")
        lines.extend(dispatch_line(d) for d in dispatches)
    else:
        lines.append(f":information_source: *AlwaysGreen: no fix agent needed* {on}")

    references = payload.get("references") or {}
    lines.extend(area_line(s, references) for s in suppressed)

    if noise and not dispatches and not suppressed:
        lines.append(noise_line(noise))
    if not dispatches and not suppressed and not noise:
        lines.append("• No failing job matched an area AlwaysGreen owns.")

    lines.append(footer(payload, triage_run_url))
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", required=True)
    ap.add_argument("--base-ref", default="")
    ap.add_argument("--triage-run-url", default="")
    ap.add_argument(
        "--state",
        choices=STATES,
        default=STATE_DISPATCHED,
        help="what happened to the planned dispatches; the plan cannot say",
    )
    args = ap.parse_args()
    try:
        with open(args.report) as fh:
            payload = json.load(fh)
    except (OSError, json.JSONDecodeError):
        # The message must survive a missing or truncated plan: the failure it reports
        # is real either way, and a crash here would drop the only notification.
        payload = {}
    print(text(payload, args.base_ref, args.triage_run_url, args.state))
    return 0


if __name__ == "__main__":
    sys.exit(main())
