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

|             Skill              |                                          Description                                          |
|--------------------------------|-----------------------------------------------------------------------------------------------|
| `ci-push-workflow-health`      | Analyze CI failure patterns for push-triggered workflow jobs on main and stable/* branches    |
| `ci-runner-utilization`        | Detect CI runner underutilization and give downsizing recommendations for cost savings        |
| `ci-scheduled-workflow-health` | Generate an HTML health report for all scheduled GitHub Actions workflows                     |
| `create-issue`                 | Create a GitHub issue with the correct template, component label, and parent link             |
| `engine-expert`                | Implement or fix capabilities in the Zeebe workflow engine (`zeebe/engine/`)                  |
| `frontend-feature`             | Build new pages, components, or features in the orchestration cluster webapp                  |
| `frontend-integration-test`    | Write or debug Playwright-based integration, visual, and accessibility tests in the OC webapp |
| `frontend-migrator`            | Migrate or port frontend code from `operate/client/` or `tasklist/client/` to the OC webapp   |
| `frontend-operate-migrator`    | Operate-specific overrides, migration loop protocol, and per-page context for OC webapp ports |
| `frontend-unit-test`           | Write or debug Vitest browser-mode unit tests in the orchestration cluster webapp             |
| `operate-frontend`             | Fix bugs or make changes in the Operate legacy frontend at `operate/client/`                  |
| `tasklist-frontend`            | Build or change Tasklist pod features in the OC webapp at `src/tasklist/`                      |

## Adding a new skill

1. Create a new directory under `.claude/skills/` matching the skill name (lowercase, hyphens only).
2. Add a `SKILL.md` with the required frontmatter (`name`, `description`) and instructions.
3. Update the table above.

