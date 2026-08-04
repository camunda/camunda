# .claude/skills/

Repo-specific Claude Code skills for the Camunda monorepo. Skills are loaded automatically by
the harness from this directory.

Each skill lives in its own subdirectory and must contain a `SKILL.md` with a
frontmatter `name` and `description` that the harness uses to load it:

```
.claude/skills/
  my-skill/
    SKILL.md        ← required: frontmatter + instructions
    reference.md    ← optional: supporting reference material
```

Minimal `SKILL.md` structure:

```markdown
---
name: my-skill
description: One-line trigger description used by the harness to decide when to load this skill.
---

# My Skill

Instructions go here.
```

Skills here extend the org-level skills in the central
[camunda/.github AGENTS.md](https://github.com/camunda/.github/blob/main/AGENTS.md).
When a skill exists for a recurring operation, use it rather than improvising steps.

## Available skills

|              Skill              |                                             Description                                             |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `act-testing`                    | Prepare act-testable GitHub Actions workflow scenarios and assess local `act` feasibility            |
| `agentic-workflows`              | Route gh-aw workflow create/debug/upgrade requests to the right prompts                              |
| `analytics-exporter`             | Add a new event handler or metric to the analytics exporter (`zeebe/exporters/analytics-exporter/`)  |
| `babysit-pr`                     | Self-driving loop to shepherd one or more PRs (incl. backports) to merged: rerun flaky CI, enqueue, re-enqueue |
| `ci-fix-failure`                 | Diagnose failing GitHub Actions runs and propose fixes                                               |
| `ci-flood-triage`                | Orient fast when a flood of CI incidents opens at once — find the shared pattern, flag outliers      |
| `ci-incident`                    | Drive CI incident response for a full incident by ID                                                 |
| `ci-push-workflow-health`        | Analyze CI failure patterns for push-triggered workflow jobs on main and stable/* branches            |
| `ci-runner-utilization`          | Detect CI runner underutilization and give downsizing recommendations for cost savings               |
| `ci-scheduled-workflow-health`   | Generate an HTML health report for all scheduled GitHub Actions workflows                            |
| `ci-security-compliance`         | Enforce GitHub Actions security and compliance — third-party actions, secrets, permissions           |
| `ci-validation`                  | Validate GitHub Actions workflow changes with actionlint, conftest, spotless, and act testability    |
| `ci-workflow-authoring`          | Author and refactor GitHub Actions workflows and composite actions with required conventions         |
| `create-issue`                   | Create a GitHub issue with the correct template, component label, and parent link                    |
| `engine-expert`                  | Implement, fix, or review Zeebe engine code (`zeebe/engine/`)                                        |
| `grill-me`                       | Interview the user relentlessly about a plan or design until reaching shared understanding           |
| `load-test-ops`                  | Trigger, monitor, update, profile, and stop Camunda load tests via `gh` and `kubectl`                 |
| `operate-frontend`               | Fix bugs or make changes in the Operate frontend at `operate/client/`                                |
| `zeebe-flamegraph-diff`          | Parse and compare async-profiler CPU flamegraphs from Camunda/Zeebe brokers                           |

## Adding a new skill

1. Create a new directory under `.claude/skills/` matching the skill name (lowercase, hyphens only).
2. Add a `SKILL.md` with the required frontmatter (`name`, `description`) and instructions.
3. Update the table above.
