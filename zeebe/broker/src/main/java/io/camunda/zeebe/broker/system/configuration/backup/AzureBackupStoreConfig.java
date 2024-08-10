/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.azure.AzureBackupConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.util.Objects;

public class AzureBackupStoreConfig implements ConfigurationEntry {
  private String endpoint;
  private String accountName;
  private String accountKey;
  private String connectionString;
  private String basePath;

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(final String accountName) {
    this.accountName = accountName;
  }

  public String getAccountKey() {
    return accountKey;
  }

  public void setAccountKey(final String accountKey) {
    this.accountKey = accountKey;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public void setConnectionString(final String algorithm) {
    if (Objects.equals(algorithm, "none")) {
      connectionString = null;
    } else {
      connectionString = algorithm;
    }
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public static AzureBackupConfig toStoreConfig(final AzureBackupStoreConfig config) {
    return new AzureBackupConfig.Builder()
        .withEndpoint(config.getEndpoint())
        .withAccountName(config.getAccountName())
        .withAccountKey(config.getAccountKey())
        .withConnectionString(config.getConnectionString())
        .withContainerName(config.getBasePath())
        .build();
  }

  @Override
  public int hashCode() {
    return Objects.hash(endpoint, accountName, accountKey, connectionString, basePath);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AzureBackupStoreConfig that = (AzureBackupStoreConfig) o;
    return Objects.equals(endpoint, that.endpoint)
        && Objects.equals(accountName, that.accountName)
        && Objects.equals(accountKey, that.accountKey)
        && Objects.equals(connectionString, that.connectionString)
        && Objects.equals(basePath, that.basePath);
  }

  @Override
  public String toString() {
    return "AzureBackupStoreConfig{"
        + "endpoint='"
        + endpoint
        + '\''
        + ", accountName='"
        + accountName
        + '\''
        + ", accountKey='"
        + "<redacted>"
        + '\''
        + ", connectionString='"
        + "<redacted>"
        + '\''
        + ", basePath='"
        + basePath
        + '\''
        + '}';
  }
}
