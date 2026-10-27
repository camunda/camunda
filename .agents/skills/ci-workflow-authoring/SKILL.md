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
- Set a workflow-level `env: TEST_OWNER:` with the owning team's canonical
  `@camunda/...` handle. The `# owner:` metadata comment is for humans only —
  `TEST_OWNER` is the functional field: `observe-build-status` reads it as the
  default `user_description`, so without it a failure is submitted with **no
  owner** and no medic is paged. The handle must match `.codeowners` (verify
  with `codeowners-cli owner <path>`). Override per job for outliers, and use
  `env: TEST_OWNER: ${{ matrix.owner }}` for matrix-driven ownership.

### Stable / product branches

`TEST_OWNER` only routes if the target branch's
`.github/actions/observe-build-status` falls back to `env.TEST_OWNER`. This
holds on `main` and the maintained `stable/8.x` branches, but do not assume it
for older or product branches (e.g. `stable/operate-8.5`). Before relying on it
on such a branch, confirm that branch's `observe-build-status`/`print-metadata`
reads `env.TEST_OWNER`; if it does not, pass the owner via the step's
`user_description`/`test_owner` input instead, or omit the field rather than
leave a dead reference.

## Reuse Existing Composite Actions

- Before writing an inline install/setup/util step, check `.github/actions/` for an existing
  composite action and prefer it over reimplementing the logic inline.
- If the same step gets copy-pasted across workflows, extract it into a composite action under
  `.github/actions/` (see `references/composite-action-template.md`) rather than duplicating it.
- Composite/local actions do **not** enforce `required: true` on inputs (only `workflow_call`
  inputs are validated), so validate required inputs inside the action and fail fast.
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
