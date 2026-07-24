---
name: gradle-build-parity
description: Use when editing, fixing, or debugging the Gradle build in the Camunda monorepo (build.gradle.kts, settings.gradle.kts, buildSrc/, buildlogic conventions) — especially for missing dependencies, "cannot find symbol" across modules, test-jar wiring, optional dependencies, published-POM parity, or any Gradle vs Maven behavioral difference.
---

# Gradle Build Parity

## Overview

This branch builds a Gradle build for the Camunda monorepo as a parallel path to Maven.
**Maven is the source of truth.** The job is to make Gradle match Maven behavior for the
active modules, not to redesign the build.

**Core principle:** When Gradle and Maven differ, assume Maven is correct until proven otherwise.

Match Maven behavior for: dependency graphs, generated sources, resource processing, test-jar
usage, published metadata, packaged artifacts.

## CI Build-Tool Selection (why Maven stays the gate)

`ci.yml` picks the build tool via a single `detect-changes` output:

```yaml
build-tool: ${{ (github.event_name == 'pull_request' && contains(github.event.pull_request.labels.*.name, 'gradle-build')) && 'gradle' || 'maven' }}
```

`gradle` is chosen **only** on `pull_request` events with the `gradle-build` label. Every other
trigger falls to `maven`:

- `push` (main, `stable/*`, `release-*`) → `maven`
- `merge_group` (merge queue) → `maven` (event is not `pull_request`)
- labeled PR → `gradle`

**Gradle mode replaces Maven only in the PR check, never at the landing gate.** main uses a
required merge queue (ruleset `unified-ci-merges-main-branch`: `merge_queue` rule + required
check `check-results`). A gradle-labeled PR still gets Maven-validated in its `merge_group` run
before it lands. If Maven fails there, the PR is kicked from the queue and never reaches main —
so "something that doesn't build with Maven lands on main" **cannot happen** through the queue.
`push` to main/stable is a second Maven gate.

**Do not make `build-tool` a list/set** (run both on every PR). It doubles PR CI for zero
integrity gain — the merge queue already re-validates with Maven.

**Residual gap:** a gradle-labeled PR shows green on the PR even if Maven would fail; the dev
only finds out at merge-queue time (wasted queue entry, surprise — main never breaks). If that
DX matters, the fix is *not* running both — it is scoping *when* the label may replace Maven:
restrict `gradle-build` to PRs touching only Gradle build files
(`build.gradle.kts`, `settings.gradle.kts`, `buildSrc/`); if a labeled PR also changes Java/pom,
force Maven (or run both) so the PR reflects the landing gate.

## Fix Workflow

1. Reproduce the Gradle gap (build/verify the affected module).
2. Compare against the relevant Maven `pom.xml` (module-local + `parent/pom.xml`).
3. Patch the Gradle build with a minimal, module-scoped change.
4. Verify the affected module only — no full-repo experimentation.
5. Preserve already-fixed conventions; avoid regressions.

**Before investigating CI failures: always rebase onto main first.** CI runs on the merge
commit, so Gradle build files apply against main's Java sources, which may differ from the
branch's local sources.

## Pom Changes That Break Gradle

When a Gradle failure follows a pom-only commit, check
[references/pom-change-failure-modes.md](references/pom-change-failure-modes.md) first — catalog
of pom edit categories (missing internal/external deps, wrong `api`/`implementation`, scope-
ordering quirks, exclusions, optional/test-jar wiring, version/BOM skew, surefire↔Gradle test
config drift, codegen input changes) and CI-shaped failures (concurrency misconfig, unmappable-
character test-report filenames).

## Known Gradle vs Maven Differences

For replicating Maven **build-time plugins** (code generation, resource templating, per-module
version pinning) see [references/maven-plugin-equivalents.md](references/maven-plugin-equivalents.md):
config-cache-safe pom reads, `templating-maven-plugin` → `Sync`+`ReplaceTokens`,
`openapi-generator` multi-spec `GenerateTask`, Spring version pinning, SBE tool isolation.

### Maven `<optional>true</optional>` → Gradle `compileOnly` + `testImplementation`

Gradle has no direct optional-dependency equivalent. Repo pattern:
`compileOnly(dep) + testImplementation(dep)`. It approximates Maven optional but differs:

|          Scope           | Maven `optional` | Gradle `compileOnly` + `testImplementation` |
|--------------------------|------------------|---------------------------------------------|
| Declaring module compile | ✓                | ✓                                           |
| Declaring module runtime | ✓                | ✗                                           |
| Declaring module tests   | ✓                | ✓ (via explicit `testImplementation`)       |
| Consumer compile         | ✗                | ✗                                           |
| Consumer runtime         | ✗                | ✗                                           |

The declaring module's **runtime** classpath lacks the `compileOnly` dep. Safe when the dep is
used only behind Spring Boot `@ConditionalOnClass` — the ASM condition evaluates false and the
config class never loads.

**`api()` leaks optional deps:** Gradle `api()` always propagates to consumers' compile AND
runtime classpaths, unlike Maven optional which stops at consumers. If a module declares an
optional dep as `api`, consumers get it transitively in Gradle but not Maven — silently
activating Spring Boot conditional config that should be inactive. Fix: `api(dep)` →
`compileOnly(dep) + testImplementation(dep)`, and list the dep in the module's
`OptionalDependenciesPomAction` for published-POM parity.

**True Gradle equivalent:** [feature variants](https://docs.gradle.org/current/userguide/how_to_create_feature_variants_of_a_library.html#feature_variants)
give full compile/runtime control without the `compileOnly` runtime gap. Repo uses the pragmatic
approximation; migrate to feature variants only if finer control is needed.

**Fixed so far:** `clients/java` (`micrometer-core`, `micrometer-commons`),
`clients/camunda-spring-boot-starter` (`micrometer-core`).

### Maven `test-jar` → Gradle `configuration = "tests"`

Maven modules with `<goal>test-jar</goal>` in `maven-jar-plugin` expose test classes as a
`*-tests.jar`. In Gradle this needs both sides:

**Producer** applies the convention plugin (creates `tests` configuration + `testsJar` artifact):

```kotlin
plugins {
  id("buildlogic.test-jar-conventions")
}
```

**Consumer** adds both main jar and test jar:

```kotlin
testImplementation(project(":some-module"))
testImplementation(project(":some-module", configuration = "tests"))
```

**Diagnose:** if compilation fails with `cannot find symbol` for a class under another module's
`src/test/java`, check whether that module's `build.gradle.kts` applies `test-jar-conventions`.
If not, add it, then add the `configuration = "tests"` dep on the consumer.

### No free versions in the Gradle build

Every dependency version must come from the version catalog (`libs`), never hardcoded in a
`build.gradle.kts`. The only exception is Gradle plugins themselves. The catalog is built in
code in `settings.gradle.kts`; versions are sourced from Maven via `pomVersion("version.X")`,
which reads `parent/pom.xml` `<properties>` — Maven stays the single source of truth.

When a Maven version lives **inline** (e.g. a plugin `<version>2.2.0</version>` in a module
pom, not a property), promote it to a `<properties>` entry in `parent/pom.xml` and reference
it via `${version.X}` in the module pom. Promoting an inline version to a parent property is a
standard Maven refactor, not a redesign — it gives the catalog a `pomVersion` source. Then add
`version("X", pomVersion("version.X"))` + a `library(...)` entry to the catalog and reference
`libs...` from the build. If a version genuinely has no Maven source, a hardcoded catalog entry
with a comment explaining its origin is acceptable (precedent: `aspectjweaver`).

**Catalog accessors work inside `buildscript {}`** on Gradle 9.5 — a buildscript classpath dep
can use `classpath(libs.some.lib)` instead of a hardcoded coordinate (verified on
`zeebe/protocol-asserts`, whose generator lib runs on the buildscript classpath).

### Maven surefire/failsafe ↔ Gradle test/it mapping

The current Gradle test-task contract mirrors Maven's split on the shared `src/test` source set:

- Maven **Surefire** (unit tests) ↔ Gradle **`test`**
- Maven **Failsafe** (integration tests) ↔ Gradle **`it`**
- Gradle **`check`** depends on **`it`**

`test` excludes the standard IT class-name patterns (`IT*`, `*IT`, `*ITCase`); `it` includes
those patterns. Do **not** reintroduce a custom `ut` task or make `test` an empty lifecycle task
that just depends on other test tasks — that breaks native Gradle test filtering such as
`test --tests ...`.

Optimize's owner-aligned suite tasks follow the same naming shift:

- `testCoreFeatures`
- `testDataLayer`

If you are comparing CI behavior, expect Gradle unit-test jobs to invoke `test`, not `ut`.

## Constraints

- Treat Maven behavior as the reference. Check the relevant `pom.xml` before assuming a Gradle
  dependency or task is wrong or missing.
- Prefer minimal, module-scoped Gradle fixes over broad refactors.
- Configuration cache is enabled — keep build logic compatible with it.
- Frontend builds may be skipped during backend parity work with `-Pskip.fe.build=true` when
  appropriate; frontend parity still matters.

## Tools

### `compare-module-deps.py` — per-module dependency diff

Compares the resolved dependencies of one module between Gradle and Maven. Use it to
confirm a parity fix, or to diagnose a suspected dependency gap.

```bash
python scripts/compare-module-deps.py <gradle-project> [--scope runtime|compile|test] [--versions]
python scripts/compare-module-deps.py --dir clients/java      # resolve project from its dir
python scripts/compare-module-deps.py --list                  # gradle-project -> dir map
```

It reports, per module:
- **third-party deps** — diffed by `group:artifact` (BOM/platform imports filtered out).
- **internal deps** — Maven `io.camunda:*` reactor modules vs Gradle `project :...`,
  diffed by name. This relies on the convention **Gradle project name == Maven
  artifactId**. If they diverge, fix the Gradle project name — the tool flags it as a
  false diff, which is itself a signal.

Note: not every `io.camunda:*` artifact is a reactor module — some are separately-released
libs (e.g. `camunda-security-library-*`, an alpha-versioned dependency). The tool
classifies by the reactor project-name set from `settings.gradle.kts`: reactor artifacts
are internal, all other `io.camunda:*` coords are ordinary third-party deps.

Scope maps Maven scope → Gradle configuration: `runtime`→`runtimeClasspath`,
`compile`→`compileClasspath`, `test`→`testRuntimeClasspath`. Exit code `2` on any diff.

**Before treating a MISSING/EXTRA as a real gap, confirm the transitive path.** A "MISSING
in Gradle" can be a Maven scope-resolution quirk rather than a build gap. Maven normally
narrows a transitive reached through a **test**-scoped path down to `test`, but it fails to
narrow **classifier-variant** artifacts: their nodes keep their declared `runtime` scope
even though their (non-classifier) parent was correctly narrowed to `test`. So
`-DincludeScope=runtime` wrongly lists them. Seen with
`io.netty:netty-tcnative-boringssl-static` (reached via `zeebe-test-util` → `camunda-client-java`):
the base and `netty-tcnative-classes` resolve to `test`, but the 5 platform-classifier jars
resolve to `runtime`. Gradle narrows correctly, keeping them on `testRuntimeClasspath` only.
Verify with `./mvnw dependency:list -pl <dir>` (inspect the per-artifact scope column) plus
`./mvnw dependency:tree -pl <dir> -Dincludes=<group>:<artifact>`, and check the Gradle
`testRuntimeClasspath` before changing the build.

Single-module only by design: it launches one Maven + one Gradle invocation per run.
A repo-wide `--all` was tried and dropped — 146 modules × 2 tools is too slow for CI,
and bulk single-JVM resolution hit Gradle 9 walls (config-phase resolution locks,
config-cache `Task.project` restrictions, per-project resolution locks). Not worth the
complexity for the parity payoff; run it per module on the module you're fixing.

### `compare-dist.py` — packaged distribution JAR diff

Compares the JARs bundled in the final distribution produced by the `dist/` project between
Gradle and Maven. `compare-module-deps.py` diffs a single module's resolved classpath;
`compare-dist.py` diffs the actual `lib/` contents of the shipped distribution — the
end-to-end artifact parity check.

```bash
python3 .claude/skills/gradle-build-parity/compare-dist.py <gradle-tar.gz> <maven-dist-dir>

# Example: build both dists, then compare
python3 .claude/skills/gradle-build-parity/compare-dist.py \
    dist/build/distributions/camunda-zeebe-*.tar.gz \
    dist/target/camunda-zeebe
```

- **Gradle side** — reads JARs under `*/lib/` inside the `distTar` `.tar.gz`.
- **Maven side** — reads JARs under `lib/*.jar` in the unpacked Maven dist directory.

Artifacts are matched by version-stripped name, so it reports three classes of diff:
**version mismatches** (same artifact, different version bundled), **Gradle-only**, and
**Maven-only** JARs. Use it after a module-level fix to confirm the change actually lands in
the shipped distribution, and to catch packaging gaps that per-module classpath diffs miss
(e.g. a dep present on a classpath but excluded from the assembly).

## Reference Files

- `parent/pom.xml` and module-local `pom.xml` files — authoritative for versions, deps, code
  generation, exclusions, packaging, publication
- `settings.gradle.kts` — module registration
- `buildSrc/` — convention plugins (`buildlogic.*`)
