/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.common.BackupIndexIdentifierImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AzureIndexManager {
  private static final Logger LOG = LoggerFactory.getLogger(AzureIndexManager.class);

  private final BlobContainerClient containerClient;
  private boolean containerCreated;

  AzureIndexManager(final BlobContainerClient containerClient, final boolean createContainer) {
    this.containerClient = containerClient;
    containerCreated = !createContainer;
  }

  AzureBackupIndexFile upload(final AzureBackupIndexFile indexFile) {
    assureContainerCreated();
    final BlobClient blobClient = containerClient.getBlobClient(objectKey(indexFile.id()));

    try {
      final BlobRequestConditions conditions = new BlobRequestConditions();
      if (indexFile.eTag() != null) {
        conditions.setIfMatch(indexFile.eTag());
      } else {
        conditions.setIfNoneMatch("*");
      }

      final BinaryData binaryData = BinaryData.fromFile(indexFile.path());
      final var response =
          blobClient.uploadWithResponse(
              new BlobParallelUploadOptions(binaryData).setRequestConditions(conditions),
              null,
              Context.NONE);
      LOG.debug("Uploaded index {}", indexFile.id());

      return new AzureBackupIndexFile(
          indexFile.id(), indexFile.path(), response.getValue().getETag());
    } catch (final BlobStorageException e) {
      if (e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
        throw new AzureBackupStoreException.BlobAlreadyExists(
            "Index already exists.", e.getCause());
      }
      throw new AzureBackupStoreException.IndexWriteException(
          "Failed to upload index %s".formatted(indexFile.id()), e);
    }
  }

  BackupIndexFile download(final BackupIndexIdentifier id, final Path targetPath) {
    if (Files.exists(targetPath)) {
      throw new IllegalArgumentException("Index file already exists at " + targetPath);
    }
    final BlobClient blobClient = containerClient.getBlobClient(objectKey(id));

    try {
      if (!blobClient.exists()) {
        LOG.debug("Index {} not found in Azure", id);
        try {
          Files.createFile(targetPath);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
        return new AzureBackupIndexFile(
            targetPath, new BackupIndexIdentifierImpl(id.partitionId(), id.nodeId()));
      }

      blobClient.downloadToFile(targetPath.toString(), false);
      final var properties = blobClient.getProperties();
      LOG.debug("Downloaded index {} to {}", id, targetPath);

      return new AzureBackupIndexFile(
          new BackupIndexIdentifierImpl(id.partitionId(), id.nodeId()),
          targetPath,
          properties.getETag());
    } catch (final BlobStorageException e) {
      throw new AzureBackupStoreException.IndexReadException(
          "Failed to download index %s".formatted(id), e);
    }
  }

  void assureContainerCreated() {
    if (!containerCreated) {
      containerClient.createIfNotExists();
      containerCreated = true;
    }
  }

  private String objectKey(final BackupIndexIdentifier id) {
    return "index/%s/%s/index.bin".formatted(id.partitionId(), id.nodeId());
  }
}
