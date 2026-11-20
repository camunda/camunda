/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

private val pluginVersionPattern = Regex("""\d+\.\d+\.\d+\.\d+""")

/**
 * The SpotBugs engine bundled by `spotbugs-maven-plugin`: plugin `x.y.z.w` runs engine `x.y.z`,
 * where `w` is a plugin-only patch level. Fails for any other scheme instead of guessing an engine
 * that would make Gradle report different bugs than Maven.
 */
internal fun spotbugsEngineVersion(mavenPluginVersion: String): String {
  check(pluginVersionPattern.matches(mavenPluginVersion)) {
    "plugin.version.spotbugs '$mavenPluginVersion' is not x.y.z.w; the SpotBugs engine version " +
      "cannot be derived from it. Update spotbugsEngineVersion in buildlogic."
  }
  return mavenPluginVersion.substringBeforeLast('.')
}
