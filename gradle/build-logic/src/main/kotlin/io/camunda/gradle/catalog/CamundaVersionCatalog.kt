package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/**
 * Populates the `libs` version catalog.
 *
 * Versions are resolved by the caller from Maven POM properties, so Maven stays the single source
 * of truth. The catalog contents are split by concern across the `*Libraries.kt` files.
 */
fun VersionCatalogBuilder.camundaCatalog(
  pomVersion: (String) -> String,
  optimizePomVersion: (String) -> String,
  starterPomVersion: (String) -> String,
  langchain4jVersion: String,
) {
  catalogVersions(pomVersion, optimizePomVersion, starterPomVersion, langchain4jVersion)
  catalogParentVersions(pomVersion, optimizePomVersion)
  catalogDatabaseLibraries()
  catalogSpringBootLibraries()
  catalogSpringFrameworkLibraries()
  catalogSecurityLibraries()
  catalogCloudLibraries()
  catalogTestingLibraries()
  catalogPlatformLibraries()
  catalogCoreLibraries()
  catalogPlugins()
}
