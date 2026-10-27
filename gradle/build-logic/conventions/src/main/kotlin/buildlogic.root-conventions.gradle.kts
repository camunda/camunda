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

import buildlogic.skipWhen
import com.diffplug.gradle.spotless.SpotlessExtension
import io.camunda.gradle.flags.asEnabledFlag
import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty

plugins {
  base
  id("com.diffplug.spotless")
}

val isCi =
  providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
val quickly = providers.gradleProperty("quickly").asEnabledFlag().orElse(false)

// Single source of truth shared with the Maven spotless config.
val ktfmtVersion =
  resolvePomProperty(
    "plugin.version.ktfmt",
    PomResolver(providers.fileContents(layout.settingsDirectory.file("parent/pom.xml")).asText.get())
      .properties(),
  )

extensions.configure<SpotlessExtension> {
  isEnforceCheck = isCi

  flexmark {
    // Directory excludes live on the file tree so the walk skips them. A targetExclude is
    // subtracted from the target after both trees are listed, which still walks every
    // node_modules directory.
    target(
      fileTree(projectDir) {
        include("**/*.md")
        exclude(
          "**/target/**",
          "**/node_modules/**",
          "**/build/**",
          "operate/client/**",
          "webapp/client/**",
        )
      }
    )
    // Keep in sync with the <markdown><excludes> list in the root pom.xml, which is the Maven
    // source of truth. Formatting a file here that Maven excludes makes the two formatters fight.
    targetExclude(
      "optimize/client/**/*.md",
      ".github/instructions/**/*.md",
      ".claude/agents/**/*.md",
      ".claude/skills/**/*.md",
      ".agents/agents/**/*.md",
      ".agents/skills/**/*.md",
      ".github/agents/**/*.md",
      ".github/workflows/**/*.md",
      "load-tests/skills/**/*.md",
      "docs/monorepo-docs/**/*.md",
    )
    flexmark()
  }

  kotlin {
    target(
      fileTree(projectDir) {
        include("**/*.kt")
        exclude("**/build/**", "**/node_modules/**", "**/target/**")
      }
    )
    ktfmt(ktfmtVersion).googleStyle()
  }

  pom {
    target(
      fileTree(projectDir) {
        include("**/pom.xml")
        exclude("**/build/**", "**/node_modules/**", "**/target/**")
      }
    )
    targetExclude(
      "webapp/client/pom.xml",
      "identity/client/pom.xml",
      "optimize/client/pom.xml",
    )
    sortPom()
  }
}

tasks.withType<com.diffplug.gradle.spotless.SpotlessTask>().configureEach {
  skipWhen(quickly)
}
