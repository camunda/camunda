# Download Shared Build Artifact

Downloads an artifact already produced by the current run's `build-distball` job:

|   Artifact   |        GCS object        |        GitHub artifact name         | GCS timeout per attempt |
|--------------|--------------------------|-------------------------------------|-------------------------|
| distball     | `camunda-zeebe-*.tar.gz` | `distball-${{ github.run_id }}`     | 2 minutes               |
| m2-installed | `m2-installed.tar`       | `m2-installed-${{ github.run_id }}` | 1 minute                |

Non-fork runs use the run-scoped GCS prefix. Fork PRs use the GitHub artifact API,
because they cannot authenticate to GCS. GCS downloads retain the existing policy
of five attempts with ten seconds between attempts.

## Existing consumers

- Unified CI's integration-test job downloads the distball to `dist/target`.
- The focused Gradle check downloads the distball and compares the two tarballs
  directly.
- [`restore-shared-m2`](../restore-shared-m2) downloads the SNAPSHOT archive and
  extracts it into the local Maven repository for integration and unit tests.

The downloader does not authenticate, extract archives, build distributions, or
upload artifacts. Existing callers that build locally on forks keep that fallback
and skip the download action there.

## Prerequisites

- Check out the repository before calling this local action.
- Depend on the job that uploads the selected artifact in the same workflow run.
- For non-fork runs, set `GCS_BUILD_CACHE_BUCKET` and authenticate using
  [`gcs-build-cache-auth`](../gcs-build-cache-auth) first. Keep authentication in a
  separate workflow step with `timeout-minutes: 3` and job permission `id-token: write`.
- For fork downloads, keep the producer's GitHub artifact upload enabled.
- Do not clean up the run-scoped GCS prefix until every consumer finishes.

## Inputs

|  Input   | Required |                       Description                        |
|----------|----------|----------------------------------------------------------|
| artifact | true     | `distball` or `m2-installed`; other values are rejected. |
| path     | true     | Destination directory, created if necessary.             |

## Outputs

None. Downloads the selected archive into `path`, preserving its filename.

## Usage

After checkout and the separate GCS authentication step:

```yaml
- name: Download distball
  uses: ./.github/actions/download-shared-build-artifact
  with:
    artifact: distball
    path: dist/target
```

To download the installed Maven SNAPSHOT archive without extracting it:

```yaml
- name: Download Maven SNAPSHOTs
  uses: ./.github/actions/download-shared-build-artifact
  with:
    artifact: m2-installed
    path: ${{ runner.temp }}
```

