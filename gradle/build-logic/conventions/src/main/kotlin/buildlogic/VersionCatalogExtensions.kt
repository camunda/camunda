package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Reads the required version of a catalog entry, failing if the entry has no version. */
fun VersionCatalog.requiredVersion(alias: String): String = findVersion(alias).get().requiredVersion

/** Maps `io.micrometer:<artifactId>` coordinates to the catalog's micrometer version. */
fun Project.micrometerOptionalDependencies(vararg artifactIds: String): Map<String, String> {
  val version =
    extensions.getByType<VersionCatalogsExtension>().named("libs").requiredVersion("micrometer")
  return artifactIds.associate { "io.micrometer:$it" to version }
}

/**
 * Returns `group:name:version` notations for the given modules, using the versions that [bom]
 * manages, for use with `resolutionStrategy.force`.
 *
 * Add a module here when Maven resolves it to the BOM-managed version but Gradle resolves a higher
 * one: Maven's dependencyManagement overrides what a transitive dependency requests, while a Gradle
 * `platform()` only adds constraints, so the higher requested version wins. Forcing the managed
 * version keeps Gradle aligned with Maven without copying the version into the build.
 *
 * Resolves once, when called, so call it at configuration time and not from a resolution callback.
 * Fails if the BOM does not manage one of the modules.
 */
fun Project.bomManagedForcedModules(
  bom: Provider<MinimalExternalModuleDependency>,
  modules: List<String>,
): Array<String> {
  val managedVersions =
    configurations
      .detachedConfiguration(
        dependencies.platform(bom.get()),
        *modules.map { dependencies.create(it) }.toTypedArray(),
      )
      .incoming
      .resolutionResult
      .allComponents
      .mapNotNull { it.moduleVersion }
      .associate { "${it.group}:${it.name}" to it.version }
  return modules
    .map { module ->
      val version = managedVersions[module] ?: error("${bom.get()} does not manage $module")
      "$module:$version"
    }
    .toTypedArray()
}
