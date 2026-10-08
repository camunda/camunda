# Gradle Best Practices — Catalog Index

> **Provenance record. Not read at run time.** This file exists so the catalog can be
> regenerated and audited, not so a run can consult it. `SKILL.md` carries the applicability
> triage table and the detection layer lives in the category files; a run that reads this
> file spends roughly 7,400 characters and a turn on material it does not use.

**Captured:** 2026-08-27 from `https://docs.gradle.org/current/`, then re-verified and
extended 2026-09-23 against `https://docs.gradle.org/nightly/`.
**Verified:** 2026-09-23 — complete against the Gradle **9.9.0** nightly documentation
(build `9.9.0-20260923002454+0000`). The three entries added in 9.8.0
(`obtain_loggers_via_logging_get_logger`, `favor_collection_properties`,
`build-published-artifacts-securely`) come from the nightly docs and are **not present in
any released Gradle**: `docs.gradle.org/current` was still 9.7.1 on that date. They may
change before 9.8.0 ships.
**Source pages:**

- https://docs.gradle.org/nightly/userguide/best_practices.html
- https://docs.gradle.org/nightly/userguide/best_practices_index.html
- https://docs.gradle.org/nightly/userguide/best_practices_general.html
- https://docs.gradle.org/nightly/userguide/best_practices_structuring_builds.html
- https://docs.gradle.org/nightly/userguide/best_practices_dependencies.html
- https://docs.gradle.org/nightly/userguide/best_practices_tasks.html
- https://docs.gradle.org/nightly/userguide/best_practices_performance.html
- https://docs.gradle.org/nightly/userguide/best_practices_security.html
- https://docs.gradle.org/nightly/userguide/best_practices_testing.html

Anchors below were taken from the category pages themselves. Where the published
index disagreed with the category page, the category page won — the index lists
`best_practices_for_security` for the distribution-checksum entry, while the page
itself uses `validate_gradle_checksum`.

Severity bands (`High` / `Medium` / `Recommendation`) are this skill's editorial
classification. The official documentation assigns none.

## Full catalog

| Title | Category | Anchor | Added in |
|---|---|---|---|
| Use Kotlin DSL | General | `use_kotlin_dsl` | 8.14 |
| Use the Latest Minor Version of Gradle | General | `use_latest_minor_versions` | 8.14 |
| Apply Plugins Using the plugins Block | General | `use_the_plugins_block` | 8.14 |
| Don't Assume your Plugin is Applied after Another | General | `dont_assume_plugin_order` | 9.4.0 |
| Do Not Use Internal APIs | General | `do_not_use_internal_apis` | 8.14 |
| Set build flags in gradle.properties | General | `use_the_gradle_properties_file` | 9.0.0 |
| Name Your Root Project | General | `name_your_root_project` | 9.2.0 |
| Do not use gradle.properties in subprojects | General | `do_not_use_gradle_properties_in_subprojects` | 9.2.0 |
| Avoid afterEvaluate | General | `avoid_after_evaluate` | 9.6.0 |
| Consider use of @Incubating APIs carefully | General | `consider_use_of_incubating_apis_carefully` | 9.7.0 |
| Obtain Loggers via Logging.getLogger(Class) outside of Tasks | General | `obtain_loggers_via_logging_get_logger` | 9.8.0 |
| Modularize Your Builds | Structuring Builds | `modularize_builds` | 9.0.0 |
| Do Not Put Source Files in the Root Project | Structuring Builds | `no_source_in_root` | 9.0.0 |
| Favor build-logic Composite Builds for Build Logic | Structuring Builds | `favor_composite_builds` | 9.0.0 |
| Avoid Unintentionally Creating Empty Projects | Structuring Builds | `avoid_empty_projects` | 9.1.0 |
| Use Convention Plugins for Common Build Logic | Structuring Builds | `use_convention_plugins` | 9.3.0 |
| Declare Dependencies using a single GAV (group:artifact:version) String | Dependencies | `single-gav-string` | 8.14 |
| Use Version Catalogs to Centralize Dependency Versions | Dependencies | `use_version_catalogs` | 9.0.0 |
| Name Version Catalog Entries Appropriately | Dependencies | `name_version_catalog_entries` | 9.0.0 |
| Set up your Dependency Repositories in the Settings file | Dependencies | `set_up_repositories_in_settings` | 9.0.0 |
| Don't Explicitly Depend on the Kotlin Standard Library | Dependencies | `dont_depend_on_kotlin_stdlib` | 9.0.0 |
| Avoid Redundant Dependency Declarations | Dependencies | `avoid_duplicate_dependencies` | 9.0.0 |
| Use Content Filtering with multiple Repositories | Dependencies | `use_content_filtering` | 9.1.0 |
| Apply Exclusions Narrowly | Dependencies | `apply_exclusions_narrowly` | 9.2.0 |
| Always Declare Attributes on Consumable and Resolvable Configurations | Dependencies | `use_attributes_on_configurations` | 9.7.0 |
| Avoid DependsOn | Task | `avoid_depends_on` | 8.14 |
| Favor @CacheableTask and @DisableCachingByDefault over cacheIf(Spec) and doNotCacheIf(String, Spec) | Task | `use_cacheability_annotations` | 8.14 |
| Group and Describe custom Tasks | Task | `group_describe_tasks` | 9.0.0 |
| Do not call get() on a Provider outside a Task action | Task | `avoid_provider_get_outside_task_action` | 9.1.0 |
| Don't resolve Configurations before Task Execution | Task | `dont_resolve_configurations_before_task_execution` | 9.1.0 |
| Avoid using eager APIs on File Collections | Task | `avoid_eager_file_collection_apis` | 9.1.0 |
| Use @PathSensitivity.NONE for file inputs and @PathSensitivity.RELATIVE for directories | Task | `default_path_sensitivities` | 9.2.0 |
| Use unique output files and directories | Task | `use_unique_output_files_and_directories` | 9.3.0 |
| Don't hardcode Task names unless they are documented as Public API | Task | `dont_hardcode_task_names` | 9.7.0 |
| Don't access a Project instance during Task Execution | Task | `dont_access_project_instance_inside_task` | 9.7.0 |
| Wire lazy task outputs using map and flatMap | Task | `map_versus_flatmap` | 9.7.0 |
| Favor collection property types over a Property holding a collection | Task | `favor_collection_properties` | 9.8.0 |
| Use UTF-8 File Encoding | Performance | `use_utf8_encoding` | 9.0.0 |
| Use the Build Cache | Performance | `use_build_cache` | 9.1.0 |
| Use the Configuration Cache | Performance | `use_configuration_cache` | 9.1.0 |
| Avoid Expensive Computations in Configuration Phase | Performance | `avoid_computations_in_configuration_phase` | 9.0.0 |
| Prefer the -bin Gradle Distribution | Performance | `prefer_bin_distribution` | 9.4.0 |
| Validate the Gradle Distribution SHA-256 Checksum | Security | `validate_gradle_checksum` | 9.1.0 |
| Validate the Gradle Wrapper on every Upgrade | Security | `validate_wrapper_checksum` | 9.3.0 |
| Do not Run ./gradlew on Untrusted Projects | Security | `run_gradle_on_external_projects` | 9.7.0 |
| Build Output Should Be Byte-for-Byte Reproducible | Security | `builds_should_be_reproducible` | 9.7.0 |
| Build your Published Artifacts Securely | Security | `build-published-artifacts-securely` | 9.8.0 |
| Test your custom Task and Plugins with TestKit | Testing | `test_custom_types_with_testkit` | 9.4.0 |

**48 entries.** Three of them (`run_gradle_on_external_projects`, `validate_wrapper_checksum`, `build-published-artifacts-securely`) describe operator or CI behaviour rather than a property of the code under audit; each entry says so in its own `When:` clause.
