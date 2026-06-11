# Release Process

Releases are owned by `@camunda/distribution` and triggered manually via the
[C8Run Release GitHub workflow](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml).

---

## macOS Signing Requirements

### What gets signed

`sign_and_notarize.sh` signs everything in the c8run package directory except the items listed in
`EXCLUDE_PREFIXES` (`camunda-zeebe`, `connector-runtime-bundle`, `elasticsearch`), which are
moved out before signing and re-injected after notarization unsigned.

|          Content type           |                           How it is signed                            |
|---------------------------------|-----------------------------------------------------------------------|
| `.app` bundles                  | `codesign --deep --options runtime` (covers all contents recursively) |
| Mach-O files outside `.app`     | `codesign --options runtime`                                          |
| Mach-O inside `.jar` files      | Extracted, signed individually, jar rebuilt                           |
| Mach-O under a JVM runtime path | Same as above **plus** JIT entitlements plist (see below)             |

### JVM runtime entitlements

Binaries inside a bundled JRE/JDK/runtime require three additional entitlements that the standard
hardened runtime blocks:

|                       Entitlement                        |                      Why required                      |
|----------------------------------------------------------|--------------------------------------------------------|
| `com.apple.security.cs.allow-jit`                        | JVM JIT compilation via `pthread_jit_write_protect_np` |
| `com.apple.security.cs.allow-unsigned-executable-memory` | JIT code page allocation                               |
| `com.apple.security.cs.disable-library-validation`       | Native libraries loaded by the JVM                     |

Without these, `libjvm.dylib` crashes with `EXC_BREAKPOINT` during `Threads::create_vm` on
Apple Silicon (see [#54877](https://github.com/camunda/camunda/issues/54877)).

The script applies these entitlements to any Mach-O binary whose path contains a directory name
listed in `JRE_DIR_NAMES` (currently `jre`, `jdk`, `runtime`). **To bundle an additional JRE
or JDK under a different directory name, add that name to `JRE_DIR_NAMES`** — signing,
entitlement verification, and the JVM smoke test all derive their scope from that single array.

### Post-signing verification (Step C.5)

Before submitting to Apple Notary, the script verifies the entire signed tree:

1. **Signature check** — `codesign --verify --strict` on every Mach-O file and
   `codesign --verify --deep --strict` on every `.app` bundle. Any unsigned or
   invalidly signed binary fails the release.
2. **JIT entitlement check** — every Mach-O under a `JRE_DIR_NAMES` directory is
   additionally checked for `allow-jit`. A missing entitlement fails the release before
   Apple sees the artifact.

### Post-notarization smoke test (Step G)

After notarization and re-injection of excluded items, the script finds every `bin/java`
executable under any `JRE_DIR_NAMES` directory in the re-packaged tree and runs
`java -version`. A crash at this point (e.g. a newly added JRE binary missing entitlements)
fails the release before upload.

---

## Before You Start

### 1. Check Docker Compose is released

> [!WARNING]
> Ensure the Docker Compose versions were released correctly prior to the C8Run release.

The easiest place to verify this — and to look up all updated component versions at once — is the
Renovate PR in [camunda/camunda-distributions](https://github.com/camunda/camunda-distributions/pulls)
labelled `deps/docker-compose` and `version/8.x`. That PR shows the full version delta for
Camunda, Connectors, and Docker Compose in one place. Wait for it to be merged before proceeding.

### 2. Determine the versions to release

Open the Renovate PR described above. The table at the top of the PR body lists every version
change. Record:

These two variables are the ones you will update in `./c8run/.env`:

| `./c8run/.env` variable |               Where to read it from                |
|-------------------------|----------------------------------------------------|
| `CAMUNDA_VERSION`       | `camunda/camunda` row in the Renovate PR           |
| `CONNECTORS_VERSION`    | `camunda/connectors-bundle` row in the Renovate PR |

> **Note:** `CAMUNDA_DOCKER_VERSION` and `ELASTICSEARCH_VERSION` are **not** part of `./c8run/.env`
> — they belong to the Docker Compose distribution and are managed separately in
> `camunda/camunda-distributions`.

You can also cross-check that no c8run artifacts have been published yet for the target release:

```bash
gh release view <version> --repo camunda/camunda --json assets --jq '.assets[].name' 2>/dev/null \
  | grep 'camunda8-run-' || echo "No c8run artifacts found (or release does not exist yet)"
```

If there is no output, the c8run release has not been done yet.

---

## Release Candidates

### 1. Version Bump

For each version:

1. Clone [camunda/camunda](https://github.com/camunda/camunda).
2. Create a new branch from the version base branch:
   - `main` for the latest alpha.
   - `stable/8.8` for 8.8, etc.
   - Branch name convention: `c8run-release-<version>` (e.g. `c8run-release-8.8.24`)
3. Update `./c8run/.env` with the correct versions determined above.
4. Commit with `deps: update c8run versions to <version>` and open a PR targeting the version
   base branch (e.g. `stable/8.8`). Get it merged before triggering the workflow.

### 2. Artifact

For each version, trigger the
[C8Run Release GitHub workflow](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml)
via the UI or the `gh` CLI:

```bash
gh workflow run c8run-release.yaml \
  --repo camunda/camunda \
  --ref main \
  --field branch=stable/8.8 \
  --field camundaVersion=8.8 \
  --field camundaAppsRelease=8.8.24 \
  --field publishToCamundaAppsRelease=true \
  --field publishToCamundaDownloadCenter=false \
  --field typePrerelease=false \
  --field artifactVersionSuffix="-rc"
```

If using the UI instead:

|                    Input                    |                            Value                            |
|---------------------------------------------|-------------------------------------------------------------|
| Run workflow from                           | `Branch: main` (always)                                     |
| Release branch                              | e.g. `stable/8.7` for 8.7                                   |
| Camunda minor version                       | e.g. `8.7`                                                  |
| Camunda app GH release                      | e.g. `8.7.4`                                                |
| Publish to Camunda apps GitHub release page | **Ticked**                                                  |
| Publish to Camunda Download Center          | **Unticked** (RCs are not published to the Download Center) |
| Artifact version suffix                     | `-rc` (include the dash)                                    |

Then:

1. Monitor the GitHub Action logs.
2. Confirm `*-rc` artifacts are published in [Camunda repo GitHub releases](https://github.com/camunda/camunda/releases) under the correct Camunda tag version.
3. Report back each version in the release train form in the Slack release thread that the RC artifacts for C8Run are created.

---

## Public Release

For each version, trigger the workflow via the `gh` CLI:

```bash
gh workflow run c8run-release.yaml \
  --repo camunda/camunda \
  --ref main \
  --field branch=stable/8.8 \
  --field camundaVersion=8.8 \
  --field camundaAppsRelease=8.8.24 \
  --field publishToCamundaAppsRelease=true \
  --field publishToCamundaDownloadCenter=true \
  --field typePrerelease=false \
  --field artifactVersionSuffix=""
```

If using the UI instead:

|                    Input                    |                    Value                     |
|---------------------------------------------|----------------------------------------------|
| Run workflow from                           | `Branch: main` (always)                      |
| Release branch                              | e.g. `stable/8.8` for 8.8, `main` for latest |
| Camunda minor version                       | e.g. `8.6`, `8.7`, `8.8`                     |
| Camunda app GH release                      | e.g. `8.7.1`, `8.8-alpha4.1`                 |
| Publish to Camunda apps GitHub release page | **Ticked**                                   |
| Publish to Camunda Download Center          | **Ticked**                                   |
| Artifact version suffix                     | *(leave empty)*                              |

Then:

1. Monitor the GitHub Action logs.
2. Confirm artifacts are added to the [Camunda repo GitHub release](https://github.com/camunda/camunda/releases).
3. Delete any prior C8Run releases tagged with `-pending-removal`.
4. Delete the RC artifacts from the Camunda GitHub release (the version specified in the release inputs).
5. Report back each version in the release train form in the Slack release thread that the C8Run release is complete.

