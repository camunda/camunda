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

  /** Cluster name of the secondary-storage database. */
  private String clusterName = defaultClusterName();

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

  public String getClusterName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".cluster-name",
        clusterName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyClusterNameProperties());
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  private String prefix() {
    return "camunda.data.secondary-storage." + databaseName();
  }

  private Set<String> legacyUrlProperties() {
    return Set.of(
        "camunda.database.url",
        "camunda.operate." + databaseName() + ".url",
        "camunda.tasklist." + databaseName() + ".url",
        "zeebe.broker.exporters.camundaexporter.args.connect.url");
  }

  private Set<String> legacyClusterNameProperties() {
    return Set.of(
        "camunda.operate." + databaseName() + ".clusterName",
        "camunda.tasklist." + databaseName() + ".clusterName"
    );
  }

  private String defaultClusterName() {
    return databaseName();
  }

  /** Name of the secondary-storage database, all lowercase. */
  protected abstract String databaseName();
}
