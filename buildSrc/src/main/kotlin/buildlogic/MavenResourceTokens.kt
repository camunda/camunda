package buildlogic

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

private fun Project.catalogVersion(alias: String): String =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion(alias).get().requiredVersion

fun mavenResourceFilterArgs(tokens: Map<String, String>): Map<String, Any> =
    mapOf(
        "tokens" to tokens,
        "beginToken" to "\${",
        "endToken" to "}",
    )

fun Project.clientJavaResourceTokens(): Map<String, String> =
    mapOf("project.version" to version.toString())

fun Project.schemaManagerTestResourceTokens(): Map<String, String> =
    mapOf("project.version" to version.toString())

fun Project.tasklistQaUtilResourceTokens(): Map<String, String> =
    mapOf(
        "version.tasklist.docker.current" to catalogVersion("tasklist-qa-docker-current"),
        "version.tasklist.docker.repo" to catalogVersion("tasklist-qa-docker-repo"),
    )

fun Project.optimizeBackendTestResourceTokens(): Map<String, String> =
    mapOf(
        "zeebe.docker.version" to catalogVersion("optimize-zeebe-docker"),
        "database.type" to catalogVersion("optimize-database-type"),
    )

fun Project.zeebeUtilResourceTokens(): Map<String, String> =
    mapOf(
        "project.version" to version.toString(),
        "backwards.compat.version" to catalogVersion("zeebe-compat"),
    )
