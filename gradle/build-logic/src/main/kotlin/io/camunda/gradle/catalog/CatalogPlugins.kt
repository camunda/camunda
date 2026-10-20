package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/** Gradle plugins resolved through the catalog. */
internal fun VersionCatalogBuilder.catalogPlugins() {
  plugin("spring-boot", "org.springframework.boot").versionRef("spring-boot")
}
