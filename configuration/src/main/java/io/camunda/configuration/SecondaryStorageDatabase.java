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

  /** Name of the cluster */
  private String clusterName = databaseName().toLowerCase();

  // TODO KPO add java doc
  private String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private String fieldDateFormat = "date_time";
  private Integer socketTimeout;
  private Integer connectionTimeout;

  private Security security = new Security(databaseName());

  /** Username for the database configured as secondary storage. */
  private String username = "";

  /** Password for the database configured as secondary storage. */
  private String password = "";

  /** Prefix to apply to the indexes. */
  private String indexPrefix = "";

  /** How many shards Elasticsearch uses for all Tasklist indices. */
  private int numberOfShards = 1;

  public String getUrl() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
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
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
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
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
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

  public String getFieldDateFormat() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".field-date-format",
        fieldDateFormat,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyFieldDateFormatProperties());
  }

  public void setFieldDateFormat(final String fieldDateFormat) {
    this.fieldDateFormat = fieldDateFormat;
  }

  public Integer getSocketTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".socket-timeout",
        socketTimeout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacySocketTimeoutProperties());
  }

  public void setSocketTimeout(final Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectionTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        prefix() + ".connection-timeout",
        connectionTimeout,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        legacyConnectionTimeoutProperties());
  }

  public void setConnectionTimeout(final Integer connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
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

  private Set<String> legacyDateFormatProperties() {
    final String dbName = databaseName().toLowerCase();
    return Set.of(
        "camunda.database.dateFormat",
        "camunda.operate." + dbName + ".dateFormat",
        "camunda.tasklist." + dbName + ".dateFormat",
        "zeebe.broker.exporters.camundaexporter.args.connect.dateFormat");
  }

  private Set<String> legacyFieldDateFormatProperties() {
    return Set.of(
        "camunda.database.fieldDateFormat",
        "zeebe.broker.exporters.camundaexporter.args.connect.fieldDateFormat");
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

  protected abstract String databaseName();
}
