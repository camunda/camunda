# skills/

Repo-specific Claude Code skills for the Camunda monorepo.

Each skill lives in its own subdirectory and must contain a `SKILL.md` with a
frontmatter `name` and `description` that the harness uses to load it:

```
skills/
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
