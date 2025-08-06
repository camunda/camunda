/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public abstract class SecondaryStorageDatabase {

  /** Endpoint for the database configured as secondary storage. */
  private String url = "http://localhost:9200";

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".url",
        url,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyUrlProperties());
  }

  public void setUrl(String url) {
    this.url = url;
  }

  protected abstract String prefix();
  protected abstract Set<String> legacyUrlProperties();
}
