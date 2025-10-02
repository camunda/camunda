/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Azure {
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
  @NestedConfigurationProperty private SasToken sasToken;

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

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public boolean isCreateContainer() {
    return createContainer;
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
