# Category C — Workflow / CI Config Errors

The workflow YAML itself, an action it calls, or a policy check is the problem.

## Common shapes

### actionlint / yaml syntax

Error names a workflow file and line. Read the file under `.github/workflows/`. Fix locally and
run:

```bash
actionlint .github/workflows/<file>.yml
```

### conftest / policy violation

The repo enforces policy via conftest:

```bash
conftest test --rego-version v0 -o github --policy .github .github/workflows/*.yml
```

Common violations: missing `permissions:`, unpinned action (must be a SHA), forbidden third-party
action. See the `ci-security-compliance` skill for the policy rationale.

### Missing or wrong secret

Log: `Error: Input required and not supplied: <name>` or `Bad credentials`. The secret is
referenced in the workflow but not set, or set at the wrong scope (env vs repo vs org). Do **not**
propose putting the secret value in the diff — flag it for the engineer to add via repo settings.

### Runner label typo / unavailable

Log: `Waiting for a runner...` or `No runner matching the specified labels`. Cross-check the
`runs-on:` against known labels (`gcp-*`, `aws-*`, `ubuntu-slim`, `ubuntu-latest`). Note that
self-hosted runners cost money; GitHub-hosted are free in this public repo.

### Action input contract changed

Log: `Unexpected input(s) ...` or `Required input '<x>' not provided`. The pinned action SHA
predates a breaking change, or vice versa. Read the action's release notes via `gh release view`
on its repo and update the inputs or SHA accordingly. Consult `ci-workflow-authoring` for pinning
conventions.

### Composite action error

If the failing step calls a local composite action (under `.github/actions/`), follow the same
flow into that action's `action.yml`.

### New third-party action blocked

If the GitHub Actions run does not start because a newly introduced third-party action is not allowed,
follow the instructions in `ci.md` for getting an approval.

## Proposing a workflow fix

1. Name the exact file and YAML key to change.
2. Show the before/after snippet.
3. Recommend running the `ci-validation` skill after the edit — it runs actionlint, conftest,
   spotless, and act assessment in the required order.

Do **not** edit workflow files without the user's go-ahead, and do not commit them without
running `ci-validation` first.
