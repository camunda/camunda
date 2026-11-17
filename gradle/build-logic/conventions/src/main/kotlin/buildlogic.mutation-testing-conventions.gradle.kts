/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin that adds on-demand PIT mutation testing (the `pitest` task) to a module.
 *
 * The task is never wired into `check`, `test` or any other lifecycle task, so applying this plugin
 * does not change what a regular build does. See docs/testing/mutation-testing.md.
 *
 * Properties, all optional (pass with -P<name>=<value>):
 *   pit.targetClasses        comma-separated class globs to mutate; required unless the module sets
 *                            a default in the `mutationTesting` extension
 *   pit.targetTests          comma-separated test class globs to run (default: PIT derives them)
 *   pit.threads              PIT worker threads (default 8)
 *   pit.timeoutConstant      PIT timeout constant in milliseconds (default: PIT default)
 *   pit.engine               mutation engine, `gregor` or `descartes` (default `gregor`)
 *
 * The property names and defaults match the Maven setup in parent/pom.xml.
 *   pit.crossModule          also mutate the modules registered with `pitMutableCode` (default true)
 */

import buildlogic.requiredVersion
import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask

plugins {
  id("buildlogic.java-conventions")
  id("info.solidsoft.pitest")
}

interface MutationTestingExtension {
  /** Classes mutated when `-Ppit.targetClasses` is not given. Unset: the property is required. */
  val defaultTargetClasses: SetProperty<String>

  /** Extra class globs to exclude, on top of the shared defaults. */
  val excludedClasses: SetProperty<String>

  /** Source directories of the modules registered with `pitMutableCode`, for the HTML report. */
  val crossModuleSourceDirs: ConfigurableFileCollection
}

val mutationTesting = extensions.create<MutationTestingExtension>("mutationTesting")

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun String.csv(): Set<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

fun pitProperty(name: String): Provider<String> =
  providers.gradleProperty("pit.$name").map { it.trim() }.filter { it.isNotEmpty() }

val engine = pitProperty("engine").getOrElse("gregor")
val crossModule = pitProperty("crossModule").map { it.toBoolean() }.getOrElse(true)

// Upstream modules whose classes are mutated together with this module's classes while this
// module's tests run, because those tests cover them far better than the upstream module's own.
// Declare them with `pitMutableCode(project(...))`.
val pitMutableCode by
  configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
  }

extensions.configure<PitestPluginExtension> {
  pitestVersion = versionCatalog.requiredVersion("pitest")
  junit5PluginVersion = versionCatalog.requiredVersion("pitest-junit5-plugin")

  // An empty set is replaced by a clear failure when the task executes, see below.
  targetClasses =
    pitProperty("targetClasses")
      .map { it.csv() }
      .orElse(mutationTesting.defaultTargetClasses)
      .orElse(emptySet())
  targetTests.addAll(pitProperty("targetTests").map { it.csv() }.orElse(emptySet()))

  // Metrics wiring is not worth mutating. Mirrors the surefire excludedGroups in parent/pom.xml.
  excludedClasses = mutationTesting.excludedClasses.map { it + setOf("*Metrics*", "*MetricsDoc*") }
  excludedGroups =
    setOf("performance", "randomized", "slow", "strace", "io.camunda.zeebe.test.util.junit.SlowTest")

  threads = pitProperty("threads").map { it.toInt() }.getOrElse(8)
  pitProperty("timeoutConstant").orNull?.let { timeoutConstInMillis = it.toInt() }
  mutationEngine = engine

  // No exportLineCoverage (hundreds of MB) and no history files: open-source PIT cannot read them
  // without the commercial Arcmutate plugin.
  outputFormats = setOf("XML", "HTML")
  timestampedReports = false

  // Same flags as the test JVMs, see jvm.module.opens in parent/pom.xml.
  jvmArgs =
    listOf(
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
      "--enable-native-access=ALL-UNNAMED",
    )
}

if (engine == "descartes") {
  val descartesVersion = versionCatalog.requiredVersion("pitest-descartes")
  dependencies.add("pitest", "eu.stamp-project:descartes:$descartesVersion")
}

tasks.named<PitestTask>("pitest") {
  if (crossModule) {
    mutableCodePaths.from(pitMutableCode)
    sourceDirs.from(mutationTesting.crossModuleSourceDirs)
  }
  val projectPath = project.path
  val targetClassesSet = targetClasses
  doFirst {
    if (targetClassesSet.get().isEmpty()) {
      throw GradleException(
        "PIT on $projectPath needs an explicit -Ppit.targetClasses=<fqcn-or-glob>[,...]; " +
          "only modules that set mutationTesting.defaultTargetClasses default to a whole-module run"
      )
    }
  }
}
