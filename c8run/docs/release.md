# Release Process

Releases are owned by `@camunda/distribution` and follow a **build-once → promote** model:

- [`c8run-release-rc.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-release-rc.yaml)
  builds, signs, and notarizes the four OS artifacts **once** and stages them in Harbor as the release
  candidate (RC).
- [`c8run-release-public.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-release-public.yaml)
  promotes the **exact same bytes** from Harbor to the public targets — no rebuild, no re-signing.

The release train drives both workflows via BPMN; the `gh`/UI invocations below are for ad-hoc or
hotfix runs. The legacy single-stage
[`c8run-release.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-release.yaml)
remains available during the transition.

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

### Determine the versions to release

The easiest place to look up all updated component versions at once is the Renovate PR in
[camunda/camunda-distributions](https://github.com/camunda/camunda-distributions/pulls) labelled
`deps/docker-compose` and `version/8.x`. The table at the top of the PR body lists every version
change. The two versions the release workflows take as inputs are:

|        Input        |               Where to read it from                |
|---------------------|----------------------------------------------------|
| `camundaVersion`    | `camunda/camunda` row in the Renovate PR           |
| `connectorsVersion` | `camunda/connectors-bundle` row in the Renovate PR |

`camundaVersion` is the **full** version (e.g. `8.8.29`, `8.8.0-alpha4.1`) — it is the C8Run version.
The minor (e.g. `8.8`) is derived from it automatically; you do not enter it separately.

> **Docker Compose readiness (8.7/8.8 only).** For branches whose `c8run/.env` pins `COMPOSE_TAG`,
> the RC workflow **verifies** that the rolling `docker-compose-<minor>` release already pins the
> target `CAMUNDA_VERSION` and fails the build if it is behind. Make sure the Renovate PR above is
> merged (or the release train has refreshed the compose) before triggering the RC. C8Run cannot
> refresh the compose itself — it pins only 2 of Docker Compose's component versions. `main`/`stable/8.9`
> have no Docker Compose dependency and skip this check.

You can cross-check that no c8run artifacts have been published yet for the target release:

```bash
gh release view <version> --repo camunda/camunda --json assets --jq '.assets[].name' 2>/dev/null \
  | grep 'camunda8-run-' || echo "No c8run artifacts found (or release does not exist yet)"
```

If there is no output, the c8run release has not been done yet.

---

## Release Candidate

Build once, sign + notarize once, and stage the artifacts in Harbor. QA validates this exact RC; the
public release later promotes the **same bytes** without rebuilding.

There is **no manual `.env` version bump** — the workflow updates `c8run/.env` in place (preserving the
compose/Elasticsearch keys) and opens the `deps: update c8run versions to <version>` PR itself.

Trigger
[`c8run-release-rc.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-release-rc.yaml)
via the UI or the `gh` CLI:

```bash
gh workflow run c8run-release-rc.yaml \
  --repo camunda/camunda \
  --ref main \
  --field branch=stable/8.8 \
  --field camundaVersion=8.8.29 \
  --field connectorsVersion=8.8.15
```

|        Input        |                              Value                              |
|---------------------|-----------------------------------------------------------------|
| Run workflow from   | `Branch: main` (always)                                         |
| `branch`            | release branch, e.g. `stable/8.8` (`main` for the latest alpha) |
| `camundaVersion`    | full version, e.g. `8.8.29` or `8.8.0-alpha4.1`                 |
| `connectorsVersion` | full connectors version, e.g. `8.8.15`                          |

The workflow then:

1. (8.7/8.8) verifies the rolling `docker-compose-<minor>` is at the target version;
2. updates `c8run/.env` in place and builds + signs + notarizes the four OS artifacts;
3. pushes them to Harbor as `registry.camunda.cloud/team-distribution/c8run:<camundaVersion>-rc`;
4. opens the `c8run/.env` bump PR (merged after QA sign-off by the release train).

The RC lives **only in Harbor** — nothing is published to GitHub releases or the Download Center yet.
QA validates the artifact at the RC tag.

---

## Public Release

Promotes the staged RC — pulls the exact notarized bytes from Harbor and publishes them. **No rebuild,
no re-signing.** The Camunda apps GitHub release (tag `<camundaVersion>`) must already exist; it is
created by the monorepo release.

Trigger
[`c8run-release-public.yaml`](https://github.com/camunda/camunda/actions/workflows/c8run-release-public.yaml):

```bash
gh workflow run c8run-release-public.yaml \
  --repo camunda/camunda \
  --ref main \
  --field camundaVersion=8.8.29
```

|       Input       |                 Value                  |
|-------------------|----------------------------------------|
| Run workflow from | `Branch: main` (always)                |
| `camundaVersion`  | full version to promote, e.g. `8.8.29` |

The workflow pulls `c8run:<camundaVersion>-rc` from Harbor and:

1. uploads the artifacts to the Camunda apps GitHub release (tag `<camundaVersion>`);
2. uploads to the Download Center (own minor release + apps version);
3. tags Harbor `<camundaVersion>` for archival.

No `-rc` / `-pending-removal` cleanup is needed — the RC never lands on the GitHub release or the
Download Center, so there is nothing to delete. Report back in the release train Slack thread that the
C8Run release is complete.

