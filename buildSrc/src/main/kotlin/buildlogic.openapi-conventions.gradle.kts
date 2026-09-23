/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin for modules that generate code from OpenAPI definitions
 */

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins { id("org.openapi.generator") }

val isCi =
  providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)

tasks.withType<GenerateTask>().configureEach {
  workerIsolation.set(if (isCi) "classloader" else "process")
}
