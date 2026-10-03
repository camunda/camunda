/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Configuration for redacting sensitive process variables on export, proof of concept (product-hub
 * #3805).
 *
 * <p>Maps to the {@code camunda.data.protection} property namespace.
 */
public class Protection {

  /**
   * Regular expression matched against a variable's full name. A variable whose name matches this
   * pattern is replaced with the redaction marker before any exporter receives it.
   */
  private String pattern = "sensitive_.*";

  public String getPattern() {
    return pattern;
  }

  public void setPattern(final String pattern) {
    this.pattern = pattern;
  }
}
