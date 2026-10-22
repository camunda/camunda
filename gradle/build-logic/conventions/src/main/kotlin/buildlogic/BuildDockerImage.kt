package buildlogic

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Builds the Camunda container image from `camunda.Dockerfile` and a distribution tar.
 *
 * The image lives in the container daemon, so the task is only skipped when up to date and is never
 * restored from the build cache. The image id file is the output that records the last build.
 */
abstract class BuildDockerImage @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val distball: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val buildFiles: ConfigurableFileCollection

  @get:Input abstract val image: Property<String>

  /** `docker` or `podman`; `build` is common to both, unlike `buildx`. */
  @get:Input abstract val cli: Property<String>

  /** The Docker build context; [distball] is passed relative to it. */
  @get:Internal abstract val contextDirectory: DirectoryProperty

  @get:OutputFile abstract val imageIdFile: RegularFileProperty

  @TaskAction
  fun build() {
    val context = contextDirectory.get().asFile
    val distballPath = context.toPath().relativize(distball.get().asFile.toPath())
    execOperations.exec {
      workingDir = context
      commandLine(
        cli.get(),
        "build",
        "--target",
        "app",
        "-f",
        "camunda.Dockerfile",
        "-t",
        image.get(),
        "--iidfile",
        imageIdFile.get().asFile.absolutePath,
        "--build-arg",
        "DISTBALL=$distballPath",
        ".",
      )
    }
  }
}
