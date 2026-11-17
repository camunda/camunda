# Dependencies

## Declare Dependencies using a single GAV (group:artifact:version) String · `single-gav-string` · Medium
When: any `dependencies {}` block exists.
Detect (det): a dependency declaration using named arguments — `group:` / `name:` / `version:` (Groovy) or `group =` / `name =` / `version =` (Kotlin) inside a dependency call.
Fix: Replace the map form with a single `"group:name:version"` string.

## Use Version Catalogs to Centralize Dependency Versions · `use_version_catalogs` · Medium
When: any `dependencies {}` block exists.
Detect (det): no `gradle/libs.versions.toml`, while hard-coded versions appear in dependency strings; or versions held in `ext` / `extra` / local variables (`val fooVersion =`, `def fooVersion =`, `project.ext[`).
Fix: Create `gradle/libs.versions.toml` and replace each declaration with its `libs.` accessor.

## Name Version Catalog Entries Appropriately · `name_version_catalog_entries` · Recommendation
When: `gradle/libs.versions.toml` exists.
Detect (det): judge every key in `[versions]` and `[libraries]`, not just the ones matching an example below. A key is an offender when it uses `_` as a separator, repeats a segment (`ktor-ktor-client-core`), begins with a TLD (`com-`, `org-`), is a bare generic word (`java`, `core`, `module`, `stuff`, `utils`), is an abbreviation that does not name the library (`gv` for Guava, `jl`, `cl`), or is an article-prefixed camelCase coinage (`theJson`, `myLib`). The test to apply to each key: could a reader who does not know this build say which library it refers to? If not, report it — the words listed here are examples of the failure, not the definition of it.
Fix: Rename entries per the published mapping — the catalog key determines the accessor.

## Set up your Dependency Repositories in the Settings file · `set_up_repositories_in_settings` · Medium
When: any `repositories {}` block exists anywhere.
Detect (det): a `repositories {` block in a `build.gradle` / `build.gradle.kts`, or inside a `buildscript {}` block.
Fix: Move them into `pluginManagement { }` / `dependencyResolutionManagement { }` in settings.

## Don't Explicitly Depend on the Kotlin Standard Library · `dont_depend_on_kotlin_stdlib` · Recommendation
When: a Kotlin plugin is applied (`kotlin("jvm")`, `org.jetbrains.kotlin.jvm`, …). Otherwise not applicable.
Detect (det): `kotlin("stdlib")` or `org.jetbrains.kotlin:kotlin-stdlib` in a `dependencies {}` block.
Fix: Delete the declaration — the Kotlin plugin adds it.

## Avoid Redundant Dependency Declarations · `avoid_duplicate_dependencies` · Medium
When: any `dependencies {}` block exists.
Detect (det): the same `group:artifact` reaching one project twice. Two shapes, and the second is the one that gets missed. **Within one file:** the coordinate appears in two of that project's dependency blocks — once in `api` and once in `implementation`, or in both `implementation` and `compileOnly` / `runtimeOnly` where that is redundant. **Across files:** the project declares a coordinate *directly* and also declares `api(project(":other"))` where `other/build.gradle{,.kts}` already exposes that same coordinate with `api` — the direct declaration then adds nothing. The redundancy exists only in the pair, so no scan of either file alone will show it; when a build script declares a project dependency, open that project's script and compare the two coordinate lists.
Fix: Keep the single declaration with the widest correct scope and delete the rest.

## Use Content Filtering with multiple Repositories · `use_content_filtering` · Medium
When: more than one repository is declared in the same `repositories {}` block.
Detect (det): two or more repository declarations with no `content {` or `exclusiveContent {` block among them.
Fix: Add `content { }` filters to each repository so coordinates resolve from the intended one.

## Apply Exclusions Narrowly · `apply_exclusions_narrowly` · Medium
When: any `exclude` appears in dependency or configuration handling.
Detect (det): `exclude` inside a `configurations {` block, inside `configurations.configureEach {`, or an `exclude(group = "...")` / `exclude group:` with no `module` argument.
Fix: Move the exclusion onto the offending declaration, naming both group and module.

## Always Declare Attributes on Consumable and Resolvable Configurations · `use_attributes_on_configurations` · High
When: the build declares a custom configuration via `configurations.consumable(`, `configurations.resolvable(`, `configurations.create(`, or `configurations.dependencyScope(`. Otherwise not applicable.
Detect (det): such a declaration with no `attributes {` block; or a dependency declared against a named configuration (`configuration = "customElements"`, `project(path: ..., configuration: ...)`). The runtime symptom is "Unable to find a matching variant of project".
Fix: Declare matching attributes on both ends and drop the explicit configuration name.
