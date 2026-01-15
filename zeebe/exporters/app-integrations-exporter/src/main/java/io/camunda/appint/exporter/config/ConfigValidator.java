/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.appint.exporter.config;

public class ConfigValidator {

  public static void validate(final Config config) {
    if (config == null) {
      throw new IllegalArgumentException("Configuration cannot be null.");
    }
    if (config.getUrl() == null || config.getUrl().isBlank()) {
      throw new IllegalArgumentException("Url cannot be null or blank.");
    }
  }
}
