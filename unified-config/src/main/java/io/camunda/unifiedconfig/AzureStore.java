/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

public class AzureStore {

  public static final String SAS_TOKEN_TYPE_ACCOUNT = "account";
  public static final String SAS_TOKEN_TYPE_SERVICE = "service";

  private String accountKey;
  private String accountName;
  private String basePath;
  private String connectionString;
  private boolean createContainer;
  private String containerName;
  private String endpoint;
  private String sasTokenType;
  private String sasToken;

  public String getContainerName() {
    return containerName;
  }

  public void setContainerName(final String containerName) {
    this.containerName = containerName;
  }

  public String getAccountKey() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.accountKey", accountKey);
  }

  public void setAccountKey(final String accountKey) {
    this.accountKey = accountKey;
  }

  public String getAccountName() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.accountName", accountName);
  }

  public void setAccountName(final String accountName) {
    this.accountName = accountName;
  }

  public String getBasePath() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.basePath", basePath);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public String getConnectionString() {
    return FallbackConfig.getString(
        "zeebe.broker.data.backup.azure.connectionString", connectionString);
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }

  public boolean isCreateContainer() {
    return FallbackConfig.getBoolean(
        "zeebe.broker.data.backup.azure.createContainer", createContainer);
  }

  public void setCreateContainer(final boolean createContainer) {
    this.createContainer = createContainer;
  }

  public String getEndpoint() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.endpoint", endpoint);
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getSasTokenType() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.sasToken.type", sasTokenType);
  }

  public void setSasTokenType(final String sasTokenType) {
    this.sasTokenType = sasTokenType;
  }

  public String getSasToken() {
    return FallbackConfig.getString("zeebe.broker.data.backup.azure.sasToken.value", sasToken);
  }

  public void setSasToken(final String sasToken) {
    this.sasToken = sasToken;
  }
}
