---

name: act-testing
description: Prepares act-testable GitHub Actions workflow scenarios for this monorepo. Use when validating workflow logic locally, generating temporary test harnesses, checking logic drift, assessing local act feasibility, and proposing reproducible user-run test cases.
---

# Act Testing Skill

Use this skill when a workflow change needs local validation with `act`.

## Mandatory Validation Checkpoint

After creating or editing a workflow:
1. Assess if the workflow is Tier 1, Tier 2, or Tier 3 (see `references/workflow-tiers.md`).
   - Re-assess tier after every major scope change during iteration (e.g., removed jobs/branches/options).
2. If Tier 1 or Tier 2, create a temporary `test-<workflow>.yml` harness and run drift guard:
   - `bash .github/skills/act-testing/scripts/check-drift.sh <production-workflow> <test-workflow>`
3. Assess whether `act` execution is possible in the current environment.
4. Provide the user with concrete test cases and suggested `act` commands to run manually.
5. If Tier 3, explicitly document why act testing is not applicable.

Never silently skip the testability decision.
Never run `act` directly against production `*.yml` files — always use a `test-*.yml` harness.
The agent prepares harnesses and validation guidance; the user runs `act`.

## Tier Decision Rubric (use before preparing act guidance)

Use this quick rubric per changed workflow:

- Tier 1 (fully testable): core behavior is internal control flow, expressions, and bash logic that can be validated locally.
- Tier 2 (partially testable): workflow combines meaningful internal logic with external integrations; test internal logic with harness + mocks.
- Tier 3 (not act-testable): workflow value is predominantly external orchestration/side effects (e.g., Vault-dependent auth, remote API mutation, deployment state), and local mocks would provide low confidence.

Decision rule:

- If internal logic is a primary risk, prepare harness + scenario matrix (Tier 1/2).
- If external orchestration is the primary risk and internal logic is minimal, Tier 3 rationale is acceptable.
- For multi-workflow changes, classify each workflow explicitly; do not infer one workflow's tier from another.

## Mocked-Signal Gate (mandatory)

Before committing to Tier 1/2 harnessing, estimate how much critical behavior is mocked in the harness:

- If >=70% of critical path steps are mocked, do **not** default to Tier 1/2.
- In that case, either:
   - classify as Tier 3, or
   - provide a written justification why remaining unmocked logic still gives high-confidence signal.

Required statement for Tier 1/2: "What risk is actually tested by act in this harness?"

If this statement cannot be answered clearly, classify as Tier 3.

## Harness Rules

- Copy production business logic verbatim.
- Mock only external dependencies.
- Wrap mocked setup in markers:
  - `# MOCK-START`
  - `# MOCK-END`
- Keep all non-mock logic equivalent to production so drift checks remain valid.

## Common External Integrations to Mock

Replace these with `echo` stubs in temporary harnesses:
- `hashicorp/vault-action`
- `slackapi/slack-github-action`
- `./.github/actions/observe-build-status`
- `./.github/actions/setup-maven-cache`
- `camunda/infra-global-github-actions/*`

Example mock step:

```yaml
- name: Mock Vault lookup
  run: echo "MOCK: Vault secret retrieval skipped for local act run"
```

## Required Command Loop

1. Run drift check:
   - `bash .github/skills/act-testing/scripts/check-drift.sh <production-workflow> <test-workflow>`
2. Assess `act` feasibility in the current environment (document pass/fail):
   - `command -v act`
   - `command -v docker`
   - `docker info`
3. Provide user-run commands per scenario (examples):
   - `act pull_request -e .github/skills/act-testing/references/event-payloads/<fixture>.json -W .github/workflows/test-<workflow>.yml`
   - `act push -e .github/skills/act-testing/references/event-payloads/<fixture>.json -W .github/workflows/test-<workflow>.yml`
   - `act workflow_dispatch -e .github/skills/act-testing/references/event-payloads/<fixture>.json -W .github/workflows/test-<workflow>.yml --secret-file .secrets --reuse`
   - `ACT_RUNNER_IMAGE=ghcr.io/catthehacker/ubuntu:act-latest act pull_request -e .github/skills/act-testing/references/event-payloads/<fixture>.json -W .github/workflows/test-<workflow>.yml`

Do not run `act` on behalf of the user. The user remains responsible for executing and interpreting local `act` runs.

## Command Formatting Requirements

- Always use full workspace-relative paths in commands, e.g. `.github/workflows/test-docs-preview.yml`.
- Always use this canonical argument order: `act <event> -e <event-json> -W <test-workflow> --secret-file .secrets --reuse`.
- Never format command arguments as markdown links or rich-text references.
   - Invalid: command text where the workflow argument is rendered as a clickable markdown link instead of plain text.
   - Valid: `act pull_request -e .github/skills/act-testing/references/event-payloads/pr-opened-ready.json -W .github/workflows/test-docs-preview.yml`

## Scenario Matrix Requirements

For every Tier 1/2 workflow, suggest a minimal scenario matrix covering workflow logic, including:

- positive path (expected job/step execution)
- negative path (guard rails, skip, or failure branch)
- trigger variation (branch/tag/event differences)
- actor/source variation (bot vs human when relevant)
- input variation (`workflow_dispatch` or matrix inputs when present)

Each scenario should include: fixture file, expected observable markers, and exact `act` command to run.

## Mandatory Agent Output Contract

For every Tier 1/2 workflow, the final user message must include all of the following before completion:

1. `test-workflow: <path>` (temporary harness path used for validation)
2. `drift-check: <exact command + result>`
3. `feasibility:` results for `command -v act`, `command -v docker`, and `docker info`
4. `scenario-matrix:` at least one positive and one negative scenario
5. `act-commands:` exact copy-paste commands for each scenario
6. `cleanup:` explicit statement that temporary `test-*.yml` was removed (or why retained)

If any item above is missing, validation is incomplete.

For Tier 3 workflows, the final user message must still include:

- `tier: 3`
- `non-applicability rationale: <why external orchestration dominates>`
- `act-scenarios: not required`
- `harness: not created (or removed) because mocked signal is insufficient`

## Success Metrics for Act Readiness

Track these in issue/PR evidence:
- Tier-1 harness coverage: 100%
- Tier-2 partial coverage: at least one positive and one negative path covered
- Agent act-checkpoint hit rate: >=80%
- Logic preservation rate (drift guard): 100%
- Mock correctness: 0 unmocked external integrations in harnesses
- Feasibility assessment completion: 100% for Tier 1/2 workflows
- Scenario matrix completeness: at least one positive + one negative path per Tier 1/2 workflow

## Golden Fixtures and Expected Outputs

Use event payload fixtures under `references/event-payloads/`.
Each scenario should define expected output markers to avoid subjective verification.

## Evidence and Cleanup Lifecycle

Temporary harnesses are validation artifacts.

1. Create `test-*.yml` in the PR branch.
2. Run drift guard + feasibility checks.
3. Provide user-run `act` commands and scenario matrix.
4. Keep `test-*.yml` files in the branch while providing commands so the user can execute them.
5. Paste results in PR description inside a collapsible `<details>` block.
6. Remove `test-*.yml` files only after user-run `act` validation is complete.

Never merge temporary test harness files to `main`.

## Minimum Evidence Requirements

For each changed workflow, capture one of:

- Tier 1/2: drift check result + feasibility assessment + scenario matrix with user-run commands.
- Tier 3: explicit non-applicability rationale stating which critical behaviors depend on external systems and why mock-based act signal is insufficient.

## Reference Files

- Tier mapping and ownership: `references/workflow-tiers.md`
- Test workflow template: `references/test-workflow-template.md`
- Event payload templates: `references/event-payloads.md`
- Local secrets template: `references/secrets-template.md`

