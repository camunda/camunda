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
- optionally logs into DockerHub, Harbor, and Minimus.

All credential features are **automatically disabled for fork PRs**, since Vault
secrets can't be retrieved there.

GCS build-cache auth (WIF) is a separate, self-contained action —
[`gcs-build-cache-auth`](../gcs-build-cache-auth) — called directly by the job
rather than nested inside this one, so its own `timeout-minutes` can bound a
Vault/WIF hang (see that action's README).

## Prerequisites

- The repository must be checked out first (e.g. `actions/checkout`), since this
  action and its nested actions are referenced by local path.
- To use any Vault-backed feature (Nexus mirror, DockerHub/Harbor/Minimus login),
  pass `vault-address` / `vault-jwt-role` / `vault-jwt-path` / `vault-jwt-audience` and grant the job `id-token: write`.

## Usage

### Inputs

|          Input           |                                                       Description                                                        | Required |  Default  |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------|----------|-----------|
| camunda-nexus            | Use Camunda Nexus as a Maven mirror (disabled for fork PRs)                                                              | false    | `"true"`  |
| dockerhub                | Log into DockerHub with a CI account (disabled for fork PRs)                                                             | false    | `"false"` |
| dockerhub-readonly       | Log into DockerHub with a read-only account to avoid rate limits                                                         | false    | `"false"` |
| harbor                   | Log into Harbor with a Harbor robot account (disabled for fork PRs)                                                      | false    | `"false"` |
| minimus                  | Log into Minimus with a CI account (disabled for fork PRs)                                                               | false    | `"false"` |
| java-distribution        | Java distribution to install                                                                                             | false    | `temurin` |
| java-version             | JDK version to install                                                                                                   | false    | `"21"`    |
| maven-cache-key-modifier | Modifier for the Maven cache key                                                                                         | false    | `shared`  |
| maven-mirrors            | JSON list of extra Maven mirrors (merged with Nexus, extras win)                                                         | false    | `'[]'`    |
| maven-servers            | JSON list of extra Maven servers (merged with Nexus, extras win)                                                         | false    | `'[]'`    |
| time-zone                | TZ identifier for the build env, e.g. `Europe/Berlin` (Linux only)                                                       | false    |           |
| vault-address            | Vault URL to retrieve secrets from                                                                                       | false    |           |
| vault-role-id            | Vault AppRole role id                                                                                                    | false    |           |
| vault-secret-id          | Vault AppRole secret id                                                                                                  | false    |           |
| vault-jwt-role           | Vault JWT role (`secrets.VAULT_JWT_ROLE`); when set, the action logs in via JWT/OIDC and the job needs `id-token: write` | false    |           |
| vault-jwt-path           | Vault JWT auth backend path (`secrets.VAULT_JWT_PATH`)                                                                   | false    |           |
| vault-jwt-audience       | GitHub OIDC audience for the Vault JWT login (`secrets.VAULT_JWT_AUDIENCE`)                                              | false    |           |

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
      id-token: write # Vault JWT login
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/setup-build
        with:
          dockerhub-readonly: true
          maven-cache-key-modifier: build-shared
          vault-address: ${{ secrets.VAULT_ADDR }}
          vault-jwt-role: ${{ secrets.VAULT_JWT_ROLE }}
          vault-jwt-path: ${{ secrets.VAULT_JWT_PATH }}
          vault-jwt-audience: ${{ secrets.VAULT_JWT_AUDIENCE }}
```

To also share the run-scoped distball / m2 tarball via the GCS build-cache, call
[`gcs-build-cache-auth`](../gcs-build-cache-auth) directly alongside this action —
see its README.

