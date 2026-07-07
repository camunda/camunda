# Setup Build Action

## Intro

Sets up the standard stack needed to build, install, and run monorepo projects, so
individual jobs don't repeat the same bootstrap. In one step it:

- detects fork PRs (via [`is-fork`](../is-fork)) and disables all credential-dependent
  features when secrets aren't available;
- imports CI secrets from Vault (Nexus, DockerHub, Minimus);
- installs the JDK (`actions/setup-java`);
- registers the Maven problem matcher and configures the Maven cache
  (via [`setup-maven-cache`](../setup-maven-cache));
- merges Camunda Nexus + Google Central mirrors with any extra mirrors/servers and
  writes `settings.xml`;
- optionally sets the build time zone;
- optionally logs into DockerHub, Harbor, and Minimus;
- optionally authenticates gcloud against the GCS build-cache
  (via [`gcs-build-cache-auth`](../gcs-build-cache-auth)).

All credential features are **automatically disabled for fork PRs**, since Vault
secrets can't be retrieved there.

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this
  action and its nested actions are referenced by local path.
- To use any Vault-backed feature (Nexus mirror, DockerHub/Harbor/Minimus login,
  GCS auth), pass `vault-address` / `vault-role-id` / `vault-secret-id`.
- `gcs-build-cache-auth: true` additionally requires the job to grant
  `permissions: id-token: write` (Workload Identity Federation).

## Usage

### Inputs

|          Input           |                                             Description                                              | Required |  Default  |
|--------------------------|------------------------------------------------------------------------------------------------------|----------|-----------|
| camunda-nexus            | Use Camunda Nexus as a Maven mirror (disabled for fork PRs)                                          | false    | `"true"`  |
| dockerhub                | Log into DockerHub with a CI account (disabled for fork PRs)                                         | false    | `"false"` |
| dockerhub-readonly       | Log into DockerHub with a read-only account to avoid rate limits                                     | false    | `"false"` |
| harbor                   | Log into Harbor with a CI account (disabled for fork PRs)                                            | false    | `"false"` |
| minimus                  | Log into Minimus with a CI account (disabled for fork PRs)                                           | false    | `"false"` |
| gcs-build-cache-auth     | Opt-in: set `"true"` to authenticate gcloud to the GCS build-cache via WIF (needs `id-token: write`) | false    | `"false"` |
| java-distribution        | Java distribution to install                                                                         | false    | `temurin` |
| java-version             | JDK version to install                                                                               | false    | `"21"`    |
| maven-cache-key-modifier | Modifier for the Maven cache key                                                                     | false    | `shared`  |
| maven-mirrors            | JSON list of extra Maven mirrors (merged with Nexus, extras win)                                     | false    | `'[]'`    |
| maven-servers            | JSON list of extra Maven servers (merged with Nexus, extras win)                                     | false    | `'[]'`    |
| time-zone                | TZ identifier for the build env, e.g. `Europe/Berlin` (Linux only)                                   | false    |           |
| vault-address            | Vault URL to retrieve secrets from                                                                   | false    |           |
| vault-role-id            | Vault AppRole role id                                                                                | false    |           |
| vault-secret-id          | Vault AppRole secret id                                                                              | false    |           |

### Outputs

None.

## Notes

- `dockerhub` and `dockerhub-readonly` are mutually exclusive — enabling both fails
  the action.
- The Vault path for the DockerHub account is inferred from the calling workflow's
  file name (`operate-*`, `optimize-*`, `tasklist-*`, `zeebe-*`), defaulting to
  `camunda`.

## Example

```yaml
jobs:
  build:
    permissions:
      contents: read
      id-token: write  # only needed when gcs-build-cache-auth is true
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/setup-build
        with:
          dockerhub-readonly: true
          maven-cache-key-modifier: build-shared
          vault-address: ${{ secrets.VAULT_ADDR }}
          vault-role-id: ${{ secrets.VAULT_ROLE_ID }}
          vault-secret-id: ${{ secrets.VAULT_SECRET_ID }}
          gcs-build-cache-auth: true  # share the run-scoped distball / m2 tarball
```

