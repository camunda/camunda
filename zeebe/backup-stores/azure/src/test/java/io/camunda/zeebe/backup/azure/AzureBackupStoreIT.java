/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.azure.util.AzuriteContainer;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AzureBackupStoreIT implements BackupStoreTestKit {

  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  public AzureBackupConfig azureBackupConfig;
  public AzureBackupStore azureBackupStore;
  public final String containerName = UUID.randomUUID().toString();

  @BeforeEach
  public void setUpBlobClient() {
    azureBackupConfig =
        new AzureBackupConfig.Builder()
            .withConnectionString(AZURITE_CONTAINER.getConnectString())
            .withContainerName(containerName)
            .build();
    azureBackupStore = new AzureBackupStore(azureBackupConfig);
  }

  @Override
  public AzureBackupStore getStore() {
    return azureBackupStore;
  }

  @Override
  public Class<? extends Exception> getBackupInInvalidStateExceptionClass() {
    return UnexpectedManifestState.class;
  }

  @Override
  public Class<? extends Exception> getFileNotFoundExceptionClass() {
    return FileNotFoundException.class;
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void backupShouldExistAfterStoreIsClosed(final Backup backup) {
    // given
    getStore().save(backup).join();
    final var firstStatus = getStore().getStatus(backup.id()).join();

    // when
    getStore().closeAsync().join();
    setUpBlobClient();

    // then
    final var status = getStore().getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
    assertThat(status.lastModified()).isEqualTo(firstStatus.lastModified());
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void cannotDeleteUploadingBlock(final Backup backup) {

    // given when
    uploadInProgressManifest(backup);

    // then
    Assertions.assertThat(getStore().delete(backup.id()))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(UnexpectedManifestState.class)
        .withMessageContaining(
            "Cannot delete Backup with id "
                + "'BackupIdentifierImpl[nodeId=1, partitionId=2, checkpointId=3]' "
                + "while saving is in progress.");
  }

  @ParameterizedTest
  @ArgumentsSource(TestBackupProvider.class)
  void cannotRestoreUploadingBackup(final Backup backup, @TempDir final Path targetDir) {
    // when
    uploadInProgressManifest(backup);

    // then
    Assertions.assertThat(getStore().restore(backup.id(), targetDir))
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableOfType(Throwable.class)
        .withRootCauseInstanceOf(UnexpectedManifestState.class)
        .withMessageContaining(
            "Expected to restore from completed backup with id "
                + "'BackupIdentifierImpl[nodeId=1, partitionId=2, checkpointId=3]', "
                + "but was in state 'IN_PROGRESS'");
  }

  void uploadInProgressManifest(final Backup backup) {
    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;

    try {
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final BlobClient blobClient = buildBlobClient(manifest);
    blobClient.upload(BinaryData.fromBytes(serializedManifest));
  }

  BlobClient buildBlobClient(final Manifest manifest) {
    final BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder()
            .connectionString(azureBackupConfig.connectionString())
            .buildClient();
    final BlobContainerClient blobContainerClient =
        blobServiceClient.getBlobContainerClient(azureBackupConfig.containerName());
    blobContainerClient.createIfNotExists();
    return blobContainerClient.getBlobClient(ManifestManager.manifestPath(manifest));
  }
}
