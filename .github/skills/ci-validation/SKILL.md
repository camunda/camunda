---

name: ci-validation
description: Validates GitHub Actions workflow changes in this monorepo using actionlint, conftest policy checks, spotless formatting, and act testability assessment. Use when creating, editing, or reviewing workflow files.
---

# CI Validation Skill

Use this skill for every workflow modification.

## Mandatory Sequence

1. Lint first:
   - `actionlint .github/workflows/*.yml`
2. Policy check:
   - `conftest test --rego-version v0 -o github --policy .github .github/workflows/*.yml`
3. Format:
   - `./mvnw spotless:apply -T1C`
4. Tier classification (mandatory, before act):
   - For each changed workflow, assign Tier 1/2/3 using `.github/skills/act-testing/references/workflow-tiers.md` and the rubric in `.github/skills/act-testing/SKILL.md`.
   - Record: workflow path, tier, one-line rationale, and validation path.
5. Act test (harness-style, mandatory):
   - Read `.github/skills/act-testing/SKILL.md` before proceeding.
   - Tier 1 or 2: create a temporary `test-*.yml` harness, run `check-drift.sh`, assess `act` feasibility (`act`, `docker`, daemon), and provide user-run scenario commands.
   - Keep test harness files available while sharing commands so users can execute them.
   - Remove `test-*.yml` files only after user-run `act` validation is complete.
   - Tier 3: skip harness and document non-applicability with explicit rationale.
   - If multiple workflows changed, either test all Tier 1/2 workflows or explicitly justify why each one is Tier 3.
   - Never run `act` directly against production workflows.
6. Iterate until checks are clean.

Do not skip step 1.

## Required Evidence Format

For every changed workflow, include:

- `workflow: <path>`
- `tier: <1|2|3>`
- `reason: <single sentence>`
- `evidence: <drift-check + feasibility + scenario matrix | tier-3 rationale>`

For Tier 1/2 workflows, `evidence` must explicitly contain:

- `test-workflow: <path>`
- `drift-check: <command + result>`
- `feasibility: act/docker/daemon`
- `scenario-matrix: <positive + negative at minimum>`
- `act-commands: <copy-paste ready plain text using canonical format: act <event> -e <event-json> -W <test-workflow> --secret-file .secrets --reuse; full workspace-relative paths; no markdown links in command text>`
- `cleanup: <test harness removed or justification>`

If these fields are missing, validation is incomplete and should not be reported as done.

## Validation Matrix

- Simple metadata/security updates: `actionlint + conftest + spotless`
- Logic-heavy workflows: `actionlint + conftest + spotless + act`
- External-only workflows: `actionlint + conftest + spotless + rationale`

## Preferred VS Code Tasks

- `CI: actionlint (workflows)`
- `CI: conftest (workflows policy)`
- `CI: verify skill markdown stability`

