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

  /** Prefix to apply to the indexes */
  private String indexPrefix = "";

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".url",
        url,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUrlProperties());
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getIndexPrefix() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".index-prefix",
        indexPrefix,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        indexPrefixLegacyProperties());
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  private String prefix() {
    return "camunda.data.secondary-storage." + databaseName().toLowerCase();
  }

  private Set<String> legacyUrlProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.url",
        "camunda.operate." + dbName + ".url",
        "camunda.tasklist." + dbName + ".url",
        "zeebe.broker.exporters.camundaexporter.args.connect.url");
  }

  private Set<String> indexPrefixLegacyProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.indexPrefix",
        "camunda.tasklist." + dbName + ".indexPrefix",
        "camunda.operate." + dbName + ".indexPrefix",
        "zeebe.broker.exporters.camundaexporter.args.index.indexPrefix");
  }

  protected abstract String databaseName();
}
