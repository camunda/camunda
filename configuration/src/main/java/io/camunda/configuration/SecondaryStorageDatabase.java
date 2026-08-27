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

public abstract class SecondaryStorageDatabase {

  /** Endpoint for the database configured as secondary storage. */
  private String url = "http://localhost:9200";

  /** Name of the cluster */
  private String clusterName = databaseName().toLowerCase();

  @NestedConfigurationProperty private Security security = new Security(databaseName());

  @NestedConfigurationProperty private History history = new History(databaseName());

  /** Username for the database configured as secondary storage. */
  private String username = "";

  /** Password for the database configured as secondary storage. */
  private String password = "";

  /** Prefix to apply to the indexes */
  private String indexPrefix = "";

  /** Total number of connections allowed in the ES and OS connector connection pool. */
  private Integer maxConnections;

  /** Maximum number of connections allowed per route in the ES and OS connector connection pool. */
  private Integer maxConnectionsPerRoute;

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix() + ".url",
        url,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUrlProperties());
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUsername() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix() + ".username",
        username,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyUsernameProperties());
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return UnifiedConfigurationHelper.validateSensitiveLegacyConfiguration(
        prefix() + ".password",
        password,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyPasswordProperties());
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(final Security security) {
    this.security = security;
  }

  public String getClusterName() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
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
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix() + ".index-prefix",
        indexPrefix,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        indexPrefixLegacyProperties());
  }

  public void setIndexPrefix(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  /**
   * Resolved with {@link BackwardsCompatibilityMode#SUPPORTED} rather than the {@code
   * SUPPORTED_ONLY_IF_VALUES_MATCH} its neighbours use. The neighbours all have a non-null default,
   * so a legacy value has something to be compared against; this property does not, and this branch
   * shipped it as a legacy-only property (see #55916). Under {@code SUPPORTED_ONLY_IF_VALUES_MATCH}
   * every deployment that configures only the legacy property would compare it against the unset
   * unified value and fail to start.
   */
  public Integer getMaxConnections() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix() + ".max-connections",
        maxConnections,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyMaxConnectionsProperties());
  }

  public void setMaxConnections(final Integer maxConnections) {
    this.maxConnections = maxConnections;
  }

  /** See {@link #getMaxConnections()} for why this is resolved as SUPPORTED. */
  public Integer getMaxConnectionsPerRoute() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        prefix() + ".max-connections-per-route",
        maxConnectionsPerRoute,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        legacyMaxConnectionsPerRouteProperties());
  }

  public void setMaxConnectionsPerRoute(final Integer maxConnectionsPerRoute) {
    this.maxConnectionsPerRoute = maxConnectionsPerRoute;
  }

  public History getHistory() {
    return history;
  }

  public void setHistory(final History history) {
    this.history = history;
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

  /**
   * Only 'camunda.database', where the properties around it also list 'camunda.operate.*',
   * 'camunda.tasklist.*' and the exporter args. The connection pool was never exposed on the
   * operate and tasklist prefixes. The exporter args are deliberately left out too: those resolve
   * under SUPPORTED, so listing them would let a limit meant for the exporter's client silently
   * size the webapps' client as well.
   *
   * <p>Both spellings are listed because the legacy value is read with an exact-key lookup, while
   * the property is documented (and bound by Spring) in its kebab-case form.
   */
  private Set<String> legacyMaxConnectionsProperties() {
    return Set.of("camunda.database.maxConnections", "camunda.database.max-connections");
  }

  private Set<String> legacyMaxConnectionsPerRouteProperties() {
    return Set.of(
        "camunda.database.maxConnectionsPerRoute", "camunda.database.max-connections-per-route");
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

  protected abstract String databaseName();
}
