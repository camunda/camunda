/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/**
 * Data protection configuration for the variable redaction proof of concept (product-hub #3805).
 */
public record DataProtectionCfg(String pattern) {

  public static final String DEFAULT_PATTERN = "sensitive_.*";

  public DataProtectionCfg(final String pattern) {
    this.pattern = pattern == null ? DEFAULT_PATTERN : pattern;
  }

  public static DataProtectionCfg defaultDataProtectionCfg() {
    return new DataProtectionCfg(null);
  }
}
