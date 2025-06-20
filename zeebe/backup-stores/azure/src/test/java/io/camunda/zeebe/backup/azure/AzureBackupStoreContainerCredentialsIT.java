/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.AccountSasPermission;
import com.azure.storage.common.sas.AccountSasResourceType;
import com.azure.storage.common.sas.AccountSasService;
import com.azure.storage.common.sas.AccountSasSignatureValues;
import io.camunda.unifiedconfig.AzureStore;
import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreContainerCredentialsIT {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  public UnifiedConfiguration config;
  public final String containerName = UUID.randomUUID().toString();

  @Test
  void shouldFailToInitializeStore() {
    // given
    AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setContainerName(containerName);
    azureStoreConfig.setCreateContainer(true);
    azureStoreConfig.setConnectionString(AZURITE_CONTAINER.getConnectString());
    config.getCamunda().getData().getBackup().setAzure(azureStoreConfig);

    // then we should fail to create the store since the container does not
    // exist yet.
    assertThrowsExactly(
        AzureBackupStoreException.ContainerDoesNotExist.class,
        () -> new AzureBackupStore(azureStoreConfig));

    // when we create the container
    new BlobServiceClientBuilder()
        .connectionString(AZURITE_CONTAINER.getConnectString())
        .buildClient()
        .getBlobContainerClient(containerName)
        .create();

    // then we can create the store without any exceptions
    assertDoesNotThrow(() -> new AzureBackupStore(azureStoreConfig));
  }

  // The test for the user delegation token is not present due to the fact that the azurite
  // container does not provide a real user delegation key, since this uses Microsoft Entra
  // credentials in a normal situation, which are not accessible vaia the azurite container.
  // Further explanation along the description of the manual tests to replace this can be found in
  // the PR: https://github.com/camunda/camunda/pull/31494
  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void shouldLoginWithAccountSasToken(final Backup backup) {
    // we create an azure client initially so that we can generate a sas token.
    final BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder()
            .connectionString(AZURITE_CONTAINER.getConnectString())
            .buildClient();

    // Create an account SAS token
    final BlobContainerClient containerClient =
        blobServiceClient.getBlobContainerClient(AZURITE_CONTAINER.getContainerName());
    final String accountSasToken = createAccountSAS(blobServiceClient);

    AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setContainerName(containerName);
    azureStoreConfig.setEndpoint(containerClient.getAccountUrl());
    azureStoreConfig.setSasTokenType(AzureStore.SAS_TOKEN_TYPE_ACCOUNT);
    azureStoreConfig.setSasToken(accountSasToken);
    config.getCamunda().getData().getBackup().setAzure(azureStoreConfig);

    final AzureBackupStore store = new AzureBackupStore(azureStoreConfig);

    // then should be able to upload using the account sas token.
    assertStoreCanUploadAndFetchStatus(backup, store);
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void shouldLoginWithServiceSasToken(final Backup backup) {
    // we create an azure client initially so that we can generate a sas token.
    final BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder()
            .connectionString(AZURITE_CONTAINER.getConnectString())
            .buildClient();

    // Create a service SAS token
    final BlobContainerClient containerClient =
        blobServiceClient.getBlobContainerClient(AZURITE_CONTAINER.getContainerName());
    final String serviceSasToken =
        createServiceSAS(blobServiceClient.getBlobContainerClient(containerName));

    blobServiceClient.createBlobContainer(containerName);

    AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setContainerName(containerName);
    azureStoreConfig.setEndpoint(containerClient.getAccountUrl());
    azureStoreConfig.setSasToken(serviceSasToken);
    azureStoreConfig.setSasTokenType(AzureStore.SAS_TOKEN_TYPE_SERVICE);
    config.getCamunda().getData().getBackup().setAzure(azureStoreConfig);

    final AzureBackupStore store = new AzureBackupStore(azureStoreConfig);

    // then should be able to upload using the service sas token.
    assertStoreCanUploadAndFetchStatus(backup, store);
  }

  public String createAccountSAS(final BlobServiceClient blobServiceClient) {
    final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(5);

    final AccountSasPermission accountSasPermission =
        new AccountSasPermission()
            .setReadPermission(true)
            .setWritePermission(true)
            .setCreatePermission(true);
    final AccountSasService services = new AccountSasService().setBlobAccess(true);
    final AccountSasResourceType resourceTypes =
        new AccountSasResourceType().setService(true).setContainer(true).setObject(true);

    final AccountSasSignatureValues accountSasValues =
        new AccountSasSignatureValues(expiryTime, accountSasPermission, services, resourceTypes);

    return blobServiceClient.generateAccountSas(accountSasValues);
  }

  public String createServiceSAS(final BlobContainerClient containerClient) {
    final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(5);

    final BlobContainerSasPermission sasPermission =
        new BlobContainerSasPermission().setReadPermission(true).setWritePermission(true);

    final BlobServiceSasSignatureValues sasSignatureValues =
        new BlobServiceSasSignatureValues(expiryTime, sasPermission)
            .setStartTime(OffsetDateTime.now().minusMinutes(5));

    return containerClient.generateSas(sasSignatureValues);
  }

  private void assertStoreCanUploadAndFetchStatus(
      final Backup backup, final AzureBackupStore store) {
    store.save(backup).join();
    final var status = store.getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
  }
}
