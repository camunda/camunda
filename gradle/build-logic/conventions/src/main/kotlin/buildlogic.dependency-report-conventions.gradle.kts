import buildlogic.DependencyReportData
import buildlogic.PrintGradleDependencyReportTask
import buildlogic.dependencyReportData

val dependencyReportScope = providers.gradleProperty("dependency.report.scope").orElse("runtime")

tasks.register<PrintGradleDependencyReportTask>("printGradleDependencyReportEntry") {
  group = "help"
  description = "Prints resolved dependencies for this project as a JSON object."

  val scope = dependencyReportScope.get()
  val configurationName =
    when (scope) {
      "compile" -> "compileClasspath"
      "runtime" -> "runtimeClasspath"
      "test" -> "testRuntimeClasspath"
      else -> error("Unsupported dependency report scope: $scope")
    }
  val configuration = configurations.findByName(configurationName)
  val reportData =
    if (configuration != null && configuration.isCanBeResolved) {
      configuration.incoming.resolutionResult.rootComponent.map { root ->
        root.dependencyReportData(project.path)
      }
    } else {
      providers.provider {
        DependencyReportData(emptyMap(), emptyList(), emptyList())
      }
    }

  reportScope.set(scope)
  projectName.set(project.name)
  projectDirectory.set(
    project.rootDir.toPath().relativize(project.projectDir.toPath()).toString()
  )
  thirdParty.set(reportData.map { it.thirdParty })
  directThirdParty.set(reportData.map { it.directThirdParty })
  internal.set(reportData.map { it.internal })
}
