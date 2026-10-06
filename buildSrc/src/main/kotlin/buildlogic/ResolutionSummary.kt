package buildlogic

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
    fun of(root: ResolvedComponentResult): ResolutionSummary {
      val components = linkedMapOf<ComponentIdentifier, ComponentIdentifier>()

      fun visit(component: ResolvedComponentResult) {
        if (components.putIfAbsent(component.id, component.id) != null) {
          return
        }
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
          visit(it.selected)
        }
      }

      visit(root)
      val directComponents =
        root.dependencies
          .filterIsInstance<ResolvedDependencyResult>()
          .map { it.selected.id }
          .toSet()
      return ResolutionSummary(
        components = components.values.toList(),
        directComponents = directComponents,
      )
    }
  }
}

data class DependencyReportData(
  val thirdParty: Map<String, String>,
  val directThirdParty: List<String>,
  val internal: List<String>,
)

fun ResolvedComponentResult.dependencyReportData(projectPath: String): DependencyReportData {
  val summary = ResolutionSummary.of(this)
  val thirdParty = linkedMapOf<String, String>()
  val directThirdParty = sortedSetOf<String>()
  val internal = sortedSetOf<String>()

  summary.components
    .mapNotNull { it.classify() }
    .forEach { component ->
      when (component) {
        is ClassifiedComponent.Module -> {
          if (!component.isBom) {
            thirdParty[component.coordinate] = component.version
            if (summary.isDirect(component.identifier)) {
              directThirdParty.add(component.coordinate)
            }
          }
        }
        is ClassifiedComponent.Project -> {
          if (component.projectPath != projectPath) {
            internal.add(component.projectPath.removePrefix(":"))
          }
        }
      }
    }

  return DependencyReportData(
    thirdParty = thirdParty,
    directThirdParty = directThirdParty.toList(),
    internal = internal.toList(),
  )
}

fun ResolvedComponentResult.distributionReportArtifacts(
  artifacts: Set<ResolvedArtifactResult>
): List<DistributionDependencyReportArtifact> {
  val summary = ResolutionSummary.of(this)
  return artifacts.map { artifact ->
    val component = artifact.id.componentIdentifier
    val classified = component.classify()
    val coordinate =
      when (classified) {
        is ClassifiedComponent.Module ->
          "${classified.group}:${classified.module}:${classified.version}"
        is ClassifiedComponent.Project -> classified.coordinate
        null -> component.displayName
      }
    DistributionDependencyReportArtifact(
      fileName = artifact.file.name,
      coordinate = coordinate,
      direct = summary.isDirect(component),
      internal = classified is ClassifiedComponent.Project,
    )
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
