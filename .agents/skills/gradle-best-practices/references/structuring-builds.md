# Structuring Builds

## Do Not Put Source Files in the Root Project · `no_source_in_root` · Medium
When: always — a single-project build with source in the root is exactly the case this describes.
Detect (det): a `src/main/` or `src/test/` directory at the root, and/or a language plugin (`java`, `java-library`, `application`, `groovy`, `kotlin("jvm")`) applied in the root `build.gradle(.kts)`.
Fix: Move `src/` into a new subproject with its own build script and `include("…")` it. Structural.

## Modularize Your Builds · `modularize_builds` · Recommendation
When: the build has one project, or one project holding unrelated concerns.
Detect (heur): a single project whose sources cover clearly separable concerns — unrelated top-level classes, or one build script mixing dependencies belonging to different layers (an application entry point plus utility code plus third-party integrations). Require concrete evidence: name the classes or dependency groups that would move.
Fix: Propose a decomposition into subprojects wired by `implementation(project(":…"))`. Structural.

## Favor `build-logic` Composite Builds for Build Logic · `favor_composite_builds` · Medium
When: `buildSrc/` exists.
Detect (det): a `buildSrc/` directory containing a build script or `src/main/` sources.
Fix: Move build logic into `build-logic/` with its own settings file, pulled in via `includeBuild`. Structural.

## Avoid Unintentionally Creating Empty Projects · `avoid_empty_projects` · Medium
When: the settings file includes any project by a path holding more than one segment.
Detect (det): **one** colon after the leading one is already enough — `include(":services:exporter")` counts, not just deeply nested `include(":subs:web:my-web-module")`. Gradle creates a project for *every* segment, so `:services:exporter` synthesizes `:services` as well. Report the intermediate segment when its directory holds no build script of its own (`services/build.gradle{,.kts}` absent). Reachable from either end: scan settings for the include paths, or scan the tree for a build script at `a/b/` with none at `a/`. A `projectDir` assignment on the *nested* path does not cure this — `project(":services:exporter").projectDir = …` leaves `:services` synthesized all the same; only a flat include name does.
Fix: Replace the nested include with a flat name and point it at the directory — `include(":exporter")` plus `project(":exporter").projectDir = file("services/exporter")` — so no empty intermediate project is synthesized.

## Use Convention Plugins for Common Build Logic · `use_convention_plugins` · Medium
When: the build has more than one project with a build script.
Detect (det where possible): **any** configuration repeated verbatim across two or more build scripts — this is a cross-file comparison, not a single-file scan, and two occurrences is enough to report. The shapes seen most often, not an exhaustive list: a `java { }` toolchain or source-compatibility block; `tasks.withType<JavaCompile>` settings; `useJUnitPlatform()`; `maxParallelForks`; an identical `testImplementation(…)`/`api(…)`/`implementation(…)` declaration; the same `plugins { }` set; repeated `group =` / `version =` assignments; a `repositories { }` block repeated per project. Cheapest way to run it: for each pattern you already grep, count the *distinct build scripts* it hits — two or more is the finding, and the grep you ran for some other entry has usually already produced the evidence.
Fix: Extract the duplication into a precompiled script plugin under `build-logic/src/main/kotlin/`. Structural.
