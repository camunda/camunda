# Keep Maven authoritative while evaluating a parallel Gradle build

**DRI**: Carlo Sana

**Status**: Proposed

**Purpose**: Define how an experimental Gradle build mirrors Maven and how CI keeps it usable
while it is evaluated. Maven remains the source of truth for module behavior,
dependency versions, and published or packaged output.

**Audience**: Monorepo DevOps, and engineers changing `pom.xml`, Gradle build files, or CI
workflows.

## Context

The monorepo builds with Maven. An experimental parallel Gradle build will be added for local
development and to evaluate whether Gradle is a viable long-term build tool. It will not be an
independent build definition at first. Both builds must work against the same source tree. A change
to Java sources, a POM, or Gradle build logic must not leave the other build unusable.

Maven remains authoritative during the evaluation. When the build systems differ, the Gradle build
must be brought into line with Maven rather than redefining the behavior in Gradle. Parity includes
the module graph, dependency scopes and versions, generated sources, resource processing, test-jar
usage, published metadata, and packaged artifacts.

The evaluation will determine whether maintaining both builds is light enough to continue or
whether Gradle should replace Maven. That choice requires a separate ADR. Until then, Gradle is not
used to publish artifacts to external repositories.

Gradle regressions should be visible in pull requests, and the Gradle checks should protect the
merge queue without replacing Maven's complete application-test path. Running both complete build paths for every pull
request would add too much time and cost during the evaluation, so the initial Gradle path uses
focused compilation and packaging checks.

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

### D3. Maven and Gradle changes are kept in sync

A Maven build change is not complete if it leaves the parallel Gradle build with a different
observable behavior. When a POM changes, the corresponding Gradle project, dependency configuration,
test configuration, generated-source setup, resource processing, or packaging must be updated when
needed. When Gradle build logic changes, it must be checked against the relevant module and parent
POMs.

Gradle configurations are chosen according to Maven's boundary and scope semantics, not by
mechanically translating every Maven compile dependency to `api()`. Optional Maven dependencies
must not leak to consumers. There is no universal Gradle equivalent: this repository uses
`compileOnly` plus `testImplementation` only when the dependency can safely be absent from the
declaring module's runtime classpath. Other modules must use a configuration that preserves the
required runtime behavior. Maven test JARs must remain consumable as Gradle test variants.
Published metadata and distribution contents must remain aligned with Maven.

### D4. Keep focused Gradle checks enabled in Unified CI

For every pull request, merge group, and protected-branch push that can affect build behavior,
Unified CI runs focused Gradle checks. They are part of the required CI path rather than an opt-in
signal. The `gradle-changes` filter triggers **Gradle production and test compilation** and validates
the **resulting distribution against Maven** when Java, Maven, Gradle, or relevant CI inputs change.

For this ADR, Gradle build inputs are:

- `*.gradle.kts` files;
- `gradle.properties`;
- `gradlew` and `gradlew.bat`;
- files below `gradle/`; and
- files below `buildSrc/`.

The focused checks target the most frequent sources of drift, such as dependency changes, newly
added modules, and changes to Java, Maven, Gradle, or relevant CI inputs. They cannot catch every
possible mismatch between Maven and Gradle. Maven remains the behavioral reference and the complete
application-test path.

The distribution check compares the versioned roots and bundled JAR inventories; byte-level
differences are expected. It distinguishes between non-blocking and blocking differences:

- **Non-blocking:** Numeric patch-only dependency version differences are reported for visibility
  but do not fail the build. Gradle generally selects the highest available version, while Maven
  selects the nearest dependency and uses declaration order to break ties. This temporary tolerance
  applies only while Gradle is used for local development and evaluation, not external publication.
- **Blocking:** Missing or extra JARs, distribution-root differences, and major or minor version
  differences fail the check.

Patch-version tolerance **must be reconsidered** before Gradle publishes external artifacts or becomes
a supported or authoritative build path.

The Gradle checks are included in Unified CI's `check-results` gate. They block relevant pull
requests and merge groups when Gradle compilation, packaging, or distribution parity fails, but
Maven test jobs must not depend on them. A Gradle failure must not skip, cancel, or make Maven tests
unavailable. Protected-branch pushes run the same checks for post-merge health and to warm the
shared Gradle cache; a push failure cannot prevent the commit that triggered it from having already
landed.

The focused Gradle compilation check verifies test-class compilation, not unit-test execution. Full
Gradle test execution is selected separately as described in D5 and D6.

### D5. Make the complete CI path selectable by build tool

Unified CI exposes a `build-tool` input that accepts `maven` or `gradle`. Selecting Maven runs the
complete Maven application-test path. Selecting Gradle runs the complete Gradle unit and integration
test path. This selection is separate from the focused Gradle checks in D4, which remain enabled for
relevant changes.

The Gradle selection is used when a change needs complete Gradle validation but does not modify Java
sources, Maven configuration, or shared CI. In particular, a change limited to Gradle build inputs
or Gradle-specific CI files can run the complete CI path with `build-tool: gradle` without also
running the Maven path.
This allows an engineer repairing a scheduled Gradle failure to validate the repair without paying
for unrelated Maven tests. Shared CI changes must still run the Maven path when they can affect it.

### D6. Run the complete Gradle CI path on a schedule

A scheduled workflow invokes Unified CI with `build-tool: gradle` and runs the complete Gradle unit
and integration test path against the default branch, beyond the focused compilation and packaging
checks. This reuses the same selectable build path that engineers use to validate fixes to Gradle
build or CI files. The exact cadence is an operational setting and can be adjusted without changing
this decision. A failure creates a CI incident for the owning team to triage and track until the
Gradle path is healthy again. The owning team must be assigned before this ADR is accepted.

The scheduled workflow does not replace Maven's application-test path.

## Deferred work

The following work is intentionally excluded from this change:

- **Changing the source of truth.** Maven remains authoritative until a future decision explicitly
  changes that arrangement.
- **Mandatory Gradle unit test check on every event.** Running all unit tests for every relevant
  change can increase CI cost; the scheduled test suites provide regular coverage while the
  event-scoped checks remain focused on compilation and packaging validation.
- **Post-merge Gradle repair automation.** Automated repair requires a confirmed ownership and
  alerting model.

## Alternatives considered

- **Let Gradle own its dependency versions.** Rejected: duplicated versions would allow the two
  builds to resolve different graphs and make parity failures difficult to diagnose.
- **Treat Gradle as an independent build definition.** Rejected: the project would have to maintain
  two sources of truth for modules, dependencies, generated sources, and packaging.
- **Run both complete build paths on every pull request.** Rejected for the initial evaluation
  because the additional Gradle test execution would add too much CI time and cost. This accepts
  that Gradle-specific test-execution failures may land and be detected by scheduled validation.
- **Run the full experimental Gradle test path on push or merge-group events.** Rejected: those
  contexts need a smaller, stable compilation signal rather than the larger experimental suite.

## Consequences

- Maven POMs remain the source for dependency versions and build behavior during the evaluation.
- Maintaining two build definitions adds work to Maven and Gradle build changes.
- The focused Gradle checks add CI time and cost and may block relevant pull requests or merge
  groups even though the build is experimental.
- Build-path selection avoids unnecessary Maven runs for Gradle-only changes, but the file filters
  must remain accurate so that shared changes do not skip required Maven or Gradle validation.
- Event-scoped checks do not run Gradle tests, so Gradle-specific test-execution failures may land
  and remain undetected until scheduled validation.
- Numeric patch differences in transitive dependency resolution are tolerated during the
  evaluation. Gradle must not publish external artifacts under this policy.
- Scheduled validation needs an owning team and creates incident-response work when it fails.
- Gradle changes require parity checks against Maven, and Maven build changes may require a matching
  Gradle update before they are complete.
- Distribution parity catches differences in the versioned archive root and bundled JAR set, but it
  does not compare archive contents byte for byte.
- Protected pushes provide post-merge Gradle health feedback and warm the shared cache.
- Maven application tests remain independently runnable when Gradle compilation fails.
- A future ADR must decide whether to keep the parallel build or replace Maven with Gradle before
  Gradle becomes a supported build or publishes external artifacts.

## Source

- [Gradle build pull request #52869](https://github.com/camunda/camunda/pull/52869) review
  discussion.
- [Gradle build evaluation issue #53297](https://github.com/camunda/camunda/issues/53297).

