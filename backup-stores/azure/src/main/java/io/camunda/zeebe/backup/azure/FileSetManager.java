/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.BlobAlreadyExists;

final class FileSetManager {
  // The path format is constructed by partitionId/checkpointId/nodeId/nameOfFile
  private static final String PATH_FORMAT = "%s/%s/%s/%s/";
  private final BlobContainerClient containerClient;
  private boolean containerCreated = false;

  FileSetManager(final BlobContainerClient containerClient) {
    this.containerClient = containerClient;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    assureContainerCreated();
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();
      final String fileSetPath = fileSetPath(id, fileSetName);

      final BlobClient blobClient = containerClient.getBlobClient(fileSetPath + fileName);

      try {
        final BinaryData binaryData = BinaryData.fromFile(filePath);
        blobClient.upload(binaryData, false);
      } catch (final BlobStorageException e) {
        if (e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
          throw new BlobAlreadyExists("File already exists.", e.getCause());
        }
        throw e;
      }
    }
  }

  public void delete(final BackupIdentifier id, final String fileSetName) {
    assureContainerCreated();
    final ListBlobsOptions options = new ListBlobsOptions().setPrefix(fileSetPath(id, fileSetName));
    containerClient
        .listBlobs(options, null)
        .forEach(
            blobItem ->
                containerClient.getBlobClient(blobItem.getName()).getBlockBlobClient().delete());
  }

  void assureContainerCreated() {
    if (!containerCreated) {
      containerClient.createIfNotExists();
      containerCreated = true;
    }
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }
}
