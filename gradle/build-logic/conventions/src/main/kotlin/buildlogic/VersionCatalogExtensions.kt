package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** Reads the required version of a catalog entry, failing if the entry has no version. */
fun VersionCatalog.requiredVersion(alias: String): String = findVersion(alias).get().requiredVersion

/** Maps `io.micrometer:<artifactId>` coordinates to the catalog's micrometer version. */
fun Project.micrometerOptionalDependencies(vararg artifactIds: String): Map<String, String> {
  val version =
    extensions.getByType<VersionCatalogsExtension>().named("libs").requiredVersion("micrometer")
  return artifactIds.associate { "io.micrometer:$it" to version }
}
