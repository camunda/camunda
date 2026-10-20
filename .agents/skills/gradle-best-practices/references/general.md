# General

## Use Kotlin DSL · `use_kotlin_dsl` · Recommendation
When: always.
Detect (det): any file named `build.gradle` or `settings.gradle` (no `.kts`).
Fix: Convert the script to `.gradle.kts`, one script at a time. Structural — plan first.

## Use the Latest Minor Version of Gradle · `use_latest_minor_versions` · Medium
When: a wrapper exists.
Detect (heur): the version in `distributionUrl` (`gradle/wrapper/gradle-wrapper.properties`). Flag only if clearly old — a superseded major, or several minors behind. Do not guess at "latest"; state the version found and that it needs checking, rather than asserting a newer number.
Fix: Run `./gradlew wrapper --gradle-version <v>`, then update plugins. Gradle before plugins.

## Apply Plugins Using the `plugins` Block · `use_the_plugins_block` · Medium
When: always.
Detect (det): `apply plugin:`, `apply(plugin =`, or a `buildscript {` block containing `classpath(` / `classpath `, in any build or settings script.
Fix: Replace with `plugins { id("…") }` and delete the `buildscript { }` classpath entry.

## Don't Assume your Plugin is Applied after Another · `dont_assume_plugin_order` · Medium
When: build logic in `buildSrc/` or `build-logic/`, or a script configuring another plugin's extensions.
Detect (heur): `extensions.getByType(` / `extensions.getByName(`, or configuration of another plugin's extension at the top level of a plugin's `apply`. `subprojects {}` / `allprojects {}` blocks configuring plugin extensions are the common case. **Being guarded is not enough — check which guard.** `pluginManager.withPlugin("id") { … }` is a deferred callback and is the fix; `plugins.hasPlugin("id")` / `pluginManager.hasPlugin("id")` / `plugins.findPlugin(…)` are eager point-in-time queries and are *themselves* the violation, because applying this plugin first makes the test false and the whole block is silently skipped forever. An `if (hasPlugin)` wrapper reads like a fix and reports nothing when it fails; treat it as unguarded.
Fix: Replace any `if (…hasPlugin(…))` test with `pluginManager.withPlugin("id") { … }`, which fires whenever the other plugin is applied, before or after. If the prerequisite is genuinely required rather than optional, apply it explicitly with `pluginManager.apply("id")` and drop the conditional.

## Do Not Use Internal APIs · `do_not_use_internal_apis` · High
When: Java/Kotlin/Groovy source under `buildSrc/` or `build-logic/`, or scripts importing Gradle types.
Detect (det): `org.gradle.` … `.internal.` in an import or fully-qualified reference; a cast or reference to a type ending `Internal` (e.g. `AttributeContainerInternal`) or `Impl`.
Fix: Use the public API equivalent; if none exists, copy the logic into the project.

## Set Build Flags in `gradle.properties` · `use_the_gradle_properties_file` · Medium
When: always.
Detect (heur): `org.gradle.*` flags absent from the root `gradle.properties` while appearing in CI configuration, scripts or documentation as `-D` / `-P` arguments. Also flag a missing root `gradle.properties` in a build that plainly needs flags (see `performance.md`).
Fix: Move the flags into the root `gradle.properties`, one `key=value` per line.

## Name Your Root Project · `name_your_root_project` · Medium
When: a settings file exists — or should; a build with none is its own finding.
Detect (det): no `rootProject.name` assignment in `settings.gradle.kts` / `settings.gradle`.
Fix: Add `rootProject.name = "…"` to the settings file, after any `pluginManagement { }`.

## Do not use `gradle.properties` in subprojects · `do_not_use_gradle_properties_in_subprojects` · Medium
When: more than one project.
Detect (det): a `gradle.properties` file at any path other than the root project, or an included build's own root.
Fix: Copy the values into the root `gradle.properties`, then **delete the subproject's file** — emptying it does not clear the violation, since the detection is the file's existence at that path, not its contents. Use a convention plugin for per-project config.

## Avoid `afterEvaluate` · `avoid_after_evaluate` · High
When: always.
Detect (det): `afterEvaluate` anywhere in a build script, settings script, or build logic source.
Fix: Use lazy `Property`/`Provider` wiring, and `pluginManager.withPlugin` to react to plugins.

## Consider use of `@Incubating` APIs carefully · `consider_use_of_incubating_apis_carefully` · Recommendation
When: build logic source or scripts using recent Gradle APIs.
Detect (heur): APIs annotated `@Incubating`, or an `@OptIn`-style suppression of an incubating warning. Flag only where the usage is load-bearing, and note it as a maintenance risk rather than a defect.
Fix: Record which incubating APIs are used and why; re-check them on every Gradle upgrade.

## Obtain Loggers via `Logging.getLogger(Class)` outside of Tasks · `obtain_loggers_via_logging_get_logger` · Medium
When: the build defines a `Plugin`, a `BuildService`, or other non-`Task` build logic that logs, in `buildSrc/`, `build-logic/`, or inline.
Detect (det): `project.logger` / `getProject().getLogger()` outside a task; a bare `logger` used inside `Plugin.apply` or a `BuildService` with no `Logging.getLogger(…::class.java)` declared; or an `@Inject`-ed `Logger` property. `Task.getLogger()` and the `logger` inherited inside a task type are *not* violations — Gradle attributes those per task.
Fix: Declare `private val logger = Logging.getLogger(TheClass::class.java)` in a companion object.
