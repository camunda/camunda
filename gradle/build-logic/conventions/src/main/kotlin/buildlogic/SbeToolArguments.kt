package buildlogic

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Program arguments for the SbeTool: the schema files. Passing them through a provider with
 * relative-path-sensitive inputs, instead of `JavaExec.args`, keeps the task cache key independent
 * of the checkout location, since plain arguments are part of the key as absolute paths.
 */
abstract class SbeSchemaArguments : CommandLineArgumentProvider {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val schemaFiles: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> = schemaFiles.files.map { it.absolutePath }
}

/**
 * JVM argument telling the SbeTool where to write. The output directory is declared as a task
 * output separately, so the absolute path is deliberately not part of the cache key.
 */
abstract class SbeOutputDirArgument : CommandLineArgumentProvider {
  @get:Internal abstract val outputDirectory: DirectoryProperty

  override fun asArguments(): Iterable<String> =
    listOf("-Dsbe.output.dir=${outputDirectory.get().asFile.absolutePath}")
}
