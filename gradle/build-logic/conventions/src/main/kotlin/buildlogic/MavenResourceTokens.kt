package buildlogic

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.CopySpec
import org.gradle.kotlin.dsl.getByType

private fun Project.catalogVersion(alias: String): String =
  extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion(alias)
    .get()
    .requiredVersion

/** Applies Maven-style token replacement and tracks its configuration as task inputs. */
fun CopySpec.filterMavenResources(
  task: Task,
  inputName: String,
  tokens: Map<String, String>,
  beginToken: String = "\${",
  endToken: String = "}",
  matching: String? = null,
) {
  task.inputs.property(
    inputName,
    mapOf(
      "tokens" to tokens,
      "beginToken" to beginToken,
      "endToken" to endToken,
      "matching" to (matching ?: ""),
    ),
  )

  if (tokens.isEmpty()) return

  val replacements = tokens.mapKeys { (key, _) -> beginToken + key + endToken }
  val pattern = Regex(replacements.keys.joinToString("|") { Regex.escape(it) })
  val replace =
    Transformer<String?, String> { line ->
      pattern.replace(line!!) { replacements.getValue(it.value) }
    }
  if (matching == null) {
    filter(replace)
  } else {
    filesMatching(matching) { filter(replace) }
  }
}

/** The Maven `${project.version}` token, shared by every module that templates its version. */
fun Project.projectVersionToken(): Map<String, String> =
  mapOf("project.version" to version.toString())

fun Project.optimizeBackendTestResourceTokens(): Map<String, String> =
  mapOf(
    "zeebe.docker.version" to catalogVersion("optimize-zeebe-docker"),
    "database.type" to catalogVersion("optimize-database-type"),
  )

fun Project.zeebeUtilResourceTokens(): Map<String, String> =
  projectVersionToken() + mapOf("backwards.compat.version" to catalogVersion("zeebe-compat"))
