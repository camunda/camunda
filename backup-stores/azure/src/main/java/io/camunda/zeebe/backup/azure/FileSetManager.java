/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.specialized.BlobLeaseClient;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import com.azure.storage.common.implementation.Constants;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.UnexpectedManifestState;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;

final class FileSetManager {

  public static final int PRECONDITION_FAILED = 412;
  // The path format is constructed by partitionId/checkpointId/nodeId/nameOfFile
  private static final String PATH_FORMAT = "%s/%s/%s/%s/";
  private final BlobContainerClient containerClient;
  private boolean containerCreated = false;
  private String fileSetPath;

  FileSetManager(final BlobContainerClient containerClient) {
    this.containerClient = containerClient;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet)
      throws NoSuchFileException {
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();

      if (!containerCreated) {
        containerClient.createIfNotExists();
        fileSetPath = fileSetPath(id, fileSetName);
        containerCreated = true;
      }
      final BlobClient blobClient = containerClient.getBlobClient(fileSetPath + fileName);

      try {
        final BinaryData binaryData = BinaryData.fromFile(filePath);
        final BlobRequestConditions blobRequestConditions = acquireBlobLease(blobClient);
        disableOverWrite(blobRequestConditions);
        blobClient.uploadWithResponse(
            new BlobParallelUploadOptions(binaryData).setRequestConditions(blobRequestConditions),
            null,
            Context.NONE);
        blobClient.upload(binaryData, true);
        releaseLease(blobClient);
      } catch (final BlobStorageException e) {
        if (e.getStatusCode() == PRECONDITION_FAILED) {
          // blob might be currently blocked, which is not expected, as this is
          // supposed to be the first and only write operation on this blob
          throw new UnexpectedManifestState(e.getMessage());
        }
        throw e;
      } catch (final Exception e) {
        // When file does not exist fromFile() throws a UncheckedIOException
        // with cause java.io.FileNotFoundException
        if (e.getCause() != null && e.getCause().getClass().equals(FileNotFoundException.class)) {
          throw new NoSuchFileException(String.format("File %s does not exist.", filePath));
        }
        throw e;
      }
    }
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }

  public BlobRequestConditions acquireBlobLease(final BlobClient blobClient) {
    final BlobLeaseClient leaseClient =
        new BlobLeaseClientBuilder().blobClient(blobClient).buildClient();
    try {
      return new BlobRequestConditions().setLeaseId(leaseClient.acquireLease(-1));
    } catch (final Exception e) {
      // Blob client might not exist
      return new BlobRequestConditions();
    }
  }

  public void releaseLease(final BlobClient blobClient) {
    final BlobLeaseClient leaseClient =
        new BlobLeaseClientBuilder().blobClient(blobClient).buildClient();
    try {
      leaseClient.releaseLease();
    } catch (final Exception e) {
      // Blob client might not exist
    }
  }

  private void disableOverWrite(final BlobRequestConditions blobRequestConditions) {
    // Optionally limit requests to resources that do not match the passed ETag.
    // None will match therefore it will not overwrite.
    blobRequestConditions.setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);
  }
}
