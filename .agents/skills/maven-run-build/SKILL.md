---
name: maven-run-build
description: Use when compiling, testing, formatting, or packaging the monorepo with ./mvnw. For ./gradlew use gradle-run-build.
---

# Running the Maven build

Maven is the authoritative build. Run everything from the repo root with the wrapper (`./mvnw`).
For the experimental Gradle build, use `gradle-run-build`.

## Rules

- Scope to one module with `-pl <module>`. Never run a full-repo build for single-module work.
- Some modules are split into sub-modules that hold the tests (e.g. `zeebe/engine`,
  `zeebe/broker`). If a top-level module runs no tests, target the sub-module.
- Use `-T1C` (one thread per CPU core) for standalone builds. Use `-T2` when other heavy
  processes run (IDE, Docker, other builds).
- Use `-am` only with `install -Dquickly`. Never use `-am` with `verify`: it runs the tests of
  every dependency module.
- `-Dquickly` skips tests, formatting checks, and Optimize. Add `-DskipTests=false` to run
  tests with it. Use it for fast iteration only, not for the final check before a commit.
- Never suppress warnings or failures to force a build to pass.

## Common commands

```bash
# Build a module and its dependencies (recommended after changing upstream modules)
./mvnw install -pl <module> -am -Dquickly -T1C

# Build one module; dependencies must already be installed and unchanged
./mvnw install -pl <module> -Dquickly -T1C

# Build the full repo (only to check cross-module compilation)
./mvnw install -Dquickly -T1C

# Run one unit test class
./mvnw verify -pl <module> -Dtest=<TestClassName> -DskipTests=false -DskipITs -Dquickly

# Run one integration test class
./mvnw verify -pl <module> -Dit.test=<IntegrationTestClassName> -DskipTests=false -DskipUTs -Dquickly

# Run all tests of a module
./mvnw verify -pl <module> -Dquickly -DskipTests=false -T1C
```

## Green baseline

Partial Maven builds (`-pl` without `-am`) resolve other modules from the installed artifacts
in `~/.m2`, which can be stale or from another branch. The build cache can also skip modules
whose inputs it wrongly considers unchanged. The extensions in `.mvn/extensions.xml` (build
cache, branch-scoped local repository) reduce this but do not fully fix it. A stale artifact
shows up as a compile error or a test failure that is not caused by your change. Check the
baseline right after branching:

1. Build the full repo to catch cross-module compile errors (e.g. an API change on `main` that
   breaks a downstream module): `./mvnw install -Dquickly -T1C`. Do not continue until it is
   green.
2. Once you know the target module, verify its tests pass:
   `./mvnw verify -pl <module> -Dquickly -DskipTests=false -T1C`. Target the sub-module that
   holds the tests, otherwise you can get a false green with no tests run.

Note any failure that exists before your change; do not absorb it silently. A baseline failure
that is not flaky should not be on the branch: stop and tell the engineer.

## Formatting

Format before every commit that touches Java sources, markdown, or `pom.xml` files. Run it
again after any later edit. Skipping it breaks the `Java checks` CI job.

```bash
./mvnw license:format spotless:apply -T1C
```

## Before committing

1. Format (see above).
2. Build the changed module and run its tests without `-Dquickly` skipping checks:
   `./mvnw verify -pl <module> -DskipTests=false`
3. Check that the full repo still compiles: `./mvnw install -Dquickly -T1C`
