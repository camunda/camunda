/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gradle.pom

private val promotedCheckPattern = Regex("""-Xep:([A-Za-z0-9_]+):ERROR\b""")

/**
 * Returns the Error Prone checks that [compilerArgs] promotes to errors.
 *
 * Maven and Gradle share the `error-prone.checks` property in `parent/pom.xml` as the single source
 * of truth, so promoting a check there applies it to both builds.
 */
fun promotedErrorProneChecks(compilerArgs: String): List<String> =
  promotedCheckPattern.findAll(compilerArgs).map { it.groupValues[1] }.toList()

/**
 * Returns the Error Prone checks promoted to errors by this POM's `error-prone.checks` property.
 */
fun PomResolver.promotedErrorProneChecks(): List<String> =
  promotedErrorProneChecks(resolveProperty("error-prone.checks"))
