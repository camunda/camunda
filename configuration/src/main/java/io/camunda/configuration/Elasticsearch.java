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

public class Elasticsearch {

  private static final String PREFIX = "camunda.data.secondary-storage.elasticsearch";
  private static final Set<String> LEGACY_URL_PROPERTIES =
      Set.of(
          "camunda.database.url",
          "camunda.operate.elasticsearch.url",
          "camunda.tasklist.elasticsearch.url");

  /** Endpoint for the Elasticsearch engine configured as secondary storage. */
  private String url = "http://localhost:9200";

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".url",
        url,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_URL_PROPERTIES);
  }

  public void setUrl(String url) {
    this.url = url;
  }

  protected String prefix() {
    return PREFIX;
  }

  protected Set<String> legacyUrlProperties() {
    return LEGACY_URL_PROPERTIES;
  }
}
