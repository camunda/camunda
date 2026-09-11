# Experimental Gradle build and CI integration

**DRI**: Carlo Sana

**Status**: Proposed

**Purpose**: Define how the parallel Gradle build coexists with Maven and how CI exercises it
without allowing the two build systems to drift. Maven is the source of truth for now, including
module behavior, dependency versions, and published or packaged output.

**Audience**: Monorepo DevOps, and engineers changing `pom.xml`, Gradle build files, or CI
workflows.

## Context

The monorepo builds with Maven. A parallel Gradle build exists so that Gradle can eventually serve
as an alternative build path, but it is not an independent build definition. Both builds must work
against the same source tree at the same time. A change to Java sources, a POM, or Gradle build
logic must not leave the other build unusable.

Maven remains authoritative for the moment. When the build systems differ, the Gradle build must
be brought into line with Maven rather than redefining the behavior in Gradle. Parity includes the
module graph, dependency scopes and versions, generated sources, resource processing, test-jar
usage, published metadata, and packaged artifacts.

We also want Gradle regressions to be visible in pull requests and to protect the merge queue
without replacing Maven's authoritative application tests. The Gradle compilation check is
additive: it compiles production and test sources with `./gradlew testClasses` and checks
distribution packaging and dependency parity.

## Decision

### D1. Maven is the source of truth for the parallel build

Gradle mirrors Maven's active modules and behavior. Maven POMs and their associated build plugins
are the reference when adding or changing Gradle configuration. Gradle build logic must preserve
Maven's observable dependency, compilation, test, publication, and packaging behavior rather than
introducing Gradle-specific alternatives.

This is a coexistence decision, not a decision that Maven must remain the long-term build tool. A
future change may establish another source of truth, but it must be recorded explicitly; until then,
Maven remains authoritative.

### D2. Gradle must not define independent library versions

Gradle must never contain a free-standing version for a library, tool, BOM, buildscript dependency,
convention-plugin dependency, or version-catalog entry. Versions must come from Maven or be omitted
when an already-represented BOM manages them.

The Gradle version catalog is generated from Maven properties through `settings.gradle.kts` and
`pomVersion(...)`. When Maven currently declares a version inline in a module POM, the version is
first promoted to a Maven property; the Gradle catalog then resolves that property instead of
hardcoding a second copy. Maven remains the single version source, and the catalog is only a Gradle
view of it.

The only exception is a Gradle plugin version when the Gradle plugin mechanism cannot consume the
Maven-sourced version. This exception does not apply to libraries used by that plugin or to ordinary
buildscript dependencies; those must still use Maven-sourced catalog versions.

### D3. Maven and Gradle changes are kept in parity

A Maven build change is not complete if it leaves the parallel Gradle build with a different
observable behavior. When a POM changes, the corresponding Gradle project, dependency configuration,
test configuration, generated-source setup, resource processing, or packaging must be updated when
needed. When Gradle build logic changes, it must be checked against the relevant module and parent
POMs.

Gradle configurations are chosen according to Maven's boundary and scope semantics, not by
mechanically translating every Maven compile dependency to `api()`. Optional Maven dependencies
must not leak to consumers; the Gradle equivalent is `compileOnly` plus explicit test coverage when
appropriate. Maven test JARs must remain consumable as Gradle test variants. Published metadata and
distribution contents must remain aligned with Maven.

### D4. Maven remains the CI landing-gate authority

Maven remains the authoritative application-test path except for eligible pull requests. Build-tool
selection is:

| Context | Application-test path |
| --- | --- |
| Pure Gradle-only pull request | Gradle automatically |
| Other non-Java, non-Maven pull request with the `gradle-build` label | Gradle |
| Unlabeled pull request, or any pull request with Java or Maven-build changes | Maven |
| Merge group, protected-branch push, schedule, or manual run | Maven |

A label cannot select Gradle when the pull request changes Java sources or Maven build inputs. The
merge queue and protected pushes therefore always use Maven for application tests. This does not
make the builds independent: Gradle-only changes are exercised by Gradle before the merge queue,
and the Maven merge-group run validates that the result still works with Maven before landing.

For this ADR, Gradle build inputs are:

- `*.gradle.kts` files;
- `gradle.properties`;
- `gradlew` and `gradlew.bat`;
- files below `gradle/`; and
- files below `buildSrc/`.

### D5. Add a relevant Gradle compilation and distribution check to Unified CI

The `Gradle / Test Classes` job runs when the change set contains Java or resource sources, Maven
build inputs, Gradle build inputs, or CI inputs that can change build behavior. It runs
`testClasses` and `:camunda-zeebe:distZip` together so the distribution reuses the compilation
outputs:

```text
./gradlew --no-daemon --console=plain --parallel -Pskip.fe.build testClasses :camunda-zeebe:distZip
```

The job uploads the Gradle ZIP as an artifact. A separate `Gradle / Distribution Parity` job waits
for this job and `build-distball`, downloads the Gradle and Maven ZIPs, and compares their versioned
roots and bundled JAR names and versions. Other file paths and byte contents are not compared; the
JAR bytes generally differ, for example because of manifest metadata.

Both jobs are included in Unified CI's `check-results` gate. They block relevant pull requests and
merge groups when Gradle compilation, packaging, or distribution parity fails. Protected-branch
pushes run the same checks for post-merge health and to warm the shared Gradle cache; a push failure
cannot prevent the commit that triggered it from having already landed.

Note that the comparison of the tarball does not guarantee that each module contain the same exact
dependencies, altough it's likely to happen (mostly because the modules compile).

The `gradle testClasses` verifies only compilation of test classes, not the execution of the unit tests:
there might be some situations where a change in java/maven requires a change in gradle that it's not catched
by CI.

### D6. Keep the Gradle and Maven jobs independently gated

The Gradle compilation job is a member of the Unified CI result gate, but Maven test jobs must not
depend on it. A Gradle failure may fail `check-results` and prevent a merge-group landing, but it
must not skip, cancel, or make Maven tests unavailable.


## Deferred work

The following work is intentionally excluded from this change:

- **Nightly/Scheduled Gradle validation.** A scheduled Gradle test run requires a confirmed alerting model.
- **Changing the source of truth.** Maven remains authoritative until a future decision explicitly
  changes that arrangement.
- **Mandatory Gradle unit test check** Running all unit tests with gradle is under discussion as it can
  prevent some regressions with limited CI time required, especially if tests results are cached.
  We might select a subset of "fast" tests only (for example excluding randomized tests).

## Alternatives considered

- **Let Gradle own its dependency versions.** Rejected: duplicated versions would allow the two
  builds to resolve different graphs and make parity failures difficult to diagnose.
- **Treat Gradle as an independent build definition.** Rejected: the project would have to maintain
  two sources of truth for modules, dependencies, generated sources, and packaging.
- **Run both complete build paths on every pull request.** Rejected: this doubles CI cost without
  improving the landing guarantee; the merge queue already revalidates Gradle-labeled changes with
  Maven.
- **Run the Gradle compilation check only for Java changes.** Rejected: Gradle-only and Maven-only
  build changes can independently break Gradle compilation and must also be covered.
- **Make Maven test jobs depend on the Gradle compilation job.** Rejected: a Gradle failure must
  not prevent Maven tests from running.
- **Run the full experimental Gradle test path on push or merge-group events.** Rejected: those
  contexts need a smaller, stable compilation signal rather than the larger experimental suite.
- **Add nightly validation or post-merge Gradle repair automation now.** Deferred until the PR and
  merge-group signals and their ownership model are stable.

## Consequences

- Maven POMs remain the single source for dependency versions and build behavior for now.
- Maven and Gradle can be used concurrently against the same source tree without intentionally
  diverging module, dependency, test, or distribution behavior.
- Gradle changes require parity checks against Maven, and Maven build changes may require a matching
  Gradle update before they are complete.
- Relevant pull requests receive direct Gradle production-and-test compilation feedback.
- Distribution parity catches differences in the versioned archive root and bundled JAR set.
- Merge groups are blocked when relevant Gradle compilation, packaging, or parity checks fail.
- Protected pushes provide post-merge Gradle health feedback and warm the shared cache.
- Maven application tests remain authoritative for Java and Maven changes and remain independently
  runnable when Gradle compilation fails.

## Source

- Pull request #52869 review discussion and this conversation.
- The `gradle-build-parity` repository skill.
