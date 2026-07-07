# Pom Changes That Break Gradle Parity

Catalog of Maven `pom.xml` edits that commonly break the Gradle build (including tests),
grouped by what changed in the pom. Check this list first when a Gradle failure follows a
pom-only commit — the Gradle side almost never auto-picks-up a pom change.

## Dependency graph changes

- **Missing internal module dep** — new `<dependency>` on a reactor module (`io.camunda:*`)
  has no matching `project(":...")` line in the Gradle module's `build.gradle.kts`.
  Symptom: `cannot find symbol` for a class that clearly exists in another module.
- **Missing external lib** — new third-party `<dependency>` has no version-catalog entry
  (`libs.versions.toml` / `settings.gradle.kts` catalog) and no `implementation(libs...)` line.
- **Wrong `api()` vs `implementation()`** — Maven has no compile/api split, so a new
  transitive-needing-consumer dep is easy to wire as `implementation` when downstream modules
  actually need it on their compile classpath (→ `api`), or as `api` when it should stay
  internal (→ leaks to consumers, see optional-dependency section in SKILL.md).
  **Cache-invalidation cost:** `api()` puts the dep on every consumer's compile classpath, so
  a version bump or change in that dep invalidates the *compile* task (and build-cache entry)
  of every downstream module, not just the declaring one. Wiring a dep as `api` when
  `implementation` would suffice fans out cache misses/recompiles across the whole dependent
  graph — mis-scoping is a build-cache-effectiveness regression, not just an over-exposure risk.
- **Missing dependency from Maven scope/ordering quirks** — Maven's nearest-wins + declaration
  order can resolve a transitive differently than Gradle's conflict resolution. A version bump
  or reordering in one pom changes what wins the conflict in Maven but not in Gradle (or vice
  versa), producing a version mismatch or a missing class at runtime only. Diagnose with
  `compare-module-deps.py --versions` and `./mvnw dependency:tree`.
- **Removed/changed `<exclusions>`** — a new or changed exclusion block in the pom has no
  matching `exclude(group = ..., module = ...)` on the Gradle side, so Gradle pulls in a
  transitive Maven deliberately drops (or the reverse, if the exclusion was removed).
- **`<optional>true</optional>` added/removed** — needs the `compileOnly` +
  `testImplementation` + `OptionalDependenciesPomAction` treatment on the Gradle side (see
  SKILL.md's optional-dependency section). Easy to miss because Maven needs zero extra wiring.
- **`test-jar` goal added/removed** — a module newly emitting or consuming a `*-tests.jar`
  needs `buildlogic.test-jar-conventions` (producer) and
  `testImplementation(project(":x", configuration = "tests"))` (consumer) — see SKILL.md.
- **New reactor module** — a new `<module>` in a parent pom needs a matching `include(...)` in
  `settings.gradle.kts`, or the module silently doesn't exist in Gradle.

## Version / property changes

- **Inline `<version>` or `<properties>` bump** — the version catalog reads versions via
  `pomVersion("version.X")` from `parent/pom.xml` properties. A version bumped **inline** in a
  module pom (not through a property) has no catalog source and Gradle keeps building against
  the old (or hardcoded) version — silent skew, not a build failure. See "No free versions" in
  SKILL.md.
- **BOM / `dependencyManagement` import version bump** — if the BOM version is pinned
  separately in the Gradle catalog rather than derived via `pomVersion`, a pom-side BOM bump
  doesn't propagate and dependency versions silently diverge between the two builds.

## Test-configuration changes

- **Surefire/Failsafe include/exclude pattern changes** — Maven's `<includes>`/`<excludes>`
  in `maven-surefire-plugin` / `maven-failsafe-plugin` config must stay mirrored by the
  Gradle `test`/`it` task filters (`IT*`, `*IT`, `*ITCase` split). A pattern change on the
  Maven side with no matching edit on the Gradle side causes tests to run in the wrong task
  (or not at all).
- **JVM args / system properties in surefire config** — `<argLine>`, `<systemPropertyVariables>`
  changes need mirroring into the Gradle `test`/`it` task's `jvmArgs`/`systemProperty(...)`.
  Easy to miss for CI-only properties (heap size, temp dir, feature flags) — the test passes
  locally but fails or behaves differently in Gradle CI.
- **`forkCount`/parallelism changes** — Maven `forkCount`/`reuseForks` tuning doesn't
  automatically map to Gradle `maxParallelForks`. A concurrency change made for Maven's fork
  model can leave Gradle over- or under-parallelized for the runner's CPU/memory budget,
  causing CI-only OOMs or timeouts that don't reproduce locally.

## Code generation / resource changes

- **New codegen input (OpenAPI spec, SBE schema, protobuf, templated resource)** — Maven
  plugin config changes (new spec file, new template placeholder) need a matching update to
  the Gradle `GenerateTask`/`Sync`+`ReplaceTokens` equivalent in `buildSrc/` — see
  [maven-plugin-equivalents.md](maven-plugin-equivalents.md). A new placeholder token with no
  Gradle-side substitution ships the literal placeholder string into the built resource.

## CI-environment-shaped failures (not a pom diff, but pom-adjacent)

- **Runner concurrency/parallelism misconfigured** — wrong `-T` / `maxParallelForks` /
  matrix shard count for the runner's actual core count causes flaky OOM or timeout failures
  that look like product bugs but are resource starvation.
- **Test-report generation fails on unmappable filename characters** —
  `Could not generate test report ... Malformed input or input contains unmappable characters`
  for an HTML report path derived from a parameterized JUnit 5 display name containing
  non-ASCII characters (e.g. `→` in `[userTaskAssign-→-zeebe-assignmentDefinition]`). Gradle's
  HTML test reporter builds one file per test using the **display name** verbatim in the
  filename; Maven Surefire's HTML/XML reports don't do this (index-based naming), so this class
  of failure is Gradle-specific. Root cause is usually the runner's JVM file-system encoding
  (`sun.jnu.encoding`, often `ANSI_X3.4-1968`/POSIX on a minimal Linux image) not being UTF-8,
  so it can't encode the non-ASCII display-name characters into a filesystem path. Fix at the
  source (avoid non-ASCII in `@MethodSource`/`@ParameterizedTest` display names) or force a
  UTF-8 file-system encoding for the CI JVM (`org.gradle.jvmargs=-Dfile.encoding=UTF-8
  -Dsun.jnu.encoding=UTF-8`, or `LANG=en_US.UTF-8`/`LC_ALL=en_US.UTF-8` in the runner env).
