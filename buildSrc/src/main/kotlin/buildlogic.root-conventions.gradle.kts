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

plugins {
    base
    id("com.diffplug.spotless")
}

val isCi = providers.environmentVariable("CI")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

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
            ".github/agents/**/*.md",
            "docs/monorepo-docs/**/*.md",
        )
        flexmark()
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

