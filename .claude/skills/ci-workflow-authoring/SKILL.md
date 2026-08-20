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
- Fail fast: validate cheap preconditions (required secrets, Vault reachability, input/tag contracts) in a preflight step or job before expensive build/test steps.

## Recommendation: restrict `on: push` to main/stable branches

Use `push` only for workflows on `main`/`stable/*`, where every commit needs a full build for
confidence. Use `pull_request` everywhere else, so a branch doesn't get two trigger sources to
deduplicate.

## Recommendation: concurrency groups for PR, push, and scheduled workflows

Add a `concurrency:` group to cancel superseded runs, reduce race conditions, and avoid wasted
runner cost. Copy the pattern from
[`ci.yml`](https://github.com/camunda/camunda/blob/b4da8ace350ca5f64564bf764b7d85833c366995/.github/workflows/ci.yml#L20-L25)
rather than inventing a new one:

```yaml
concurrency:
  cancel-in-progress: true
  group: ${{ format('{0}-{1}-{2}', github.workflow, github.event_name, (github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/heads/stable/')) && github.sha || github.ref) }}
```

`github.event_name` keeps different trigger sources on the same ref from colliding, especially
scheduled cache-refresh runs and push builds on `main`/`stable/*`. The `github.sha` fallback for
`main`/`stable/*` is intentional: those branches need complete builds for every pushed commit, and
a cancelled push run can mean a missed alert or skipped artifact push.

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
