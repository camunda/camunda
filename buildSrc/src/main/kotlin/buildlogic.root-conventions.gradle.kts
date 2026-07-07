/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Root build conventions for repo-wide formatting.
 */

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.provider.Provider

fun Provider<String>.asEnabledFlag(): Provider<Boolean> = map { value ->
  value.isEmpty() || value.toBoolean()
}

plugins {
  base
  id("com.diffplug.spotless")
}

val isCi =
  providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
val quickly = providers.gradleProperty("quickly").asEnabledFlag().orElse(false)

extensions.configure<SpotlessExtension> {
  isEnforceCheck = isCi

  flexmark {
    target("**/*.md")
    targetExclude(
      "**/target/**/*.md",
      "**/node_modules/**/*.md",
      "tasklist/client/**/*",
      "operate/client/**/*",
      "optimize/client/**/*.md",
      "webapp/client/**/*",
      ".github/instructions/**/*.md",
      ".github/skills/**/*.md",
      ".claude/skills/**/*.md",
      ".github/agents/**/*.md",
      "docs/monorepo-docs/**/*.md",
    )
    flexmark()
  }

  kotlin {
    target("**/*.gradle.kts")
    targetExclude(
      "**/build/**",
      "**/target/**",
      // buildscript{} before imports = valid Gradle DSL but invalid Kotlin; ktfmt rejects it
      "zeebe/protocol-asserts/build.gradle.kts",
    )
    ktfmt().googleStyle()
  }

  pom {
    target("pom.xml", "**/pom.xml")
    targetExclude(
      "**/target/**",
      "webapp/client/pom.xml",
      "tasklist/client/pom.xml",
      "identity/client/pom.xml",
      "optimize/client/pom.xml",
    )
    sortPom()
  }
}

tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
  enabled = !quickly.get()
}

// The general unit-test CI job runs `test` across the whole build and excludes the modules that
// have dedicated jobs via `-x :<module>:test`. Aggregator projects (Maven packaging=pom) apply no
// Java convention and therefore have no `test`/`it` task, which makes `-x :<module>:test` fail
// with "Task 'test' not found". Register no-op placeholders on any project missing them so the
// exclusion always resolves. Runs in afterEvaluate so it never collides with the real tasks
// defined by buildlogic.java-conventions.
allprojects {
  afterEvaluate {
    if (tasks.findByName("test") == null) {
      tasks.register("test") {
        group = "verification"
        description = "No-op placeholder (module has no unit tests)"
      }
    }
    if (tasks.findByName("it") == null) {
      tasks.register("it") {
        group = "verification"
        description = "No-op placeholder (module has no integration tests)"
      }
    }
  }
}
