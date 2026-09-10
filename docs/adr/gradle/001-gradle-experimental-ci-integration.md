# Experimental Gradle build integration in CI

**DRI**: Carlo Sana

**Status**: Proposed

**Purpose**: Defines the CI integration for the experimental Gradle build while keeping Maven
authoritative for application and Java changes. Relevant pull requests, merge groups, and protected
branch pushes must prove that Gradle can compile production and test sources. This ADR does not
make the full Gradle test path the default build path.

**Audience**: Monorepo DevOps, and any engineer changing `pom.xml`, Gradle build files, or CI
workflows.

## Context

The monorepo builds with Maven. A parallel Gradle build exists and can already run most of CI, but
it can drift whenever `pom.xml` files or Java sources change: most of those problems should surface as
compilation failures, but sometime they might make a test failing instead.

We want to surface Gradle regressions during pull requests and protect the merge queue without
replacing Maven's authoritative application tests. The Gradle compilation check is additive and
runs in Unified CI for pull requests, merge groups, and protected branch pushes. It compiles both
production and test sources with `./gradlew testClasses`.

The experimental Gradle application test path is selected automatically for pure Gradle-only pull
requests. Other non-Java, non-Maven pull requests can opt in through the `gradle-build` label,
allowing CI-only changes to exercise the Gradle path without allowing Java, Maven-build,
merge-queue, or push runs to select Gradle. Unlabeled and non-PR runs continue to use Maven.

## Decision

**D1. Maven remains the default and the landing-gate authority.**

Maven remains the authoritative application test path for unlabeled pull requests, pull requests
with Java or Maven-build changes, merge groups, and pushes. Pure Gradle-only pull requests select the
experimental Gradle application path automatically; other non-Java, non-Maven pull requests may opt
in with the `gradle-build` label. This does not change the Maven requirement for Java or Maven-build
changes or the merge queue, which always selects Maven.

The Gradle compilation check also runs for pull requests that change Gradle build inputs. For this
ADR, Gradle build inputs are:

- `*.gradle.kts` files;
- `gradle.properties`;
- `gradlew` and `gradlew.bat`;
- files below `gradle/`; and
- files below `buildSrc/`.

**D2. Gradle application tests are selected only for gradle only pull requests**

A pure Gradle-only pull request selects the Gradle application test path automatically. Other
non-Java, non-Maven pull requests can select it with the `gradle-build` label, making it possible to
exercise Gradle when the pull request changes CI configuration rather than Java sources or Maven
build inputs. Any Java or Maven-build change forces Maven, regardless of the label.

In non-PR contexts, including `push` and `merge_group`, and on unlabeled, Java, or Maven-build pull
requests, the build tool remains Maven. The Gradle compilation check described in D3 is still run in
those contexts when its change filters match.

**D3. Add a relevant Gradle compilation check to Unified CI.**

The `Gradle / Test Classes` job runs when the change set contains Java/resource sources, Maven build
inputs, Gradle build inputs, or CI inputs that can change build behavior. It runs `testClasses` and
`:camunda-zeebe:distZip` in the same Gradle invocation so the distribution reuses the compilation
outputs:

```text
./gradlew --no-daemon --console=plain --parallel -Pskip.fe.build testClasses :camunda-zeebe:distZip
```

The job uploads the Gradle ZIP as artifact. A separate `Gradle / Distribution Parity` job waits for both this
job and `build-distball`, downloads the Gradle and Maven ZIPs, and compares their versioned roots
and bundled JAR names and versions. Other file paths and byte contents are not compared. In general, the jar bytes
differ (for example manifest metadata)

Both jobs are included in Unified CI's `check-results` gate. They therefore block relevant pull
requests and merge groups when Gradle compilation, packaging, or distribution parity fails.
Protected branch pushes run the same checks to provide post-merge health feedback and warm the
shared Gradle cache; a push failure cannot prevent the commit that triggered it from having already
landed.

**D4. Build-tool selection defaults to Maven outside labeled pull requests.**

Maven is the default build tool. Pure Gradle-only pull requests select Gradle automatically; other
non-Java, non-Maven pull requests need the `gradle-build` label. Pushes, merge groups, schedules,
manually dispatched workflows, unlabeled pull requests, and all pull requests with Java or Maven
build changes use Maven for the application test path.

**D5. The Gradle compilation check and Maven jobs are independently gated.**

The Gradle compilation job is a member of the Unified CI result gate, but Maven test jobs must not
depend on it. A Gradle compilation failure may fail `check-results` and prevent a merge-group
landing, but it must not skip, cancel, or make Maven tests unavailable.

**D6. Configure per-test retries in the shared Gradle test convention.**

`buildlogic.java-conventions` applies the Gradle Test Retry plugin to every Gradle `Test` task. The
plugin reads the Gradle property `test.max.retries`; when the property is absent, its default is
`0`, so local Gradle runs do not retry tests implicitly.

CI maps the existing `FLAKY_TEST_RERUN_COUNT` environment value to the Gradle property on each
Gradle test invocation:

```text
-Ptest.max.retries=${FLAKY_TEST_RERUN_COUNT}
```

Gradle therefore uses the same retry budget as Maven without reading the CI environment directly.
The retry plugin reruns individual failed test cases. CI must not wrap the complete Gradle task in
an additional retry loop, since that would apply a different and potentially multiplied retry
policy.

**D7. Migrate the remaining Unified CI database suites to Gradle.**

The standard database, history, identity, physical-tenant acceptance, physical-tenant identity,
physical-tenant history, and RDBMS integration jobs use dedicated Gradle test tasks when the Gradle
path is selected: `itMultiDb`, `itHistory`, `itIdentity`, `itPhysicalTenant`,
`itPhysicalTenantIdentity`, `itPhysicalTenantHistory`, and `itRdbms`. Maven remains the default for
all other contexts. Standalone database workflows outside Unified CI continue to use Maven until
they are explicitly migrated.

Unified CI keeps `build-distball` in the static dependency list for these jobs because Maven runs
still consume the shared Maven artifacts. Gradle runs do not consume the distball; when the Gradle-only
path is selected, the distball job is skipped and `always()` allows the Gradle job to proceed.
Splitting the dependency graph into separate Maven and Gradle jobs is intentionally deferred to keep
this workflow structure simple.

## Deferred work

The following work is intentionally excluded from this change:

- **Nightly Gradle validation.** A scheduled Gradle test run requires a confirmed alerting model.
- **Automated Gradle repair.** Repair automation will be designed after the CI signal is stable.

## Alternatives considered

- **Run the Gradle compilation check only for Java changes.** Rejected: Gradle-only and Maven-only
  build changes can independently break Gradle compilation and must also be covered.
- **Make Maven test jobs depend on the Gradle compilation job.** Rejected: a Gradle failure must
  not prevent Maven tests from running.
- **Run the full experimental Gradle test path on push or merge-group events.** Rejected: those
  contexts need a smaller, stable compilation signal rather than the larger experimental suite.
- **Add nightly validation or post-merge Gradle repair automation now.** Deferred until the PR and
  merge-group signals and their ownership model are stable.

## Consequences

- Relevant pull requests receive direct Gradle production-and-test compilation feedback.
- Merge groups are blocked when relevant Gradle production or test sources do not compile.
- Protected pushes provide post-merge Gradle health feedback and warm the shared cache.
- Maven remains the authoritative application test path for Java and Maven changes.
- Pure Gradle-only PRs select the experimental Gradle application path automatically; other
  non-Java, non-Maven PRs need the label for mixed or CI-only changes. Java or Maven-build changes,
  merge-queue runs, and push runs always use Maven.
- Maven jobs remain independently runnable when the Gradle compilation job fails.
- Gradle and Maven use the same CI retry budget, while local Gradle runs remain retry-disabled by
  default.
- Nightly parity and automated repair remain deliberate follow-up work.

## Source

- Pull request #52869 review discussion and this conversation.
- The `gradle-build-parity` repository skill.

