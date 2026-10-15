# Experimental Gradle build

The Gradle build is an experimental parallel path for local development and parity checks. **Maven
remains authoritative** for module behavior, dependency versions, tests, publication, and distribution
contents. Gradle publication is not authorized. When behavior differs, use Maven as the reference.

The focused Gradle CI jobs compile production and test classes, package the distribution, and compare
its JAR inventory with Maven. They do not run the full Gradle application-test suite. Maven remains
the application-test and landing-gate path; see
[ADR 001](../docs/adr/gradle/001-gradle-experimental-ci-integration.md).

Run all commands below from the repository root.

## Compile and test

```bash
# Compile production and test classes in the active Gradle projects
./gradlew -Pskip.fe.build=true testClasses

# Compile test sources for one project
./gradlew -Pskip.fe.build=true :camunda-search-client:compileTestJava

# Run unit tests (the Surefire equivalent)
./gradlew -Pskip.fe.build=true :camunda-search-client:test

# Run integration tests (the Failsafe equivalent)
./gradlew -Pskip.fe.build=true :camunda-search-client:it
```

Gradle's `test` task excludes the standard integration-test class patterns (`IT*`, `*IT`, and
`*ITCase`). The `it` task includes those patterns and uses the same test source set. `check` depends
on `it`. Target a test with Gradle's native filter on either task:

```bash
./gradlew -Pskip.fe.build=true :camunda-search-client:test \
  --tests 'io.camunda.search.client.SomeTest'
./gradlew -Pskip.fe.build=true :camunda-search-client:it \
  --tests 'io.camunda.search.client.SomeIT'
```

`-Pquickly` is a fast development option. It omits Optimize projects from the Gradle project graph,
disables test execution and Spotless tasks, and also skips frontend builds. The `testClasses` task
still compiles test code without running tests. Prefer `-Pskip.fe.build=true` when only frontend
work should be skipped and the active project graph should remain unchanged.

## Packaging and frontend assets

```bash
# Builds frontend assets as dependencies of the distribution assembly
./gradlew :camunda-zeebe:distZip

# Backend-only archive: does not build fresh frontend assets
./gradlew -Pskip.fe.build=true :camunda-zeebe:distZip
```

Frontend builds are triggered by distribution assembly, not by compilation, tests, or a standalone
webjar `jar` task. An archive built with `-Pskip.fe.build=true` is **not frontend-complete** and must
not be treated as a user-ready distribution.

For backend parity, the dependency manifest and archive can be built with:

```bash
./gradlew --console=plain --parallel -Pskip.fe.build=true \
  testClasses :camunda-zeebe:distZip
./gradlew --no-configuration-cache --console=plain -Pskip.fe.build=true \
  :camunda-zeebe:writeDistDependencyReport
python3 .github/scripts/gradle/compare-dist.py \
  dist/build/distributions/camunda-zeebe-*.zip \
  dist/target/camunda-zeebe-*.zip \
  --gradle-manifest dist/build/reports/dist-dependencies.json
```

The Maven ZIP in `dist/target/` must be produced from the **same source and POM revision** as the
Gradle archive. If needed, build it with Maven before comparing:

```bash
./mvnw -pl dist -am package -DskipTests -DskipChecks \
  -PskipFrontendBuild -Dskip.fe.build
```

The comparator checks the versioned ZIP root and bundled JAR names and versions. It does not compare
all archive file contents, scripts, or file permissions. Its successful result is an inventory parity
signal, not byte-for-byte distribution equivalence. The Gradle manifest allows the comparator to
apply the documented policy for transitive patch-version differences.

## Useful properties

- `-Pskip.fe.build=true` disables frontend npm tasks while keeping the active project graph.
- `-Pquickly` omits Optimize projects, disables tests and Spotless, and skips frontend builds.
- `-PuseMavenLocal` adds Maven Local to dependency resolution when explicitly needed.
- `-Ptest.max.forks=N` sets the number of Gradle test JVM forks; default is `1`. Keep the default
  until the shared test-port slot handling supports multiple forks.
- `-Ptest.max.retries=N` sets the maximum retries for failed tests; default is `0`. Do not use
  retries to hide a test failure.
- `-Pparallel.tests` enables JUnit parallel execution for tests that opt in with their execution
  mode.
- `-Pjunit.thread.count=N` sets the fixed JUnit parallelism; default is `2` when parallel tests
  are enabled.
- `-Ptest.jvm.maxheap=SIZE` sets the test JVM maximum heap, for example
  `-Ptest.jvm.maxheap=2g`.
- `-PincludeSlowTests` includes tests normally excluded as slow.
- `-PincludePerformanceTests` includes performance-tagged tests.
- `-PincludeStraceTests` includes strace-tagged tests.
- `-PincludeRandomTests` selects randomized tests and uses the configured randomized-test counts.

The repository enables the Gradle build cache and configuration cache in `gradle.properties`. To
diagnose task caching, repeat a task with `--info` and inspect whether Gradle reports `UP-TO-DATE`
or `FROM-CACHE`. To inspect configuration-cache problems, run the same task twice with
`--configuration-cache --configuration-cache-problems=warn --info` and check for cache reuse and
reported incompatibilities. Some tasks or callers still disable the configuration cache; for
example, the distribution dependency report command above uses `--no-configuration-cache`. Keep
that workaround until the task and its callers have been verified together.

## Build-logic tests and fixtures

The convention and task implementation tests live in
`gradle/build-logic/conventions/src/test/kotlin/`; their TestKit fixtures live in
`gradle/build-logic/conventions/src/test/resources/fixtures/`. `gradle/build-logic` is an included
build holding the convention plugins (`conventions`) and the shared Maven POM resolver
(`pom-resolution`). Run all of its tests with:

```bash
./gradlew -p gradle/build-logic test
```

Run only the convention tests, or a single test class or method, with:

```bash
./gradlew -p gradle/build-logic :conventions:test
./gradlew -p gradle/build-logic :conventions:test \
  --tests 'buildlogic.DistributionPackagingTest'
./gradlew -p gradle/build-logic :conventions:test \
  --tests 'buildlogic.DistributionPackagingTest.<methodName>'
```

These tests are not part of the root build's `test` or `testClasses` tasks, and CI does not run
them. Run them locally after changing anything under `gradle/build-logic/`.

To add a build-logic regression fixture, add a small isolated project under
`gradle/build-logic/conventions/src/test/resources/fixtures/<fixture-name>/`. Add a JUnit test under
`gradle/build-logic/conventions/src/test/kotlin/` that copies the fixture to a temporary directory, starts it with
`GradleRunner` and `withPluginClasspath()`, and asserts task outcomes or generated files. Keep the
fixture offline and scoped to the behavior under test; avoid depending on the full monorepo project
graph when a minimal project can reproduce the contract.
