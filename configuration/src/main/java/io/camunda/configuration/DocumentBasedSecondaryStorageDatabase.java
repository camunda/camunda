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
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public abstract class DocumentBasedSecondaryStorageDatabase
    extends SecondaryStorageDatabase<DocumentBasedHistory> {

  /** Prefix to apply to the indexes. */
  private String indexPrefix = "";

  /** Name of the cluster */
  private String clusterName = databaseName().toLowerCase();

  /** How many shards Elasticsearch uses for all Tasklist indices. */
  private int numberOfShards = 1;

  /** How many replicas Elasticsearch uses for all indices. */
  private int numberOfReplicas = 0;

  /** Variable size threshold for the database configured as secondary storage. */
  private int variableSizeThreshold = 8191;

  /** Whether to wait for importers before proceeding. */
  private boolean waitForImporters = true;

  @NestedConfigurationProperty private Security security = new Security(databaseName());

  @NestedConfigurationProperty
  private DocumentBasedHistory history = new DocumentBasedHistory(databaseName());

  @Override
  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".url",
        super.getUrl(),
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUrlProperties());
  }

  @Override
  public String getUsername() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".username",
        super.getUsername(),
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUsernameProperties());
  }

  @Override
  public String getPassword() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".password",
        super.getPassword(),
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyPasswordProperties());
  }

  @Override
  public DocumentBasedHistory getHistory() {
    return history;
  }

  @Override
  public void setHistory(final DocumentBasedHistory history) {
    this.history = history;
  }

  @Override
  protected abstract String databaseName();

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(final Security security) {
    this.security = security;
  }

  public String getClusterName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".cluster-name",
        clusterName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyClusterNameProperties());
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public String getIndexPrefix() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".index-prefix",
        indexPrefix,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        indexPrefixLegacyProperties());
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public int getNumberOfShards() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".number-of-shards",
        numberOfShards,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyNumberOfShardsProperties());
  }

  public void setNumberOfShards(final int numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public int getNumberOfReplicas() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".number-of-replicas",
        numberOfReplicas,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyNumberOfReplicasProperties());
  }

  public void setNumberOfReplicas(final int numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  public int getVariableSizeThreshold() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".variable-size-threshold",
        variableSizeThreshold,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyVariableSizeThresholdProperties());
  }

  public void setVariableSizeThreshold(final int variableSizeThreshold) {
    this.variableSizeThreshold = variableSizeThreshold;
  }

  public boolean isWaitForImporters() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".wait-for-importers",
        waitForImporters,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyWaitForImportersProperties());
  }

  public void setWaitForImporters(final boolean waitForImporters) {
    this.waitForImporters = waitForImporters;
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

  private Set<String> legacyClusterNameProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.clusterName",
        "camunda.operate." + dbName + ".clusterName",
        "camunda.tasklist." + dbName + ".clusterName",
        "zeebe.broker.exporters.camundaexporter.args.connect.clusterName");
  }

  private Set<String> legacyUsernameProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.username",
        "camunda.operate." + dbName + ".username",
        "camunda.tasklist." + dbName + ".username",
        "zeebe.broker.exporters.camundaexporter.args.connect.username");
  }

  private Set<String> legacyPasswordProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.password",
        "camunda.operate." + dbName + ".password",
        "camunda.tasklist." + dbName + ".password",
        "zeebe.broker.exporters.camundaexporter.args.connect.password");
  }

  private Set<String> indexPrefixLegacyProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.indexPrefix",
        "camunda.tasklist." + dbName + ".indexPrefix",
        "camunda.operate." + dbName + ".indexPrefix",
        "zeebe.broker.exporters.camundaexporter.args.index.indexPrefix");
  }

  private Set<String> legacyNumberOfShardsProperties() {
    return Set.of(
        "camunda.database.index.numberOfShards",
        "zeebe.broker.exporters.camundaexporter.args.index.numberOfShards");
  }

  private Set<String> legacyNumberOfReplicasProperties() {
    return Set.of(
        "camunda.database.index.numberOfReplicas",
        "zeebe.broker.exporters.camundaexporter.args.index.numberOfReplicas");
  }

  private Set<String> legacyVariableSizeThresholdProperties() {
    return Set.of(
        "camunda.database.index.variableSizeThreshold",
        "zeebe.broker.exporters.camundaexporter.args.index.variableSizeThreshold");
  }

  private Set<String> legacyWaitForImportersProperties() {
    return Set.of(
        "camunda.database.index.shouldWaitForImporters",
        "zeebe.broker.exporters.camundaexporter.args.index.shouldWaitForImporters");
  }
}
