/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.ArrayList;
import java.util.List;
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

  @NestedConfigurationProperty private Security security = new Security(databaseName());

  /** Sets the interceptor plugins */
  private List<InterceptorPlugin> interceptorPlugins = new ArrayList<>();

  @NestedConfigurationProperty
  private DocumentBasedHistory history = new DocumentBasedHistory(databaseName());

  /** Whether to create the schema automatically */
  private boolean createSchema = true;

  @NestedConfigurationProperty
  private IncidentNotifier incidentNotifier = new IncidentNotifier(databaseName());

  @NestedConfigurationProperty
  private Cache batchOperationCache = new Cache(databaseName(), "batchOperation");

  @NestedConfigurationProperty private Cache processCache = new Cache(databaseName(), "process");

  @NestedConfigurationProperty private Cache formCache = new Cache(databaseName(), "form");

  @NestedConfigurationProperty private PostExport postExport = new PostExport(databaseName());

  @NestedConfigurationProperty
  private BatchOperation batchOperations = new BatchOperation(databaseName());

  @NestedConfigurationProperty private Bulk bulk = new Bulk(databaseName());

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

  public boolean isCreateSchema() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".create-schema",
        createSchema,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        Set.of("zeebe.broker.exporters.camundaexporter.args.createSchema"));
  }

  public void setCreateSchema(final boolean createSchema) {
    this.createSchema = createSchema;
  }

  public Cache getBatchOperationCache() {
    return batchOperationCache;
  }

  public void setBatchOperationCache(final Cache batchOperationCache) {
    this.batchOperationCache = batchOperationCache;
  }

  public Cache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final Cache processCache) {
    this.processCache = processCache;
  }

  public Cache getFormCache() {
    return formCache;
  }

  public void setFormCache(final Cache formCache) {
    this.formCache = formCache;
  }

  public PostExport getPostExport() {
    return postExport;
  }

  public void setPostExport(final PostExport postExport) {
    this.postExport = postExport;
  }

  public BatchOperation getBatchOperations() {
    return batchOperations;
  }

  public void setBatchOperations(final BatchOperation batchOperations) {
    this.batchOperations = batchOperations;
  }

  public Bulk getBulk() {
    return bulk;
  }

  public void setBulk(final Bulk bulk) {
    this.bulk = bulk;
  }

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

  public List<InterceptorPlugin> getInterceptorPlugins() {
    return interceptorPlugins;
  }

  public void setInterceptorPlugins(final List<InterceptorPlugin> interceptorPlugins) {
    this.interceptorPlugins = interceptorPlugins;
  }

  public IncidentNotifier getIncidentNotifier() {
    return incidentNotifier;
  }

  public void setIncidentNotifier(final IncidentNotifier incidentNotifier) {
    this.incidentNotifier = incidentNotifier;
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
}
