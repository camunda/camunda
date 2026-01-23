/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.ResolvableType;

public abstract class DocumentBasedSecondaryStorageDatabase
    extends SecondaryStorageDatabase<DocumentBasedHistory> {

  /** Prefix to apply to the indexes. */
  private String indexPrefix = "";

  /** Name of the cluster */
  private String clusterName = databaseName().toLowerCase();

  /** The date format for ES and OS */
  private String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

  /** The socket timeout for ES and OS connector */
  private Duration socketTimeout;

  /** The connection timeout for ES and OS connector */
  private Duration connectionTimeout;

  /** How many shards Elasticsearch uses for all Tasklist indices. */
  private int numberOfShards = 1;

  /** How many replicas Elasticsearch uses for all indices. */
  private int numberOfReplicas = 0;

  /** What default refresh interval we will use for all indices */
  private String refreshInterval;

  /** Variable size threshold for the database configured as secondary storage. */
  private int variableSizeThreshold = 8191;

  /** Per-index replica overrides. */
  private Map<String, Integer> numberOfReplicasPerIndex = new HashMap<>();

  /** Per-index shard overrides. */
  private Map<String, Integer> numberOfShardsPerIndex = new HashMap<>();

  /** Per-index refresh interval overrides. */
  private Map<String, String> refreshIntervalByIndexName = new HashMap<>();

  /** Template priority for index templates. */
  private Integer templatePriority;

  @NestedConfigurationProperty
  private SecondaryStorageSecurity security = new SecondaryStorageSecurity(databaseName());

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

  @NestedConfigurationProperty
  private Cache decisionRequirementsCache = new Cache(databaseName(), "decisionRequirements");

  @NestedConfigurationProperty private Cache formCache = new Cache(databaseName(), "form");

  @NestedConfigurationProperty private PostExport postExport = new PostExport(databaseName());

  @NestedConfigurationProperty
  private BatchOperation batchOperations = new BatchOperation(databaseName());

  @NestedConfigurationProperty private Bulk bulk = new Bulk(databaseName());

  @NestedConfigurationProperty
  private DocumentBasedSecondaryStorageBackup backup =
      new DocumentBasedSecondaryStorageBackup(databaseName());

  @NestedConfigurationProperty private ProxyConfiguration proxy = new ProxyConfiguration();

  @Override
  public String getUrl() {
    validateUrlConfiguration();
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".url",
        super.getUrl(),
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUrlProperties());
  }

  @Override
  public List<String> getUrls() {
    validateUrlConfiguration();
    return super.getUrls();
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

  /**
   * Validates that both 'url' and 'urls' are not configured simultaneously. Having both configured
   * is ambiguous and would lead to unexpected behavior.
   *
   * @throws IllegalArgumentException if both url (non-default) and urls (non-empty) are configured
   */
  private void validateUrlConfiguration() {
    final String url = super.getUrl();
    final List<String> urls = super.getUrls();
    final boolean hasNonDefaultUrl = !"http://localhost:9200".equals(url);
    final boolean hasUrls = urls != null && !urls.isEmpty();

    if (hasNonDefaultUrl && hasUrls) {
      throw new IllegalArgumentException(
          "Cannot configure both 'url' and 'urls' for "
              + databaseName()
              + ". Use 'url' for a single node or 'urls' for multiple nodes.");
    }
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

  public Cache getDecisionRequirementsCache() {
    return decisionRequirementsCache;
  }

  public void setDecisionRequirementsCache(final Cache decisionRequirementsCache) {
    this.decisionRequirementsCache = decisionRequirementsCache;
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

  public SecondaryStorageSecurity getSecurity() {
    return security;
  }

  public void setSecurity(final SecondaryStorageSecurity security) {
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

  public String getDateFormat() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".date-format",
        dateFormat,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyDateFormatProperties());
  }

  public void setDateFormat(final String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public Duration getSocketTimeout() {
    final var socketTimeout =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix() + ".socket-timeout",
            this.socketTimeout != null ? Math.toIntExact(this.socketTimeout.toMillis()) : null,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
            legacySocketTimeoutProperties());
    return socketTimeout != null ? Duration.ofMillis(socketTimeout) : null;
  }

  public void setSocketTimeout(final Duration socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Duration getConnectionTimeout() {
    final var connectionTimeoutInt =
        UnifiedConfigurationHelper.validateLegacyConfiguration(
            prefix() + ".connection-timeout",
            connectionTimeout != null ? Math.toIntExact(connectionTimeout.toMillis()) : null,
            Integer.class,
            BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
            legacyConnectionTimeoutProperties());
    return connectionTimeoutInt != null ? Duration.ofMillis(connectionTimeoutInt) : null;
  }

  public void setConnectionTimeout(final Duration connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
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

  public String getRefreshInterval() {
    return refreshInterval;
  }

  public void setRefreshInterval(final String refreshInterval) {
    this.refreshInterval = refreshInterval;
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

  public Map<String, Integer> getNumberOfReplicasPerIndex() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".number-of-replicas-per-index",
        numberOfReplicasPerIndex,
        ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyReplicasByIndexNameProperties());
  }

  public void setNumberOfReplicasPerIndex(final Map<String, Integer> numberOfReplicasPerIndex) {
    this.numberOfReplicasPerIndex = numberOfReplicasPerIndex;
  }

  public Map<String, Integer> getNumberOfShardsPerIndex() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".number-of-shards-per-index",
        numberOfShardsPerIndex,
        ResolvableType.forClassWithGenerics(Map.class, String.class, Integer.class),
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyShardsByIndexNameProperties());
  }

  public void setNumberOfShardsPerIndex(final Map<String, Integer> numberOfShardsPerIndex) {
    this.numberOfShardsPerIndex = numberOfShardsPerIndex;
  }

  public Map<String, String> getRefreshIntervalByIndexName() {
    return refreshIntervalByIndexName;
  }

  public void setRefreshIntervalByIndexName(final Map<String, String> refreshIntervalByIndexName) {
    this.refreshIntervalByIndexName = refreshIntervalByIndexName;
  }

  public Integer getTemplatePriority() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".template-priority",
        templatePriority,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyTemplatePriorityProperties());
  }

  public void setTemplatePriority(final Integer templatePriority) {
    this.templatePriority = templatePriority;
  }

  public DocumentBasedSecondaryStorageBackup getBackup() {
    return backup;
  }

  public void setBackup(final DocumentBasedSecondaryStorageBackup backup) {
    this.backup = backup;
  }

  public ProxyConfiguration getProxy() {
    return proxy;
  }

  public void setProxy(final ProxyConfiguration proxy) {
    this.proxy = proxy;
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

  private Set<String> legacyDateFormatProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.dateFormat",
        "camunda.operate." + dbName + ".dateFormat",
        "camunda.tasklist." + dbName + ".dateFormat",
        "zeebe.broker.exporters.camundaexporter.args.connect.dateFormat");
  }

  private Set<String> legacySocketTimeoutProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.socketTimeout",
        "camunda.operate." + dbName + ".socketTimeout",
        "camunda.tasklist." + dbName + ".socketTimeout",
        "zeebe.broker.exporters.camundaexporter.args.connect.socketTimeout");
  }

  private Set<String> legacyConnectionTimeoutProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.connectionTimeout",
        "camunda.operate." + dbName + ".connectionTimeout",
        "camunda.tasklist." + dbName + ".connectionTimeout",
        "zeebe.broker.exporters.camundaexporter.args.connect.connectionTimeout");
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

  private Set<String> legacyReplicasByIndexNameProperties() {
    return Set.of(
        "camunda.database.index.replicasByIndexName",
        "zeebe.broker.exporters.camundaexporter.args.index.replicasByIndexName");
  }

  private Set<String> legacyShardsByIndexNameProperties() {
    return Set.of(
        "camunda.database.index.shardsByIndexName",
        "zeebe.broker.exporters.camundaexporter.args.index.shardsByIndexName");
  }

  private Set<String> legacyTemplatePriorityProperties() {
    return Set.of(
        "camunda.database.index.templatePriority",
        "zeebe.broker.exporters.camundaexporter.args.index.templatePriority");
  }
}
