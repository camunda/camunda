import org.gradle.api.DefaultTask
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
