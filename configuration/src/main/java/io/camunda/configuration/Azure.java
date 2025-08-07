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

public class Azure {
  private static final String PREFIX = "camunda.data.backup.azure.";
  private static final Set<String> LEGACY_ENDPOINT_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.endpoint");
  private static final Set<String> LEGACY_ACCOUNTNAME_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.accountName");
  private static final Set<String> LEGACY_ACCOUNTKEY_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.accountKey");
  private static final Set<String> LEGACY_CONNECTIONSTRING_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.connectionString");
  private static final Set<String> LEGACY_BASEPATH_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.basePath");
  private static final Set<String> LEGACY_CREATECONTAINER_PROPERTIES =
      Set.of("zeebe.broker.data.backup.azure.createContainer");

  /** Azure endpoint to connect to. Required unless a connection string is specified. */
  private String endpoint;

  /**
   * Account name used to authenticate with Azure. Can only be used in combination with an account
   * key. If account credentials or connection string are not provided, authentication will use
   * credentials from the runtime environment: <a
   * href="https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable">...</a>
   */
  private String accountName;

  /**
   * Account key that is used to authenticate with Azure. Can only be used in combination with an
   * account name. If account credentials or connection string are not provided, authentication will
   * use credentials from the runtime environment: <a
   * href="https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable">...</a>
   */
  private String accountKey;

  /**
   * The connection string configures endpoint, account name and account key all at once. If
   * connection string or account credentials are not provided, authentication will use credentials
   * from the runtime environment: <a
   * href="https://learn.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable">...</a>
   */
  private String connectionString;

  /** Defines the container name where backup contents are saved. */
  private String basePath;

  /** Defines the container name where backup contents are saved. */
  private boolean createContainer = true;

  public String getEndpoint() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "endpoint",
        endpoint,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENDPOINT_PROPERTIES);
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccountName() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "account-name",
        accountName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCOUNTNAME_PROPERTIES);
  }

  public void setAccountName(final String accountName) {
    this.accountName = accountName;
  }

  public String getAccountKey() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "account-key",
        accountKey,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCOUNTKEY_PROPERTIES);
  }

  public void setAccountKey(final String accountKey) {
    this.accountKey = accountKey;
  }

  public String getConnectionString() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "connection-string",
        connectionString,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CONNECTIONSTRING_PROPERTIES);
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "base-path",
        basePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BASEPATH_PROPERTIES);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public boolean isCreateContainer() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "create-container",
        createContainer,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CREATECONTAINER_PROPERTIES);
  }

  public void setCreateContainer(final boolean createContainer) {
    this.createContainer = createContainer;
  }
}
