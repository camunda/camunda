# Monorepo DevOps Collaboration Guidelines

These are our baseline expectations.

## Core Principles

* **You own your domain workflows** – Monorepo DevOps supports, but doesn't take over domain-specific CI/CD logic.
* **Accountability follows ownership** – The requesting team owns results, failures, and communication.
* **No surprises** – Involve Monorepo DevOps before you promise timelines, blocking behavior, or release changes.
* **Release changes require alignment** – Any change to the Monorepo release train must be explicitly discussed and written down.
* **Clarity first** – Vague or underspecified requests will pause until scope, ownership, and impact are clear.

💰 **Golden rule: Heads-up early → decide together → own independently.**

👉 Heads-up early, decide together, own independently.

## When to Involve Monorepo DevOps

### ✅ Must Involve (Required)

* Changes to release train behavior or gates (RCs, final artifacts, quality checks)
* New blocking in CI steps
* Changes to shared infra (runners, artifact storage, shared workflows/templates)
* External integrations that affect releases
* Versioning/tagging/release orchestration changes

### ⚠️ Should Involve (Recommended)

* New workflows that follow or extend existing monorepo patterns
* Unclear placement (RC vs final artifact vs post-release)
* Unclear blocking vs non-blocking behavior
* Performance-sensitive changes
* Cross-team dependency workflows

✋ **When in Doubt: Ask early in #ask-monorepo-devops. A 5-minute chat can save hours.**

## Heads-Up Pattern (Start Here) 📣

Use this to make requests scoping-friendly and reviewable.

📢 **Align early, surface constraints, and set expectations on ownership.**

### Problem & Goal

* **What:** Add E2E smoke tests for C8 orchestration workflows
* **Why:** Catch cross-component issues before customer-facing releases
* **Success criteria:** E2E suite runs automatically on RCs; failures are visible and actionable; issues caught before release

### Placement & Behavior

* **Where in the flow:** After RC build, before manual QA starts
* **Blocking or non-blocking:** Blocking — RC progression pauses if tests fail
* **Failure handling:** QA triages failures, communicates status; define what triggers a new RC vs re-run

### Technical Scope

* **Inputs:** RC build artifact/version, environment details, test config
* **Outputs:** Test report + pass/fail signal, logs/artifacts, notification to Slack/dashboard
* **Dependencies:** Existing RC pipeline + orchestration environment; any required secrets/services
* **Performance considerations:** Expected runtime, parallelism needs, runner requirements

### Ownership & Maintenance

* **Owner/DRI:** QA team (test logic + result communication)
* **Maintenance plan:** Who updates tests when components change; deprecation/disable path if flaky

### Risk & Rollback

* **Risks / failure modes:** Flakiness causing RC stalls; environment instability; long runtime
* **Rollback/disable strategy:** Feature flag / toggle to temporarily switch to non-blocking or skip
* **Monitoring/visibility:** Slack alerts, dashboard, run history

### Timeline & Coordination

* **Timeline:** Target v2.1 in ~3 weeks
* **Questions for Monorepo DevOps:** Best way to hook into the existing Monorepo release process?

## Code Review, Integration & Independence 💻

### What Monorepo DevOps Reviews

* Shared CI infrastructure/templates changes
* Release orchestration/train behavior changes
* Integration points that affect cadence, artifacts, or shared patterns
* Performance/reliability risks
* Loop us in for scoping + review via [#epo-reviews](https://camunda.slack.com/archives/C090HGYE5T2) when it could affect release cadence or shared CI patterns.

### Enabling Independent Ownership 🏅

You're encouraged to:

* Design and evolve your own CI/CD workflows
* Use existing Monorepo patterns and integration points
* Communicate results and reasoning clearly in Slack / issues
* Iterate independently as long as you don't:
  * Change shared infrastructure
  * Change release train behavior
  * Impact other teams without coordination

When any of those become true → loop in Monorepo DevOps.

## Quick Reference

### Emergency Contacts

* Slack: [#ask-monorepo-devops](https://camunda.slack.com/archives/C08NZ7E9ZT2)
* Escalation: Engineering manager – see [Monorepo DevOps Team page](https://confluence.camunda.com/spaces/HAN/pages/277021448/Monorepo+DevOps+Team) for current EM/DRI details.

### Key Documentation

* **CI Pipeline Architecture:** [CI & Automation](ci.md) and [C8 Monorepo CI Processes](https://confluence.camunda.com/spaces/HAN/pages/245403757/C8+Monorepo+CI+Processes).
* **Release Process Guide:** [Release Process](release.md).
* **Performance Guidelines:** [Monorepo CI Health KPIs & Impact](https://docs.google.com/document/d/1VDnNy4KOidxOev2_BNNA-BEJQKSTUhspiYqlEKRPaSM).

### Templates & Examples

* **CI Workflow Template:** See patterns and examples in [CI & Automation](ci.md).
* **Integration Request Template:** Use the **Heads-Up Pattern** section on this page together with the CI/Release docs above when opening new GitHub issues.

## FAQ ❓

**Q: How early should I reach out?**
A: As soon as you have a rough idea that might affect the release process. Better to ask too early than too late.

**Q: What if I'm not sure about ownership boundaries?**
A: Use the heads-up template to start the conversation. We'll work together to clarify ownership.

**Q: What if my change seems small, but I'm not sure?**
A: Use the "When in Doubt" rule – a quick Slack message takes 30 seconds.

**Q: Can I just implement and ask for a review later?**
A: Only if your changes are completely self-contained. If there's any doubt, heads-up first.

**Q: What if timelines are tight?**
A: Communicate the timeline pressure early. We can often find faster paths when we understand the constraints.

**Q: How do I know if my change is blocking or non-blocking?**
A: If failure prevents the release from proceeding, it's blocking. When in doubt, start with non-blocking and justify why blocking is necessary.
