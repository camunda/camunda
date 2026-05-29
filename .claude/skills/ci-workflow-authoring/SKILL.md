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

