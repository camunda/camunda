/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin for modules that generate code from SBE (Simple Binary Encoding) definitions
 */

import buildlogic.SbeOutputDirArgument
import buildlogic.SbeSchemaArguments
import buildlogic.requiredVersion
import org.gradle.api.provider.ListProperty

plugins { id("buildlogic.server-conventions") }

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val sbeToolVersion = versionCatalog.requiredVersion("uk-co-real-logic-sbe-tool")

// Extension to configure SBE schema files and any additional files they reference.
interface SbeExtension {
  val schemaFiles: ConfigurableFileCollection
  val additionalInputFiles: ConfigurableFileCollection

  /**
   * Generated files, relative to the SBE output directory, to remove after generation. SBE emits a
   * `package-info.java` for every schema package; when the module also has a hand-written
   * `package-info.java` in that package, the duplicate breaks compilation. Maven removes the
   * generated file with antrun; here the generator removes it itself so its declared output stays
   * stable and the task can be up-to-date/cached.
   */
  val generatedFilesToDelete: ListProperty<String>
}

val sbeExtension = extensions.create<SbeExtension>("sbe")

val generateSbe =
  tasks.register<JavaExec>("generateSbe") {
    group = "sbe"
    description = "Generate Java code from SBE message schemas"

    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    classpath = configurations.getByName("sbeTool")

    val outputDir = layout.buildDirectory.dir("generated-sources/sbe")

    // Configure inputs and outputs for caching
    inputs
      .files(sbeExtension.schemaFiles, sbeExtension.additionalInputFiles)
      .withPropertyName("sbeInputFiles")
      .withPathSensitivity(PathSensitivity.RELATIVE)

    argumentProviders.add(
      objects.newInstance<SbeSchemaArguments>().apply { schemaFiles.from(sbeExtension.schemaFiles) }
    )
    outputs.dir(outputDir).withPropertyName("sbeOutputDir")

    // JavaExec is not cacheable by default; opt in so unchanged inputs hit the
    // build cache across fresh CI checkouts instead of regenerating every run.
    outputs.cacheIf { true }

    // JVM arguments to allow access to internal JDK APIs required by Agrona
    jvmArgs(
      "--add-opens",
      "java.base/jdk.internal.misc=ALL-UNNAMED",
      "--add-exports",
      "java.base/jdk.internal.misc=ALL-UNNAMED",
    )

    jvmArgumentProviders.add(
      objects.newInstance<SbeOutputDirArgument>().apply { outputDirectory.set(outputDir) }
    )

    // System properties for SBE tool configuration
    systemProperty("sbe.java.generate.interfaces", "true")
    systemProperty("sbe.decode.unknown.enum.values", "true")
    systemProperty("sbe.xinclude.aware", "true")
    systemProperty("sbe.generate.ir", "true")

    val filesToDelete = sbeExtension.generatedFilesToDelete
    inputs.property("sbeGeneratedFilesToDelete", filesToDelete)

    doFirst {
      // SBE does not remove files for schemas that were deleted or renamed. Clean the generated
      // Java directory before each execution so removed types cannot survive incrementally.
      outputDir.get().asFile.deleteRecursively()
      outputDir.get().asFile.mkdirs()
    }

    // Remove colliding generated files as part of the generation itself, so the declared output
    // directory only ever contains what this task produces.
    doLast {
      val outDir = outputDir.get().asFile
      filesToDelete.get().forEach { relativePath -> outDir.resolve(relativePath).delete() }
    }
  }

// Add SBE tool to dedicated configuration for code generation
val sbeTool =
  configurations.create("sbeTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }

dependencies { sbeTool("uk.co.real-logic:sbe-tool:$sbeToolVersion") }

// Add generated Java sources to the source set; the task output carries its dependency.
sourceSets { main { java { srcDir(generateSbe) } } }

// Package generated SBE IR and schema resources like Maven does.
tasks.named<ProcessResources>("processResources") {
  from(generateSbe) {
    into("sbe")
    include("**/*.sbeir")
    include("**/*.xml")
  }
}
