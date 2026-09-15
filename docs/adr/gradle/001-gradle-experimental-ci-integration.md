# Experimental Gradle build and CI integration

**DRI**: Carlo Sana

**Status**: Proposed

**Purpose**: Define how the parallel Gradle build coexists with Maven and how CI exercises it
without allowing the two build systems to drift. Maven is the source of truth for now, including
module behavior, dependency versions, and published or packaged output.

**Audience**: Monorepo DevOps, and engineers changing `pom.xml`, Gradle build files, or CI
workflows.

## Context

The monorepo builds with Maven. A parallel Gradle build will be added so that Gradle can eventually serve
as an alternative build path, but it will not be an independent build definition at first. Both builds must work
against the same source tree at the same time. A change to Java sources, a POM, or Gradle build
logic must not leave the other build unusable.

Maven remains authoritative for the moment. When the build systems differ, the Gradle build must
be brought into line with Maven rather than redefining the behavior in Gradle. Parity includes the
module graph, dependency scopes and versions, generated sources, resource processing, test-jar
usage, published metadata, and packaged artifacts.

We also want Gradle regressions to be visible in pull requests and to protect the merge queue
without replacing Maven's authoritative application tests. To avoid running the Maven CI and
the Gradle CI for every PR (because of costs) we will choose some checks that we can run in Gradle
so that we catch as many issues as possible with quick checks.


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

### D4. Maven remains the CI application-test and landing-gate authority

Maven runs the application-test path for every event. The experimental Gradle path does not select
or replace application tests; it is an additive compilation and distribution-parity signal. The
`gradle-changes` filter independently triggers the Gradle `testClasses` and distribution-parity
jobs when Gradle, Maven, Java, or relevant CI inputs change.

These checks cannot catch every possible mismatch between Maven and Gradle, but they are intended
to catch the most frequent sources of drift, such as dependency changes, newly added modules, and
changes to Java, Maven, Gradle, or relevant CI inputs. Maven remains the application-test and
landing-gate authority.

For this ADR, Gradle build inputs are:

- `*.gradle.kts` files;
- `gradle.properties`;
- `gradlew` and `gradlew.bat`;
- files below `gradle/`; and
- files below `buildSrc/`.

### D5. Add relevant Gradle compilation and distribution checks to Unified CI

For changes that can affect build behavior, Unified CI runs Gradle production and test compilation
and validates the resulting distribution against Maven. The distribution check compares the
versioned roots and bundled JAR inventories; byte-level differences are expected. It distinguishes
between non-blocking and blocking differences:

- **Non-blocking:** Numeric patch-only dependency version differences are reported for visibility
  but do not fail the build. This tolerance accounts for the different transitive-dependency
  conflict-resolution algorithms: Gradle selects the highest available version, while Maven uses
  the first version encountered in dependency resolution order.
- **Blocking:** Missing or extra JARs, distribution-root differences, and major or minor version
  differences fail the check.

These checks are included in Unified CI's `check-results` gate. They block relevant pull requests
and merge groups when Gradle compilation, packaging, or distribution parity fails. Protected-branch
pushes run the same checks for post-merge health and to warm the shared Gradle cache; a push failure
cannot prevent the commit that triggered it from having already landed.

The Gradle compilation check verifies test-class compilation, not unit-test execution. Full Gradle
test execution is handled by the scheduled validation described in D7.

### D6. Keep the Gradle and Maven jobs independently gated

The Gradle compilation job is a member of the Unified CI result gate, but Maven test jobs must not
depend on it. A Gradle failure may fail `check-results` and prevent a merge-group landing, but it
must not skip, cancel, or make Maven tests unavailable.

### D7. Run the full Gradle CI test suite on a schedule

A scheduled workflow runs the full Gradle CI test suite. Its purpose is to exercise Gradle test
execution continuously beyond the event-scoped checks used for pull-request compilation and
packaging validation. The scheduled workflow does not replace Maven's application-test path or
change the CI landing-gate authority.

## Deferred work

The following work is intentionally excluded from this change:

- **Changing the source of truth.** Maven remains authoritative until a future decision explicitly
  changes that arrangement.
- **Mandatory Gradle unit test check on every event.** Running all unit tests for every relevant
  change can increase CI cost; the scheduled full test suite provides continuous test coverage
  while the event-scoped checks remain focused on compilation and packaging validation.
- **Post-merge Gradle repair automation.** Automated repair requires a confirmed ownership and
  alerting model.

## Alternatives considered

- **Let Gradle own its dependency versions.** Rejected: duplicated versions would allow the two
  builds to resolve different graphs and make parity failures difficult to diagnose.
- **Treat Gradle as an independent build definition.** Rejected: the project would have to maintain
  two sources of truth for modules, dependencies, generated sources, and packaging.
- **Run both complete build paths on every pull request.** Rejected: this doubles CI cost without
  improving the landing guarantee; the merge queue already revalidates the result with Maven.
- **Run the full experimental Gradle test path on push or merge-group events.** Rejected: those
  contexts need a smaller, stable compilation signal rather than the larger experimental suite.

## Consequences

- Maven POMs remain the single source for dependency versions and build behavior for now.
- Maven and Gradle can be used concurrently against the same source tree without intentionally
  diverging module, dependency, test, or distribution behavior.
- Gradle changes require parity checks against Maven, and Maven build changes may require a matching
  Gradle update before they are complete.
- Relevant pull requests receive direct Gradle production-and-test compilation feedback.
- Distribution parity catches differences in the versioned archive root and bundled JAR set.
- Merge groups are blocked when relevant Gradle compilation, packaging, or parity checks fail.
- A scheduled full Gradle CI test run keeps Gradle test execution continuously exercised.
- Protected pushes provide post-merge Gradle health feedback and warm the shared cache.
- Maven application tests remain authoritative for Java and Maven changes and remain independently
  runnable when Gradle compilation fails.

## Source

- Pull request #52869 review discussion and this conversation.
- The `gradle-build-parity` repository skill.

