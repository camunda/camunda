/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

public record AzureBackupConfig(
    String endpoint,
    String accountName,
    String accountKey,
    String connectionString,
    String containerName,
    boolean createContainer,
    String sasToken,
    String accountSasToken) {

  public static class Builder {

    private String endpoint;
    private String accountName;
    private String accountKey;
    private String conectionString;
    private boolean createContainer = true;
    private String sasToken;
    private String accountSasToken;

    // maps to the basePath env variable
    private String containerName;

    public Builder withEndpoint(final String endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public Builder withAccountName(final String accountName) {
      this.accountName = accountName;
      return this;
    }

    public Builder withAccountKey(final String accountKey) {
      this.accountKey = accountKey;
      return this;
    }

    public Builder withConnectionString(final String connectionString) {
      conectionString = connectionString;
      return this;
    }

    public Builder withContainerName(final String containerName) {
      this.containerName = containerName;
      return this;
    }

    public Builder withCreateContainer(final boolean createContainer) {
      this.createContainer = createContainer;
      return this;
    }

    public Builder withSasToken(final String sasToken) {
      this.sasToken = sasToken;
      return this;
    }

    public Builder withAccountSasToken(final String accountSasToken) {
      this.accountSasToken = accountSasToken;
      return this;
    }

    public AzureBackupConfig build() {

      return new AzureBackupConfig(
          endpoint,
          accountName,
          accountKey,
          conectionString,
          containerName,
          createContainer,
          sasToken,
          accountSasToken);
    }
  }
}
