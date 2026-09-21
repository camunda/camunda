package buildlogic

import org.gradle.api.artifacts.VersionCatalog

/** Reads the required version of a catalog entry, failing if the entry has no version. */
fun VersionCatalog.requiredVersion(alias: String): String =
    findVersion(alias).get().requiredVersion
