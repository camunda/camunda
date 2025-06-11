/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import io.camunda.unifiedconfig.AzureStore;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigIT {

  private static final String VALID_ACCOUNT_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
  private static final String VALID_ACCOUNT_NAME = "devstoreaccount1";
  private static final String VALID_ENDPOINT = "https://127.0.0.1";
  private static final String VALID_CONNECTION_STRING = "DefaultEndpointsProtocol=http;"
          + "AccountName=" + VALID_ACCOUNT_NAME + ";"
          + "AccountKey=" + VALID_ACCOUNT_KEY + ";"
          + "BlobEndpoint=" + VALID_ENDPOINT + ":";

  @Test
  void shouldSuccessfullyValidateCredentialsConfig() {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setEndpoint(VALID_ENDPOINT + ":10000/" + VALID_ACCOUNT_NAME);
    azureStoreConfig.setAccountKey(VALID_ACCOUNT_KEY);
    azureStoreConfig.setAccountName(VALID_ACCOUNT_NAME);
    azureStoreConfig.setContainerName(UUID.randomUUID().toString());

    Assertions.assertThatCode(() -> new AzureBackupStore(azureStoreConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldValidateConnectionStringConfig() {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setConnectionString(VALID_CONNECTION_STRING);
    azureStoreConfig.setContainerName(UUID.randomUUID().toString());

    Assertions.assertThatCode(() -> new AzureBackupStore(azureStoreConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailDueToMissingEndpointOnAutoAuthConfig() {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setContainerName(UUID.randomUUID().toString());

    Assertions.assertThatCode(() -> new AzureBackupStore(azureStoreConfig))
        .hasMessage("The Azure Storage endpoint url is malformed.");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .hasMessage("Connection string or endpoint is required");
  }

  @Test
  void shouldValidateAutoAuthConfig() {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setEndpoint(VALID_ENDPOINT);
    azureStoreConfig.setContainerName(UUID.randomUUID().toString());

    Assertions.assertThatCode(() -> new AzureBackupStore(azureStoreConfig))
        .doesNotThrowAnyException();
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailValidationBecauseOfMissingConnectionString() {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setConnectionString(null);
    azureStoreConfig.setContainerName(UUID.randomUUID().toString());

    Assertions.assertThatCode(() -> new AzureBackupStore(azureStoreConfig))
        .hasMessage("The Azure Storage endpoint url is malformed.");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .hasMessage("Connection string or endpoint is required");
  }

  @Test
  void shouldFailValidationWithPartialAccountCredentials() {
   final  AzureStore config1 = new AzureStore();
    config1.setEndpoint("test");
    config1.setAccountKey("key");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(config1))
        .hasMessage("Account key is specified but account name is missing");

    final AzureStore config2 = new AzureStore();
    config2.setEndpoint("test");
    config2.setAccountName("name");
    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(config2))
        .hasMessage("Account name is specified but account key is missing");
  }

  @Test
  void containerNameCannotBeMissing() {
    // given
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setConnectionString(VALID_CONNECTION_STRING);
    azureStoreConfig.setContainerName(null);

    Assertions.assertThatCode(() -> AzureBackupStore.validateConfig(azureStoreConfig))
        .hasMessage("Container name cannot be null.");
  }
}
