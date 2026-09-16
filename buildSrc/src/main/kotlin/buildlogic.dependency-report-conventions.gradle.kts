import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

val dependencyReportProject = project

tasks.register("printGradleDependencyReportEntry") {
  group = "help"
  description = "Prints resolved dependencies for this project as a JSON object."
  notCompatibleWithConfigurationCache("The report resolves configurations during task execution")

  doLast {
    val scope =
      dependencyReportProject.providers
        .gradleProperty("dependency.report.scope")
        .getOrElse("runtime")
    val configurationName =
      when (scope) {
        "compile" -> "compileClasspath"
        "runtime" -> "runtimeClasspath"
        "test" -> "testRuntimeClasspath"
        else -> error("Unsupported dependency report scope: $scope")
      }
    val thirdParty = linkedMapOf<String, String>()
    val directThirdParty = sortedSetOf<String>()
    val internal = sortedSetOf<String>()
    val configuration = dependencyReportProject.configurations.findByName(configurationName)
    if (configuration != null && configuration.isCanBeResolved) {
      val resolution = configuration.incoming.resolutionResult
      val directComponents =
        resolution.root.dependencies
          .filterIsInstance<ResolvedDependencyResult>()
          .map { it.selected.id }
          .toSet()

      resolution.allComponents.forEach { component ->
        when (val identifier = component.id) {
          is ModuleComponentIdentifier -> {
            if (
              identifier.module != "bom" &&
                !identifier.module.endsWith("-bom") &&
                !identifier.module.endsWith("-dependencies")
            ) {
              val coordinate = "${identifier.group}:${identifier.module}"
              thirdParty[coordinate] = identifier.version
              if (identifier in directComponents) {
                directThirdParty.add(coordinate)
              }
            }
          }
          is ProjectComponentIdentifier -> {
            if (identifier.projectPath != dependencyReportProject.path) {
              internal.add(identifier.projectPath.removePrefix(":"))
            }
          }
        }
      }
    }
    println(
      JsonOutput.toJson(
        mapOf(
          "project" to dependencyReportProject.name,
          "directory" to
            dependencyReportProject.rootDir
              .toPath()
              .relativize(dependencyReportProject.projectDir.toPath())
              .toString(),
          "scope" to scope,
          "third_party" to thirdParty,
          "direct_third_party" to directThirdParty.toList(),
          "internal" to internal.toList(),
        )
      )
    )
  }
}
