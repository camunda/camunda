# Release Process

Releases are owned by `@camunda/distribution` and triggered manually via the
[C8Run Release GitHub workflow](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml).

> [!WARNING]
> Ensure the Docker Compose versions were released correctly prior to the C8Run release.

---

## Release Candidates

### 1. Version Bump

For each version:

1. Clone [camunda/camunda](https://github.com/camunda/camunda).
2. Create a new branch from the version base branch:
   - `main` for the latest alpha.
   - `stable/8.8` for 8.8, etc.
3. Update `./c8run/.env` with the correct versions and tags.
   - For the Connectors version, check the [Camunda Artifacts repo](https://artifacts.camunda.com/ui/native/connectors/io/camunda/connector/connector-runtime-bundle/).
4. Create a PR from the branch and get it merged.

### 2. Artifact

For each version, click **Run workflow** in the
[C8Run Release GitHub workflow](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml):

| Input | Value |
|---|---|
| Run workflow from | `Branch: main` (always) |
| Release branch | e.g. `stable/8.7` for 8.7 |
| Camunda minor version | e.g. `8.7` |
| Camunda app GH release | e.g. `8.7.4` |
| Publish to Camunda apps GitHub release page | **Ticked** |
| Publish to Camunda Download Center | **Unticked** (RCs are not published to the Download Center) |
| Artifact version suffix | `-rc` (include the dash) |

Then:

1. Monitor the GitHub Action logs.
2. Confirm `*-rc` artifacts are published in [Camunda repo GitHub releases](https://github.com/camunda/camunda/releases) under the correct Camunda tag version.
3. Report back each version in the release train form in the Slack release thread that the RC artifacts for C8Run are created.

---

## Public Release

For each version, click **Run workflow** in the
[C8Run Release GitHub workflow](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml):

| Input | Value |
|---|---|
| Run workflow from | `Branch: main` (always) |
| Release branch | e.g. `stable/8.8` for 8.8, `main` for latest |
| Camunda minor version | e.g. `8.6`, `8.7`, `8.8` |
| Camunda app GH release | e.g. `8.7.1`, `8.8-alpha4.1` |
| Publish to Camunda apps GitHub release page | **Ticked** |
| Publish to Camunda Download Center | **Ticked** |
| Artifact version suffix | *(leave empty)* |

Then:

1. Monitor the GitHub Action logs.
2. Confirm artifacts are added to the [Camunda repo GitHub release](https://github.com/camunda/camunda/releases).
3. Delete any prior C8Run releases tagged with `-pending-removal`.
4. Delete the RC artifacts from the Camunda GitHub release (the version specified in the release inputs).
5. Report back each version in the release train form in the Slack release thread that the C8Run release is complete.
