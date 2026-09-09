---
name: create-rt-issue
description: Create a GitHub issue in camunda/camunda for load-test/Reliability Testing (RT) work, extending create-issue with the component/load-tests label and an rt/foundation, rt/coverage, or rt/enablement classification. Use when asked to create, file, or open an RT/load-test issue.
---

# Create RT Issue

Extends the `create-issue` skill for Reliability Testing (RT) work. Reuses its template selection,
duplicate check, field inference, body composition, and creation flow, and layers on two RT-specific
overrides.

## Procedure

Invoke the `create-issue` skill and follow its procedure, with these two overrides:

### Override 1 — Component label (replaces `create-issue` Step 3)

Skip `create-issue`'s component auto-detection entirely. Always apply `component/load-tests`,
regardless of which files are touched or what the issue is about — this skill is only invoked for
RT/load-test work.

### Override 2 — RT focus-area label (new step, alongside `create-issue` Step 2's field inference)

Every RT issue gets exactly one of the following labels, based on the team's focus-area model
(foundation → enablement → coverage):

| Label            | Meaning                                                                                          |
|-------------------|--------------------------------------------------------------------------------------------------|
| `rt/foundation`   | Tools, automation, or infrastructure investment for reliability testing (test, investigate, validate) |
| `rt/enablement`   | Onboards other teams to run reliability tests proactively on their own                           |
| `rt/coverage`     | Increases reliability-testing coverage of the orchestration cluster or an environment/deployment |

Infer the label from the issue description:
- Building/extending the load-test framework, harness, CI tooling, or dashboards → `rt/foundation`
- Documentation, onboarding, or self-service tooling aimed at other teams running tests themselves →
  `rt/enablement`
- Adding/extending a load-test scenario, or covering a previously-untested feature/environment →
  `rt/coverage`

If the description doesn't clearly fit one category, ask the user to pick one before proceeding —
do not guess or apply more than one.

Additionally, infer the existing `discovered-by/load-tests` label when the description states the
issue was found during a load-test run. This is optional (not every RT issue is discovery-sourced);
leave it off if the description doesn't say so.

### Labels passed at creation

Pass all applicable labels explicitly on `gh issue create` (unlike plain `create-issue`, where most
labels come from the body labeler): `kind/<type>`, `component/load-tests`, the chosen `rt/*` label,
and `discovered-by/load-tests` if applicable.
