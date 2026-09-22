package buildlogic

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Resolved view of a configuration shared by the dependency-report conventions.
 *
 * The module report and the distribution report walk the same resolution graph and classify its
 * components the same way. Keeping that here stops the two classifiers from drifting apart.
 */
class ResolutionSummary(
  val components: List<ComponentIdentifier>,
  val directComponents: Set<ComponentIdentifier>,
) {
  fun isDirect(component: ComponentIdentifier): Boolean = component in directComponents

  companion object {
    fun of(configuration: Configuration): ResolutionSummary {
      val resolution = configuration.incoming.resolutionResult
      val directComponents =
        resolution.root.dependencies
          .filterIsInstance<ResolvedDependencyResult>()
          .map { it.selected.id }
          .toSet()
      return ResolutionSummary(
        components = resolution.allComponents.map { it.id },
        directComponents = directComponents,
      )
    }
  }
}

/** A resolved component classified into the coordinate shapes the dependency reports emit. */
sealed interface ClassifiedComponent {
  val identifier: ComponentIdentifier

  data class Module(override val identifier: ModuleComponentIdentifier) : ClassifiedComponent {
    val group: String
      get() = identifier.group

    val module: String
      get() = identifier.module

    val version: String
      get() = identifier.version

    val coordinate: String
      get() = "$group:$module"

    /** Platform/BOM imports carry no runtime classes; they are not reported as dependencies. */
    val isBom: Boolean
      get() = module == "bom" || module.endsWith("-bom") || module.endsWith("-dependencies")
  }

  data class Project(override val identifier: ProjectComponentIdentifier) : ClassifiedComponent {
    val projectPath: String
      get() = identifier.projectPath

    val coordinate: String
      get() = "project:$projectPath"
  }
}

fun ComponentIdentifier.classify(): ClassifiedComponent? =
  when (this) {
    is ModuleComponentIdentifier -> ClassifiedComponent.Module(this)
    is ProjectComponentIdentifier -> ClassifiedComponent.Project(this)
    else -> null
  }
