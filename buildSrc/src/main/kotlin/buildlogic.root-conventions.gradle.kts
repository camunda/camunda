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
import io.camunda.gradle.flags.asEnabledFlag

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
    // Keep in sync with the <markdown><excludes> list in the root pom.xml, which is the Maven
    // source of truth. Formatting a file here that Maven excludes makes the two formatters fight.
    targetExclude(
      "**/target/**/*.md",
      "**/node_modules/**/*.md",
      "operate/client/**/*",
      "optimize/client/**/*.md",
      "webapp/client/**/*",
      ".github/instructions/**/*.md",
      ".claude/agents/**/*.md",
      ".claude/skills/**/*.md",
      ".github/agents/**/*.md",
      ".github/workflows/**/*.md",
      "load-tests/skills/**/*.md",
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
      "identity/client/pom.xml",
      "optimize/client/pom.xml",
    )
    sortPom()
  }
}

tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
  enabled = !quickly.get()
}
