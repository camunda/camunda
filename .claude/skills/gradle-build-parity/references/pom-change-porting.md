# Porting Maven changes to Gradle

Use this procedure whenever a Maven `pom.xml` change must be represented in the Gradle build.
Maven is the source of truth. Port the final behavior, not merely the fact that a POM line was
added or removed. For rebased branches, first use [rebase-pom-audit.md](rebase-pom-audit.md) to
reduce the history to the final net POM state.

## 1. Classify the Maven change

Before editing, inspect both the module POM and the relevant parent POM. Classify each final change:

1. **New reactor module** — add `include(":<artifactId>")` and its `projectDir` mapping in
   `settings.gradle.kts`; add the module `build.gradle.kts`.
2. **Internal reactor dependency** — add `implementation(project(":<artifactId>"))`, `api`, or a
   test configuration according to the API boundary. Do not add an external catalog alias for a
   reactor artifact.
3. **New external dependency** — add a version-catalog alias and a Gradle dependency line. Follow
   the no-free-versions rule below.
4. **Scope or exclusion change** — map Maven compile/provided/test/optional behavior to the
   matching Gradle configuration and exclusions. Maven compile scope alone is not evidence for
   `api`; inspect whether dependency types cross the module boundary.
5. **Test-jar change** — mirror both producer and consumer wiring when a module starts or stops
   publishing or consuming a `*-tests.jar`.
6. **Plugin, profile, test-filter, or code-generation change** — mirror it in the appropriate
   convention/module task, or explicitly record it as a known Maven-only/deferred gap.

Check the current Gradle files before adding anything:

```bash
rg -n '<artifact-id>|<group-id>' <module>/pom.xml <module>/build.gradle.kts
rg -n --glob '*.gradle.kts' '<artifact-or-project-name>' .
```

An already-present dependency is verify-only, not an unconditional duplicate-add task.

## 2. Choose the Gradle dependency configuration

Maven's default `compile` scope does not automatically mean Gradle `api()`:

- Use **`api()`** when the dependency's classes are exposed by the module's public or protected
  API and must be resolved by consumers. This includes public/protected method parameters and
  return types, fields, constructors, thrown types, generic bounds, implemented interfaces,
  superclasses, and annotations that consumers must resolve.
- Use **`implementation()`** when the dependency is used only by implementation code, including
  implementation code in another package in the same module. A direct Maven compile dependency or
  a currently-working transitive path is not enough evidence for `api`.
- Because Gradle `implementation()` dependencies are absent from a consumer's compile classpath,
  add a direct dependency to every consumer whose source imports those classes. Choose that
  consumer's `api` versus `implementation` from the consumer's own API boundary; do not make the
  provider's dependency `api` merely to preserve an accidental transitive compile path.
- Test-only usage remains `testImplementation()`.

### Maven optional dependencies

Maven `<optional>true</optional>` normally maps to:

```kotlin
compileOnly(libs.some.library)
testImplementation(libs.some.library)
```

Also list the dependency in the module's `OptionalDependenciesPomAction` so the published POM
retains Maven optional-dependency semantics. Do not use `api()` for an optional dependency:
Gradle `api()` propagates it to consumer compile and runtime classpaths, unlike Maven optional.

The `compileOnly` approximation intentionally leaves the dependency off the declaring module's
runtime classpath. It is safe when the dependency is used only behind Spring Boot
`@ConditionalOnClass`; use feature variants instead if the module needs more precise optional
runtime behavior.

### Maven test jars

Maven modules with a `test-jar` execution expose a `*-tests.jar`. Gradle needs both sides:

**Producer:**

```kotlin
plugins {
  id("buildlogic.test-jar-conventions")
}
```

**Consumer:**

```kotlin
testImplementation(project(":some-module"))
testImplementation(project(":some-module", configuration = "tests"))
```

If test compilation cannot find a class under another module's `src/test/java`, check both pieces.

## 3. Required: no free library versions

**Never define a library version directly in Gradle.** This applies to external libraries, tools,
BOMs, buildscript dependencies, convention-plugin dependencies, and version-catalog entries. A
version must be sourced from Maven through the generated `libs` catalog or omitted because an
already-represented BOM manages it. A hardcoded library version in any Gradle file is a parity
violation, even if Maven has no convenient property yet.

The catalog is generated in `settings.gradle.kts`. Use Maven as its source of truth:

```bash
rg -n '<version\.' parent/pom.xml
rg -n '<artifact-id-fragment>|<group-id-fragment>' --glob 'pom.xml' .
rg -n 'version\(|pomVersion\(' settings.gradle.kts
```

Choose the catalog declaration as follows:

- A dependency managed by an already imported BOM uses `.withoutVersion()`.
- A dependency whose version is a parent property uses
  `version("some-lib", pomVersion("version.some-lib"))`, then `versionRef("some-lib")` on the
  library.
- If Maven has an inline library version in a module POM, first promote it to a parent property
  and use `${version.some-lib}` in Maven. Then source the catalog from that property.
- A genuinely versionless dependency still needs a catalog alias if it is referenced as `libs...`.

Typical declarations:

```kotlin
version("some-lib", pomVersion("version.some-lib"))
library("com-example-some-lib", "com.example", "some-lib").versionRef("some-lib")

// Dependency managed by a Maven BOM already represented in the catalog.
library("software-amazon-awssdk-new-service", "software.amazon.awssdk", "new-service")
  .withoutVersion()
```

The only hardcoded-version exception is a Gradle plugin version when the Gradle plugin mechanism
cannot consume the Maven-sourced version. That exception does not apply to libraries used by the
plugin or to ordinary buildscript dependencies; use the catalog for those.

Catalog accessors also work inside `buildscript {}` on Gradle 9.5, so use
`classpath(libs.some.lib)` rather than a hardcoded coordinate.

## 4. Compare the resolved dependency graphs

After wiring the settings/module entry, compare one module at a time:

```bash
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope compile
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope runtime
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope test --versions
```

Use Maven to explain a discrepancy rather than blindly changing Gradle:

```bash
./mvnw dependency:list -pl <module-dir>
./mvnw dependency:tree -pl <module-dir> -Dincludes=<group>:<artifact>
```

The comparison can report false Maven runtime extras for classifier variants reached through a
test-scoped dependency. Confirm the per-artifact Maven scope and the Gradle
`testRuntimeClasspath` before changing the build. A transitive dependency is not a substitute for
a direct dependency when source code uses it or Maven declares it explicitly.

## 5. Validate narrowly, then validate the Gradle gate

For a module build change, use the affected module only for the inner loop:

```bash
./gradlew :<project>:compileJava --configuration-cache
./gradlew :<project>:compileTestJava --configuration-cache
./gradlew :<project>:test --tests '<TestClass>' --configuration-cache
```

Before concluding, complete the Gradle CI-shaped validation from the repository root:

```bash
./gradlew --no-daemon --console=plain --parallel -Pskip.fe.build \
  testClasses :camunda-zeebe:distZip
```

Compare the Gradle archive with the Maven archive:

```bash
python3 .claude/skills/gradle-build-parity/compare-dist.py \
  dist/build/distributions/camunda-zeebe-*.zip \
  dist/target/camunda-zeebe-*.zip
```

Every Gradle change must pass this distribution parity check. CI uses the same `testClasses` plus
distribution build gate, followed by the required distribution parity job. The current CI artifacts
are ZIPs; `compare-dist.py` also supports tar.gz/exploded distribution comparisons. The Gradle path
is not a full test-suite run.

For a documentation-only porting audit, no build is necessary, but run `git diff --check` on the
changed reference files.

## 6. Record decisions and deferred behavior

Keep an actionable note with the source commit/POM path, final Maven behavior, target Gradle file,
and validation result. Mark changes as:

- **ported** — Gradle behavior now matches Maven;
- **verify-only** — the behavior was already present and was checked; or
- **deferred** — Maven-only publication/release/plugin behavior has no current Gradle equivalent.

Never silently drop a Maven change just because it does not affect compilation.

## Common edge cases

- Gradle `implementation` is not compile-transitive to consumers, unlike Maven's usual compile
  dependency propagation. A Maven consumer importing a provider's transitive dependency may need an
  additional direct Gradle declaration; this is not a reason to make the provider's dependency
  `api`.
- A new parent-POM dependency can require two independent Gradle changes: the module dependency
  and the catalog alias. Missing either one causes a compile failure or violates catalog parity.
- A new dependency may be supplied transitively in Gradle and therefore not fail compilation. Keep
  the explicit Gradle declaration when Maven declares it directly, then verify `api` versus
  `implementation`.
- A POM plugin setting can matter for Maven publication but have no Gradle equivalent in the
  current scope, such as flattened POMs, source/javadoc attachment, or dependency analysis. Keep
  these in the deferred section so they remain visible.
