import buildlogic.ClassifiedComponent
import buildlogic.ResolutionSummary
import buildlogic.classify
import groovy.json.JsonOutput

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
      val summary = ResolutionSummary.of(configuration)

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
              if (component.projectPath != dependencyReportProject.path) {
                internal.add(component.projectPath.removePrefix(":"))
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
