---
title: Flood Patterns
status: current
---

# Known CI Flood Patterns

These are observed archetypes from real incidents. Use them to inform reasoning — not as rigid
rules. Real floods may not match either pattern cleanly, or may combine elements of both.

## Pattern A: Merge Queue Flood

**What it looks like:**
- Many incidents open within a tight window (minutes, not hours)
- Incident names all contain `high-failure-rate-in-merge-queue`
- Summaries reference the same 3–5 GHA run URLs
- Jobs span multiple teams but share a compile failure or infra blip

**Why it happens:**
A single broken PR or infra issue enters the merge queue. Every job in every batch run fails.
The alerting fires one incident per job — so one root cause becomes 30 incidents.

**Key correlation signal:** shared GHA run IDs in alert payloads. If 25 of 30 incidents reference
the same 3 runs, it's almost certainly one cause.

**Caveat — run ID alone is not proof:** a shared run ID means the incidents came from the same CI
*invocation*, not that they share a cause. This heuristic is strongest here, in a merge-queue batch,
because a single broken commit really does poison every job in that batch. It is much weaker for a
scheduled/nightly workflow (see Pattern B) — one nightly run fans out into many independent
per-component jobs that all legitimately carry the same run URL by construction, whether or not
their failures are related. Before grouping on run-ID alone, compare the job name segment (e.g.
`integration-tests/zeebe-opensearch-exporter` vs `integration-tests/camunda-exporter`) — different
job names sharing one run are a stronger signal of *different* causes than the shared run ID is a
signal of the same cause. Confirm with the actual failing test/class name when it's available.

**Right response:**
1. Identify the dominant run IDs
2. Find the common failure in those runs (compile error → look at Java Checks / Maven; infra →
   look at Nexus, GitHub Actions, external upstreams)
3. If it's a PR conflict: pull the offending PR from the merge queue, fix, re-enqueue
4. Merge all related incidents into one, assign IC to the owning team
5. Broadcast to #top-monorepo-ci: "merge queue blocked, cause identified, IC assigned"

**Outlier signal:** an incident that doesn't reference the dominant runs, or has a different
failure message — investigate separately via `/ci-incident`.

---

## Pattern B: Nightly / Scheduled Flood

**What it looks like:**
- Incidents open spread over hours (e.g. 21:00 → 03:00), not a burst
- Incident names contain `nightly`, `scheduled`, `manualscheduled`, or `unsuccessful-job`
- Different workflows, different teams, no obvious shared run IDs
- Some jobs may have been failing for days
- A single nightly/scheduled run can trigger several incidents at once — one per failing job in
  its matrix — that all share the same run URL. That's normal fan-out, not evidence those jobs
  share a root cause (see the run-ID caveat under Pattern A)

**Why it happens (two sub-cases):**

*Sub-case B1 — Expired silence:*
A Grafana silence that was suppressing alerts expires. Pre-existing failures suddenly surface as
new incidents all at once. The jobs were already broken; the silence expiring is the trigger, not
a new failure.

Signal: summaries say jobs were failing before the flood window. Check whether a silence was
recently active on `merge-queue-high-failure-rate` or `unsuccessful-job` alert rules.

Response: re-create the silence if appropriate; route real failures to owning teams.

*Sub-case B2 — Shared infra problem:*
An upstream goes down (e.g. JBoss Maven repo, AWS, Snyk), affecting many independent nightly
jobs over hours as they happen to run.

Signal: summaries mention the same external URL or service; failures cluster around the same
error type (timeouts, 503s, auth failures).

Response: identify the shared upstream, open one incident for it, route nightly job incidents to
their owning teams.

**Right response (general):**
- Do NOT merge unrelated incidents just because they opened in the same window
- Route each to its owning team (check `TEST_OWNER` in the workflow or CODEOWNERS)
- Only merge if you can confirm they share a root cause
- If it's a silence expiration, the fix is the silence — not investigating each job

---

## When the Pattern Isn't Clear

If incidents span both merge-queue and nightly names, or if timing and summaries don't fit either
archetype cleanly, pull `incident_show` on a representative sample and look for:

- Common error strings across summaries
- Shared external dependencies (Maven repos, AWS regions, third-party APIs)
- Whether the flood started at an unusual hour (silence expiry tends to happen at fixed times)

State your uncertainty explicitly in the triage output rather than forcing a pattern.

---

## Correlation Pitfalls

Two failure modes have actually caused mis-grouping in practice — check for both before finalizing
any group of more than one incident:

- **Don't let one incident's narrative anchor its siblings.** If incident A's summary says
  "already tracked under issue #X," that claim covers incident A's own failure. It does not
  automatically extend to incident B just because B shares a run ID, workflow-name prefix, or
  timing with A. Confirm each incident's own failing job/test independently before folding it into
  A's group — a confident sibling is not evidence about an unrelated one.
- **A thin alert payload blocks grouping, not just outlier status.** If an alert's "unsuccessful
  test cases" came back empty or uncaptured, you don't know what actually failed. Don't assume it
  matches the group's dominant cause just because the surrounding metadata (run ID, name prefix)
  looks similar — that's exactly the situation where superficial signals get mistaken for a shared
  cause. Pull the real failing test/job (the GHA job log, e.g. `gh run view <run_id> --log-failed`)
  or escalate via `/ci-incident` before including it.

