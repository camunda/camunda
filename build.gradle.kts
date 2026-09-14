import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PrintGradleProjectInventoryTask : DefaultTask() {
  @get:Input abstract val projectInventory: ListProperty<String>

  @TaskAction
  fun printInventory() {
    projectInventory.get().forEach(::println)
  }
}

plugins { id("buildlogic.root-conventions") }

val gradleProjectInventory =
  subprojects
    .map { project ->
      "${project.name}\t${rootDir.toPath().relativize(project.projectDir.toPath())}"
    }
    .sorted()

tasks.register<PrintGradleProjectInventoryTask>("printGradleProjectInventory") {
  group = "help"
  description = "Prints active Gradle projects and their source directories as TSV."
  projectInventory.set(gradleProjectInventory)
}

val gradleDependencyReportTasks =
  subprojects.map { subproject ->
    subproject.tasks.register("printGradleDependencyReportEntry") {
      doLast {
        val scope = subproject.providers.gradleProperty("dependency.report.scope").getOrElse("runtime")
        val configurationName =
          when (scope) {
            "compile" -> "compileClasspath"
            "runtime" -> "runtimeClasspath"
            "test" -> "testRuntimeClasspath"
            else -> error("Unsupported dependency report scope: $scope")
          }
        val thirdParty = linkedMapOf<String, String>()
        val internal = sortedSetOf<String>()
        val configuration = subproject.configurations.findByName(configurationName)
        if (configuration != null && configuration.isCanBeResolved) {
          configuration.incoming.resolutionResult.allComponents.forEach { component ->
            when (val identifier = component.id) {
              is ModuleComponentIdentifier -> {
                if (
                  identifier.module != "bom" &&
                    !identifier.module.endsWith("-bom") &&
                    !identifier.module.endsWith("-dependencies")
                ) {
                  thirdParty["${identifier.group}:${identifier.module}"] = identifier.version
                }
              }
              is ProjectComponentIdentifier -> {
                if (identifier.projectPath != subproject.path) {
                  internal.add(identifier.projectPath.removePrefix(":"))
                }
              }
            }
          }
        }
        println(
          JsonOutput.toJson(
            mapOf(
              "project" to subproject.name,
              "directory" to rootDir.toPath().relativize(subproject.projectDir.toPath()).toString(),
              "scope" to scope,
              "third_party" to thirdParty,
              "internal" to internal.toList(),
            )
          )
        )
      }
    }
  }

tasks.register("printGradleDependencyReport") {
  notCompatibleWithConfigurationCache("The report resolves configurations from all active Gradle projects")
  group = "help"
  description = "Prints resolved dependencies for all active Gradle projects as JSON lines."
  dependsOn(gradleDependencyReportTasks)
}
