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

plugins {
    id("buildlogic.java-conventions")
}

// Extension to configure SBE input files
interface SbeExtension {
    val inputFiles: ConfigurableFileCollection
}

val sbeExtension = extensions.create<SbeExtension>("sbe")

val generateSbe = tasks.register<JavaExec>("generateSbe") {
    group = "sbe"
    description = "Generate Java code from SBE message schemas"

    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    classpath = configurations.getByName("sbeTool")

    val outputDir = layout.buildDirectory.dir("generated-sources/sbe")
    val workingDir = layout.buildDirectory.dir("generated-sources")

    // Configure inputs and outputs for caching
    inputs.files(sbeExtension.inputFiles)
        .withPropertyName("sbeInputFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir)
        .withPropertyName("sbeOutputDir")

    // JVM arguments to allow access to internal JDK APIs required by Agrona
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"
    )

    // System properties for SBE tool configuration
    systemProperty("sbe.output.dir", outputDir.get().asFile.absolutePath)
    systemProperty("sbe.java.generate.interfaces", "true")
    systemProperty("sbe.decode.unknown.enum.values", "true")
    systemProperty("sbe.xinclude.aware", "true")
    systemProperty("sbe.generate.ir", "true")

    workingDir(workingDir)

    // Arguments will be configured per module (the XML schema files)
    // args will be set in individual build.gradle.kts files

    doFirst {
        outputDir.get().asFile.mkdirs()
        workingDir.get().asFile.mkdirs()
    }
}

// Add SBE tool to dedicated configuration for code generation
val sbeTool by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    sbeTool("uk.co.real-logic:sbe-tool:1.37.1")
}

// Add generated sources to the source set
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-sources/sbe"))
        }
    }
}

// Make compileJava depend on SBE generation
tasks.named("compileJava") {
    dependsOn(generateSbe)
}
