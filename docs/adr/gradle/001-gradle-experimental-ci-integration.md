# Experimental Gradle build integration in CI

**DRI**: Carlo Sana

**Status**: Proposed

**Purpose**: Defines the minimum CI integration for the experimental Gradle build while keeping
Maven authoritative for application and Java changes. This ADR intentionally does not define the
future nightly validation or automated Gradle repair loop.

**Audience**: Monorepo DevOps, and any engineer changing `pom.xml`, Gradle build files, or CI
workflows.

## Context

The monorepo builds with Maven. A parallel Gradle build exists and can already run most of CI, but
it is not yet at parity and can drift whenever `pom.xml` files or Java sources change.

We want engineers to try Gradle and surface regressions early, while preserving one non-negotiable
rule: a pull request that changes Java or Maven build inputs must run the Maven tests. Gradle must
not replace Maven for such a pull request, and a Gradle failure must not prevent Maven tests from
running.

The only exception is a pull request whose complete change set consists exclusively of Gradle build
inputs. Such a pull request may use Gradle validation without running the Maven tests.

Unified CI runs on pull requests, pushes to protected branches, and merge queue events. Failures in
push and merge queue contexts are incident-tracked. The experimental Gradle path must therefore not
be allowed to introduce a Gradle failure into those contexts.

## Decision

**D1. Maven remains authoritative for code and Maven-build changes.**

For every pull request that changes Java sources or Maven build inputs, the Maven test jobs must
run. This includes pull requests that also change Gradle files. The Gradle result must never replace
those Maven tests, gate them, or be a prerequisite for them.

The only exception is a pull request whose complete change set consists exclusively of Gradle build
inputs. For this ADR, Gradle build inputs are:

- `*.gradle.kts` files;
- `gradle.properties`;
- `gradlew` and `gradlew.bat`;
- files below `gradle/`; and
- files below `buildSrc/`.

A pull request that mixes Gradle changes with Java, Maven, CI, documentation, or any other changes
is not a Gradle-only pull request.

**D2. Gradle full-test CI is an explicit PR-only opt-in.**

The `gradle-build` label may select the full Gradle test path only for a Gradle-only pull request.
On a pull request containing Java or Maven changes, the label must not switch CI away from Maven;
Maven remains mandatory there. In all non-PR contexts, including `push`, `merge_group`, and
`schedule`, the experimental Gradle full-test path is not selected by this ADR and Maven is used.

The Gradle-only exception is limited to the build tool used for that pull request. It does not make
Gradle a merge gate for other pull requests, and it does not change Maven's role as the authoritative
build for the repository.

**D3. Add a separate, non-blocking Gradle parity job for pull requests.**

A separate pull-request-only job runs `./gradlew testClasses` when a pull request changes Java
sources or Maven build inputs. It provides early feedback about Gradle parity without becoming a
prerequisite of any Maven job.

The job is non-blocking and is not run from `push`, `merge_group`, or `schedule` events. A failure
may be visible on the pull request, but it must not prevent the required Maven tests from executing
or allow a mixed Java/Maven pull request to omit them.

**D4. Build-tool selection defaults to Maven.**

Maven is the default build tool. The `gradle-build` label is the only opt-in for the full Gradle path,
and it is effective only when the pull request is Gradle-only. No nightly or dispatch-specific
build-tool override is introduced as part of this change.

**D5. Maven and Gradle jobs are independently gated.**

Maven test jobs must depend only on the normal Maven CI conditions and their required setup jobs.
They must not depend on the Gradle parity job or on a Gradle compile/cache-preparation job.

When Maven is required by D1, a Gradle failure must not skip, cancel, or make the Maven tests
unavailable. When a pull request is Gradle-only, the Maven-test exception applies; the full Gradle
path remains an explicit opt-in, while this parity job is intentionally scoped to Java and Maven
changes.

## Deferred work

The following work is intentionally excluded from this change and will be addressed after the
experimental Gradle integration has landed and stabilized:

- **Dedicated nightly Maven validation.** A separate, explicit nightly Maven job or workflow will
  be designed later. No new nightly workflow is added by this ADR's implementation.
- **Nightly or post-merge Gradle validation.** This requires a confirmed alerting model for scheduled
  Gradle failures and is deferred for now.
- **Automated Gradle repair.** Slack notifications, PR comments for post-merge failures, a queued
  single-writer fix loop, and automatic repair PRs are deferred. There is no automatic fix loop in
  this change.
- **Workflow-level build-tool overrides.** Explicit `workflow_call` and dispatch inputs can be added
  when the deferred nightly workflows need them.

## Alternatives considered

- **Use Gradle as the replacement for Maven on any labeled PR.** Rejected: a labeled PR may still
  change Java or Maven build inputs, in which case Maven tests are mandatory.
- **Make Maven test jobs depend on a shared Gradle compile job.** Rejected: a Gradle failure would
  be able to prevent Maven tests from running. The two paths must be independently gated.
- **Run the experimental Gradle path on `push` or `merge_group`.** Rejected: those contexts are
  incident-tracked, and Gradle is not yet reliable enough to add that failure surface.
- **Add nightly and post-merge automation in the same change.** Deferred: nightly validation and
  automated repair add operational complexity before the basic PR behavior is proven.
- **Run only a nightly Gradle parity check.** Rejected for the initial integration: it would leave
  Gradle regressions undetected until the next scheduled run. The PR-only parity job provides lower
  feedback latency without affecting Maven's gate.

## Consequences

- Maven tests are guaranteed for pull requests that change Java or Maven build inputs, including
  mixed Maven/Gradle pull requests.
- A Gradle-only pull request is the sole case where Maven tests may be omitted.
- The PR-only Gradle parity job can fail without blocking Maven tests or introducing Gradle failures
  into push and merge queue incident tracking.
- The Gradle-only path requires accurate change classification. A pull request must not be treated
  as Gradle-only when it contains any Java, Maven, CI, documentation, or other non-Gradle change.
- Gradle remains an experiment with deliberately limited operational automation. Nightly validation
  and automated repair will be separate follow-up work.

## Source

- Pull request #52869 review discussion and this conversation.
- The `gradle-build-parity` repository skill.

