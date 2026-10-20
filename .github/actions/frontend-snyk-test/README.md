# Frontend Snyk Test

Scans a frontend project's lockfile (`package-lock.json` or `yarn.lock`) for open-source
vulnerabilities with [`snyk test`](https://docs.snyk.io/developer-tools/snyk-cli/commands/test)
and uploads the JSON report as an artifact.

No dependency install or build is needed: Snyk resolves the dependency tree from the lockfile.
Only production `dependencies` are scanned, `devDependencies` are excluded.

Snyk runs from `directory`, so a `.snyk` policy file there is applied. Use it to ignore accepted
vulnerabilities, always with a `reason` and an `expires` date.

## Inputs

|        Input         | Required |       Default       |                             Description                              |
|----------------------|----------|---------------------|----------------------------------------------------------------------|
| `directory`          | yes      |                     | Frontend directory containing `package.json` and the lockfile        |
| `lockfile`           | no       | `package-lock.json` | Lockfile name inside `directory`                                     |
| `project-name`       | yes      |                     | Human readable project name, used for logging                        |
| `artifact-name`      | yes      |                     | Name of the uploaded JSON report artifact (`/` is replaced with `-`) |
| `snyk-org`           | no       | `team-zeebe`        | Snyk organization to scan against                                    |
| `severity-threshold` | no       | `high`              | Minimum vulnerability severity that fails the job                    |

The `SNYK_TOKEN` environment variable must be set.

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

