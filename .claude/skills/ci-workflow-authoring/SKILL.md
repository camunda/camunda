---

name: ci-workflow-authoring
description: Authors and refactors GitHub Actions workflows and composite actions for this monorepo using required conventions, minimal permissions, pinned SHAs, and observability steps. Use when creating or restructuring CI workflows.
---

# CI Workflow Authoring Skill

Check `docs/monorepo-docs/ci.md` first.

## Required Workflow Conventions

- Include metadata comments (`description`, `test location`, `type`, `owner`).
- Set default shell to bash.
- Start with `permissions: {}` and add only required scopes.
- Add `timeout-minutes` per job.
- Pin all external actions to commit SHAs.
- Ensure each job ends with `observe-build-status` step.

## Reuse Existing Composite Actions

- Before writing an inline install/setup/util step, check `.github/actions/` for an existing
  composite action and prefer it over reimplementing the logic inline.
- If the same step gets copy-pasted across workflows, extract it into a composite action under
  `.github/actions/` (see `references/composite-action-template.md`) rather than duplicating it.
- Composite/local actions do **not** enforce `required: true` on inputs (only `workflow_call`
  inputs are validated), so validate required inputs inside the action and fail fast.

## Unified CI Criteria

- Runtime <= 30 minutes
- Stable and observable
- Vault-based secret handling
- Uses approved cache strategy

## Caching Strategy

- Maven: `.github/actions/setup-maven-cache`
- npm is the default for Node.js workflows in this monorepo
- Yarn (legacy workflows only): `camunda/infra-global-github-actions/setup-yarn-cache`
- Do not cache Docker layers via GHA cache
- Write caches only from `main` and `stable*`

## Templates

- Workflow template: `references/workflow-template.md`
- Composite action template: `references/composite-action-template.md`

