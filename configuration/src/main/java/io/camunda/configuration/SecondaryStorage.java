/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH;

public class SecondaryStorage {

  private static final String PREFIX = "camunda.data.secondary-storage";

  private SecondaryStorage.SecondaryStorageType type;
  private Elasticsearch elasticsearch = new Elasticsearch();

  public SecondaryStorageType getType() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".type", type, SecondaryStorageType.class, SUPPORTED_ONLY_IF_VALUES_MATCH);
  }

  public void setType(SecondaryStorageType type) {
    this.type = type;
  }

  public Elasticsearch getElasticsearch() {
    return elasticsearch;
  }

  public void setElasticsearch(Elasticsearch elasticsearch) {
    this.elasticsearch = elasticsearch;
  }

  public enum SecondaryStorageType {
    elasticsearch,
    opensearch;
  }
}
