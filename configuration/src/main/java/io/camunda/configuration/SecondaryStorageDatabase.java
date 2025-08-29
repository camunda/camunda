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

  /** Username for the database configured as secondary storage. */
  private String username = null;

  /** Password for the database configured as secondary storage. */
  private String password = null;

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

  protected abstract String databaseName();
}
