/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import com.google.common.reflect.TypeToken
import java.lang.reflect.Modifier
import javax.inject.Inject
import org.assertj.assertions.generator.AssertionsEntryPointType
import org.assertj.assertions.generator.BaseAssertionGenerator
import org.assertj.assertions.generator.description.converter.ClassToClassDescriptionConverter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@CacheableTask
abstract class GenerateAssertjAssertionsTask : DefaultTask() {
  @get:Classpath abstract val classDirs: ConfigurableFileCollection

  @get:Classpath abstract val classpath: ConfigurableFileCollection

  @get:Classpath abstract val generatorClasspath: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @get:Inject abstract val workerExecutor: WorkerExecutor

  @TaskAction
  fun generate() {
    val classDirectories = classDirs
    val compileClasspath = classpath
    val toolClasspath = generatorClasspath
    val destination = outputDir

    workerExecutor
      .classLoaderIsolation {
        classpath.from(classDirectories, compileClasspath, toolClasspath)
      }
      .submit(GenerateAssertjAssertionsWorkAction::class.java) {
        classDirs.from(classDirectories)
        outputDir.set(destination)
      }
  }
}

interface GenerateAssertjAssertionsParameters : WorkParameters {
  @get:Classpath val classDirs: ConfigurableFileCollection

  @get:OutputDirectory val outputDir: DirectoryProperty
}

abstract class GenerateAssertjAssertionsWorkAction :
  WorkAction<GenerateAssertjAssertionsParameters> {
  override fun execute() {
    val destination = parameters.outputDir.get().asFile
    destination.deleteRecursively()
    destination.mkdirs()

    val classDirFiles = parameters.classDirs.files
    val classNames =
      classDirFiles
        .flatMap { directory ->
          directory
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { file ->
              file
                .relativeTo(directory)
                .invariantSeparatorsPath
                .removeSuffix(".class")
                .replace('/', '.')
            }
            .toList()
        }
        .toSet()
        .filter { it.startsWith("io.camunda.zeebe.protocol.record") }
        .filterNot {
          it.endsWith(".package-info") ||
            it.contains(".Immutable") ||
            it.endsWith("Assert") ||
            it.endsWith("Assertions")
        }

    val loader = javaClass.classLoader
    val types =
      classNames
        .map { loader.loadClass(it) }
        .filterNot { it.simpleName == "package-info" }
        .filterNot { it.isSynthetic }
        // Maven scans dependency JARs for top-level classes; exclude member types from this
        // class-directory scan to keep the generated assertion API aligned with Maven.
        .filter { Modifier.isPublic(it.modifiers) }
        .filterNot { it.isMemberClass || it.isLocalClass || it.isAnonymousClass }
        .map { TypeToken.of(it) }
        .toSet()
    val converter = ClassToClassDescriptionConverter()
    val generator = BaseAssertionGenerator()
    generator.setDirectoryWhereAssertionFilesAreGenerated(destination)

    val descriptions =
      types
        .map { type ->
          val description = converter.convertToClassDescription(type)
          generator.generateCustomAssertionFor(description)
          description
        }
        .toSet()

    generator.generateAssertionsEntryPointClassFor(
      descriptions,
      AssertionsEntryPointType.STANDARD,
      null,
    )
    generator.generateAssertionsEntryPointClassFor(
      descriptions,
      AssertionsEntryPointType.SOFT,
      null,
    )
  }
}
