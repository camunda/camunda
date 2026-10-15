/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class GeneratedSourcesJavaExec : JavaExec() {
  @get:OutputDirectory abstract val generatedSourcesDirectory: DirectoryProperty
}

abstract class ClientDiscriminatorArguments : CommandLineArgumentProvider {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val openApiDirectory: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val generatedOpenApiDirectory: DirectoryProperty

  @get:Internal abstract val outputDirectory: DirectoryProperty

  override fun asArguments(): Iterable<String> =
    listOf(
      openApiDirectory.get().asFile.absolutePath,
      generatedOpenApiDirectory
        .get()
        .dir("src/main/java/io/camunda/client/protocol/rest")
        .asFile
        .absolutePath,
      outputDirectory
        .get()
        .dir("src/main/java/io/camunda/client/protocol/rest")
        .asFile
        .absolutePath,
    )
}

abstract class GatewayModelGeneratorArguments : CommandLineArgumentProvider {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val openApiDirectory: DirectoryProperty

  @get:Internal abstract val outputDirectory: DirectoryProperty

  override fun asArguments(): Iterable<String> =
    listOf(openApiDirectory.get().asFile.absolutePath, outputDirectory.get().asFile.absolutePath)
}
