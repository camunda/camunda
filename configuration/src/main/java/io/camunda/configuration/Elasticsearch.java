/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH;

public class Elasticsearch {

  private static final String PREFIX = "camunda.data.secondary-storage.elasticsearch";

  private String url = "http://localhost:9200";

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".url", url, String.class, SUPPORTED_ONLY_IF_VALUES_MATCH);
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
