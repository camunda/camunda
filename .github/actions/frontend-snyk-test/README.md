# Frontend Snyk Test

Scans a frontend project's lockfile (`package-lock.json` or `yarn.lock`) for open-source
vulnerabilities with [`snyk test`](https://docs.snyk.io/developer-tools/snyk-cli/commands/test)
and uploads the JSON report as an artifact.

No dependency install or build is needed: Snyk resolves the dependency tree from the lockfile.
Only production `dependencies` are scanned, `devDependencies` are excluded.

Snyk runs from `directory`, so a `.snyk` policy file there is applied. Use it to ignore accepted
vulnerabilities, always with a `reason` and an `expires` date.

## npm workspaces

By default Snyk resolves only the root `package.json` against the lockfile. In a workspaces
monorepo (for example `webapp/client`) the root usually has no production dependencies, so the
scan would pass without testing anything. Set `all-projects: "true"` to run
`snyk test --all-projects --fail-fast`, which detects every workspace and resolves it against the
root lockfile ([Snyk docs](https://docs.snyk.io/supported-languages/supported-languages-list/javascript#support-for-monorepos-and-workspaces)).
In this mode:

- `.snyk` in `directory` is passed with `--policy-path`, so it applies to every workspace.
- `--all-projects` also picks up manifests of other ecosystems; skip them with `exclude`
  (for example `pom.xml,build.gradle.kts`).
- `--fail-fast` fails the scan if a project cannot be resolved instead of leaving it out.

The `scan-mode` output reports the mode that ran. Callers that check out another branch before
using this action get that branch's copy, so they can compare `scan-mode` to detect a copy that
predates `all-projects` and ignored it.

## Inputs

|        Input         | Required |       Default       |                                   Description                                    |
|----------------------|----------|---------------------|----------------------------------------------------------------------------------|
| `directory`          | yes      |                     | Frontend directory containing `package.json` and the lockfile                    |
| `lockfile`           | no       | `package-lock.json` | Lockfile name inside `directory`                                                 |
| `project-name`       | yes      |                     | Human readable project name, used for logging                                    |
| `artifact-name`      | yes      |                     | Name of the uploaded JSON report artifact (`/` is replaced with `-`)             |
| `snyk-org`           | no       | `team-zeebe`        | Snyk organization to scan against                                                |
| `severity-threshold` | no       | `high`              | Minimum vulnerability severity that fails the job                                |
| `all-projects`       | no       | `false`             | Scan all workspaces with `--all-projects`, see [npm workspaces](#npm-workspaces) |
| `exclude`            | no       |                     | Comma-separated file or directory names to skip, only with `all-projects`        |

The `SNYK_TOKEN` environment variable must be set.

## Outputs

|   Output    |                       Description                       |
|-------------|---------------------------------------------------------|
| `scan-mode` | `all-projects` or `lockfile`, depending on the scan run |

## Usage

```yaml
- uses: ./.github/actions/frontend-snyk-test
  env:
    SNYK_TOKEN: ${{ steps.snyk-secrets.outputs.SNYK_TOKEN }}
  with:
    directory: optimize/client
    lockfile: yarn.lock
    project-name: Optimize frontend
    artifact-name: snyk-report-optimize-${{ inputs.branch }}
```

For an npm workspaces monorepo:

```yaml
- uses: ./.github/actions/frontend-snyk-test
  env:
    SNYK_TOKEN: ${{ steps.snyk-secrets.outputs.SNYK_TOKEN }}
  with:
    directory: webapp/client
    all-projects: "true"
    exclude: pom.xml,build.gradle.kts
    project-name: Orchestration Cluster Webapp frontend
    artifact-name: snyk-report-webapp-${{ inputs.branch }}
```

