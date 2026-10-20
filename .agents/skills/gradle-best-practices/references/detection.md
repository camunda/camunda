# Detection

How to read a catalog entry, and which token sends you to which entry. Read
this once, at the start of Step 3.

## Anatomy of an entry

The translation from documentation prose into a concrete check is already done,
once, and recorded in the category files. **Do not re-derive it.** The recipes
are this skill's fixed contract: two runs against the same project must check
the same things the same way, and a re-derived check breaks that.

Each entry is a heading carrying `Title · anchor · Severity`, then:

1. **`When:`** — the precondition. If the project does not meet it, the entry is
   *not applicable*; count it and move on. (No Kotlin plugin applied means the
   Kotlin-stdlib entry is out. No custom tasks or plugins means the TestKit
   entry is out.)
2. **`Detect (det)` / `Detect (heur)`** — the check to run against the files
   discovered in Step 1.
   - **`det`, deterministic** — a specific string, glob, or property value
     answers it yes/no: `distributionUrl` ends in `-all.zip`, `afterEvaluate {`
     anywhere, `PathSensitivity.ABSOLUTE` in custom task source.
   - **`heur`, heuristic** — requires judgment, usually across several files.
     Flag only on the evidence the entry names, and note the uncertainty in the
     finding.
3. **Severity band** — the third field on the heading line. High = security
   risks, likely build failures, broken configuration cache, significant
   correctness problems. Medium = suboptimal builds, maintenance burden,
   violations of Gradle conventions. Recommendation = modern idioms and
   nice-to-haves. These bands are this skill's editorial classification; the
   official docs assign none, so do not present them as Gradle ranking one
   practice above another.
4. **`Fix:`** — the change to make, and what the report's **Fix:** line quotes.

**A recipe's examples are examples, not the whole rule.** Where an entry lists
tokens — vague catalog keys, duplicated block shapes, eager collection calls —
the list names the shapes seen most often and the entry's sentence is what you
check against. Judge every candidate of that kind against the rule; never clear
a project because its instance was not one of the spellings written down.

**Where an entry names the fix's shape, the shape is load-bearing.** A
construct that resembles it is not it: `if (plugins.hasPlugin("x"))` reads like
`pluginManager.withPlugin("x")` and is the opposite — an eager test that
silently skips. Read past "is it guarded?" to "is it guarded by *that*?"

## Running the checks

Run every applicable check. Batch the searching by pattern rather than by entry
— one pass per pattern across the discovered files is cheaper than re-reading
every file once per entry.

### Entries decided by comparing files

Most checks are answered by one file. Four are not, and they are the ones a
file-by-file sweep walks straight past, because no single file is wrong — the
violation lives in a *relationship*:

| entry | the comparison |
|:--|:--|
| `use_convention_plugins` | the same configuration in two or more build scripts |
| `avoid_duplicate_dependencies` | a coordinate declared directly *and* reaching the project through an `api(project(":other"))` |
| `avoid_empty_projects` | a build script at `a/b/` with none at `a/` |
| `use_unique_output_files_and_directories` | two tasks, possibly in different projects, writing one path |

The batching above already does most of this work — you just have to read it
that way. **For every pattern you grep, look at how many distinct build scripts
it hit.** One hit is a candidate for whichever entry sent you looking; two or
more hits of the same block is *additionally* evidence for
`use_convention_plugins`, whatever you were originally searching for. When a
build script declares `project(":other")`, open `other/`'s script before moving
on — that pair is the only place `avoid_duplicate_dependencies` is visible.

Run this pass before writing the report, not after. Restructuring the build
during Step 6 can dissolve the duplication that was the evidence, and an entry
you never recorded cannot be re-derived from the fixed tree.

## Symptom index

The patterns to batch on. Each row is a literal you can `grep` for and the entry
it belongs to; the entry in the category file is still the contract — this table
only tells you which entry to go and read, and never what to report.

It exists because a token in a build script is the thing you actually see first.
Scanning `lib/build.gradle.kts` and meeting `outputs.cacheIf { true }` is no use
if nothing connects that string to `use_cacheability_annotations` until you have
already opened `tasks.md` for another reason.

Presence is only one of three ways a row fires. Several fire on a token that is
**missing**. A few fire on where a file **sits** rather than on anything written
inside it — and a file you have already opened and filed findings against is not
finished with, because its own directory may be the symptom.

| grep for | entry | category |
|:--|:--|:--|
| `afterEvaluate` | `avoid_after_evaluate` | general |
| `apply plugin:`, `apply(plugin =`, `buildscript {`, `classpath` | `use_the_plugins_block` | general |
| `extensions.getByType(`, `extensions.getByName(` after an `apply` | `dont_assume_plugin_order` | general |
| `plugins.hasPlugin(`, `pluginManager.hasPlugin(`, `plugins.findPlugin(` — an eager test posing as a guard | `dont_assume_plugin_order` | general |
| `.internal.`, `org.gradle.…Internal` | `do_not_use_internal_apis` | general |
| `@Incubating`, `@OptIn` | `consider_use_of_incubating_apis_carefully` | general |
| `project.logger`, `getProject().getLogger()` | `obtain_loggers_via_logging_get_logger` | general |
| a `*.gradle` file where `*.gradle.kts` is meant | `use_kotlin_dsl` | general |
| **no** `rootProject.name` in settings | `name_your_root_project` | general |
| `gradle.properties` inside a subproject directory | `do_not_use_gradle_properties_in_subprojects` | general |
| `-D` / `-P` flags where `gradle.properties` belongs | `use_the_gradle_properties_file` | general |
| `include(` whose path carries a colon after the leading one — `include(":services:exporter")` already synthesizes `:services` | `avoid_empty_projects` | structuring-builds |
| a build script at `a/b/build.gradle{,.kts}` with **no** build script in `a/` | `avoid_empty_projects` | structuring-builds |
| `src/main/`, `src/test/` in the **root** project | `no_source_in_root` | structuring-builds |
| `buildSrc/` | `favor_composite_builds` | structuring-builds |
| any block repeated across two build scripts — `java { }`, `tasks.withType<…>`, `plugins { }`, `group =` / `version =`, `repositories { }`, an identical dependency line | `use_convention_plugins` | structuring-builds |
| `repositories {` in a build script rather than settings | `set_up_repositories_in_settings` | dependencies |
| a hardcoded `group:artifact:version`, `val fooVersion =`, `ext`/`extra` | `use_version_catalogs` | dependencies |
| a catalog key that does not name its library — generic (`core`, `utils`, `stuff`), abbreviated (`gv`), or coined (`theJson`) | `name_version_catalog_entries` | dependencies |
| the same GAV under two configurations, **or** declared directly while also arriving via `api(project(":other"))` | `avoid_duplicate_dependencies` | dependencies |
| `exclude` on `configurations {` / `configurations.configureEach {` | `apply_exclusions_narrowly` | dependencies |
| a consumable/resolvable configuration with **no** `attributes {` | `use_attributes_on_configurations` | dependencies |
| a dependency in map form — `group:` / `name:` / `version:` (Groovy) or `group =` / `name =` / `version =` (Kotlin) | `single-gav-string` | dependencies |
| `kotlin("stdlib")`, `org.jetbrains.kotlin:kotlin-stdlib` | `dont_depend_on_kotlin_stdlib` | dependencies |
| **no** `content {` / `exclusiveContent {` on a narrow repository | `use_content_filtering` | dependencies |
| `outputs.cacheIf`, `outputs.doNotCacheIf` | `use_cacheability_annotations` | tasks |
| `dependsOn` between two tasks that have actions | `avoid_depends_on` | tasks |
| `.files`, `.asPath`, `.size`, `.isEmpty()`, `.toList()` on a `FileCollection` — including `fileTree(…).files` | `avoid_eager_file_collection_apis` | tasks |
| `.resolve()`, `configurations.<name>.files`, `.asFileTree`, `.singleFile` | `dont_resolve_configurations_before_task_execution` | tasks |
| `.get()`, `.getOrElse(`, `.getOrNull()`, `.isPresent` outside `@TaskAction` / `doLast` | `avoid_provider_get_outside_task_action` | tasks |
| a provider passed to an `Object`/`String` parameter — `systemProperty(k, p)`, `args(p)`, `"$p"` | `avoid_provider_get_outside_task_action` | tasks |
| `project.` *inside* `@TaskAction` / `doLast {` / `doFirst {` | `dont_access_project_instance_inside_task` | tasks |
| `PathSensitivity.ABSOLUTE` | `default_path_sensitivities` | tasks |
| `tasks.register(` / `tasks.create(` with **no** `group` and **no** `description` | `group_describe_tasks` | tasks |
| two tasks writing the same `outputs.dir` / `@OutputDirectory` | `use_unique_output_files_and_directories` | tasks |
| `tasks.getByName("…")`, `tasks.named("…")` with a literal name | `dont_hardcode_task_names` | tasks |
| `map {` whose lambda returns a `Provider` | `map_versus_flatmap` | tasks |
| `Property<List<…>>`, `Property<Set<…>>`, `Property<Map<…>>` | `favor_collection_properties` | tasks |
| `org.gradle.jvmargs` without `-Dfile.encoding=UTF-8` | `use_utf8_encoding` | performance |
| **no** `org.gradle.caching=true` | `use_build_cache` | performance |
| **no** `org.gradle.configuration-cache=true` | `use_configuration_cache` | performance |
| `File(…).readText()`, `readLines()` at configuration time | `avoid_computations_in_configuration_phase` | performance |
| `distributionUrl` ending `-all.zip` | `prefer_bin_distribution` | performance |
| `distributionUrl` naming a release older than the current one | `use_latest_minor_versions` | general |
| **no** `distributionSha256Sum` in `gradle-wrapper.properties` | `validate_gradle_checksum` | security |
| `distributionUrl` not on `https://services.gradle.org/` | `validate_wrapper_checksum` | security |
| `exec(`, `ProcessBuilder`, `Runtime.getRuntime().exec` | `run_gradle_on_external_projects` | security |
| `: DefaultTask()`, `extends DefaultTask`, `: Plugin<Project>` with **no** `GradleRunner` anywhere under `src/` | `test_custom_types_with_testkit` | testing |

A row is a pointer, not a verdict. Read the entry before you report: several
rows carry a `When:` precondition that rules the entry out, two rows fire on the
same token for different practices, and one practice can carry two rows
reachable from opposite directions — so that missing one still leaves the other.

**The index is not the catalog.** Three of the 48 entries have no row —
`modularize_builds`, `builds_should_be_reproducible` and
`build-published-artifacts-securely` — because none has a literal token to grep
for; they are judged across the whole tree, or against CI configuration, from
their `heur` recipes. A clean pass over this table is not a clean audit, and
Step 2's category reading is still what decides which entries apply.

Two anchors are hyphenated (`single-gav-string`,
`build-published-artifacts-securely`) where the other 46 use underscores. That is
upstream's own spelling, taken from the category pages, and it is preserved here
because the anchor *is* the documentation URL fragment.
