package buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Attribute

/**
 * Identifies which non-default artifact a project exposes (test classes, class directories,
 * generated assertions, ...). Producers tag their consumable configuration with it and consumers
 * request it by value, so variant selection never depends on a configuration's name.
 */
val ARTIFACT_KIND: Attribute<String> = Attribute.of("io.camunda.build.artifact-kind", String::class.java)

object ArtifactKind {
  const val TESTS = "tests"
  const val MAIN_CLASSES = "main-classes"
  const val MAIN_OUTPUT = "main-output"
  const val GENERATED_ASSERTIONS = "generated-assertions"
  const val DISTRIBUTION_RESOURCES = "distribution-resources"
}

/**
 * Exposes a consumable configuration as the [kind] artifact of [project]: it carries the
 * [ARTIFACT_KIND] attribute and a `<project>-<kind>` capability. The capability keeps the default
 * jar variants out of the selection when a consumer asks for this one, which an extra attribute
 * alone does not.
 */
fun Configuration.artifactKind(project: Project, kind: String) {
  attributes.attribute(ARTIFACT_KIND, kind)
  outgoing.capability("${project.group}:${project.name}-$kind:${project.version}")
}

/** Depends on the [kind] artifact of the project at [path] instead of its default variant. */
fun DependencyHandler.projectArtifact(path: String, kind: String): Dependency =
  (project(mapOf("path" to path)) as ModuleDependency).apply {
    attributes { attribute(ARTIFACT_KIND, kind) }
    capabilities { requireCapability("io.camunda:${path.substringAfterLast(':')}-$kind") }
  }
