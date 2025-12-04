/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Azure {
  private static final String PREFIX = "camunda.data.primary-storage.backup.azure";
  private static final Set<Set<String>> LEGACY_ENDPOINT_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_ACCOUNTNAME_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_ACCOUNTKEY_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_CONNECTIONSTRING_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_BASEPATH_PROPERTIES = new LinkedHashSet<>(2);

  private static final Set<Set<String>> LEGACY_CREATECONTAINER_PROPERTIES = new LinkedHashSet<>(2);

  static {
    LEGACY_ENDPOINT_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.endpoint"));
    LEGACY_ENDPOINT_PROPERTIES.add(Set.of("camunda.data.backup.azure.endpoint"));

    LEGACY_ACCOUNTNAME_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.accountName"));
    LEGACY_ACCOUNTNAME_PROPERTIES.add(Set.of("camunda.data.backup.azure.account-name"));

    LEGACY_ACCOUNTKEY_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.accountKey"));
    LEGACY_ACCOUNTKEY_PROPERTIES.add(Set.of("camunda.data.backup.azure.account-key"));

    LEGACY_CONNECTIONSTRING_PROPERTIES.add(
        Set.of("zeebe.broker.data.backup.azure.connectionString"));
    LEGACY_CONNECTIONSTRING_PROPERTIES.add(Set.of("camunda.data.backup.azure.connection-string"));

    LEGACY_BASEPATH_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.basePath"));
    LEGACY_BASEPATH_PROPERTIES.add(Set.of("camunda.data.backup.azure.base-path"));

    LEGACY_CREATECONTAINER_PROPERTIES.add(Set.of("zeebe.broker.data.backup.azure.createContainer"));
    LEGACY_CREATECONTAINER_PROPERTIES.add(Set.of("camunda.data.backup.azure.create-container"));
  }

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

  /**
   * This setting defines the SAS token to use. These can be of user delegation, service or account
   * type. Note that user delegation and service SAS tokens do not support the creation of
   * containers, therefore createContainer configuration will be overridden to false if sasToken is
   * configured. The user must make sure that the container already exists, or it will lead to a
   * runtime error. See more in: <a
   * href="https://learn.microsoft.com/en-us/rest/api/storageservices/delegate-access-with-shared-access-signature">...</a>
   */
  @NestedConfigurationProperty private SasToken sasToken = new SasToken();

  public String getEndpoint() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".endpoint",
        endpoint,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENDPOINT_PROPERTIES);
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccountName() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".account-name",
        accountName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCOUNTNAME_PROPERTIES);
  }

  public void setAccountName(final String accountName) {
    this.accountName = accountName;
  }

  public String getAccountKey() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".account-key",
        accountKey,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCOUNTKEY_PROPERTIES);
  }

  public void setAccountKey(final String accountKey) {
    this.accountKey = accountKey;
  }

  public String getConnectionString() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".connection-string",
        connectionString,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CONNECTIONSTRING_PROPERTIES);
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".base-path",
        basePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BASEPATH_PROPERTIES);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public boolean isCreateContainer() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".create-container",
        createContainer,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_CREATECONTAINER_PROPERTIES);
  }

  public void setCreateContainer(final boolean createContainer) {
    this.createContainer = createContainer;
  }

  public SasToken getSasToken() {
    return sasToken;
  }

  public void setSasToken(final SasToken sasToken) {
    this.sasToken = sasToken;
  }
}
