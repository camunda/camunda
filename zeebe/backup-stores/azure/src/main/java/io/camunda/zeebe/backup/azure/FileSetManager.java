/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.implementation.Constants;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.BlobAlreadyExists;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

final class FileSetManager {
  static final int MAX_DELETE_BATCH_SIZE = 256;
  private static final long MB = 1024 * 1024;
  private static final ParallelTransferOptions CHUNKED_FILE_OPTS =
      new ParallelTransferOptions()
          .setBlockSizeLong(4 * MB)
          .setMaxSingleUploadSizeLong(8 * MB)
          .setMaxConcurrency(2);
  private static final BlobRequestConditions NO_OVERWRITE_CONDITION =
      new BlobRequestConditions().setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);

  // The path format is constructed by contents/partitionId/checkpointId/nodeId/nameOfFile
  private static final String PATH_FORMAT = "contents/%s/%s/%s/%s/";
  private final BlobContainerClient containerClient;
  private final BlobBatchClient blobBatchClient;
  private final ReentrantLock containerCreationLock = new ReentrantLock();
  private volatile boolean containerCreated = false;

  FileSetManager(
      final BlobContainerClient containerClient,
      final BlobBatchClient blobBatchClient,
      final boolean createContainer) {
    this.containerClient = containerClient;
    this.blobBatchClient = blobBatchClient;
    containerCreated = !createContainer;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    assureContainerCreated();
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();
      final String fileSetPath = fileSetPath(id, fileSetName);

      final BlobClient blobClient = containerClient.getBlobClient(fileSetPath + fileName);

      try {
        upload(blobClient, filePath);
      } catch (final BlobStorageException e) {
        if (e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
          throw new BlobAlreadyExists("File already exists.", e.getCause());
        }
        throw e;
      }
    }
  }

  Collection<String> collectBlobUrls(final BackupIdentifier id, final String fileSetName) {
    assureContainerCreated();
    final ListBlobsOptions options = new ListBlobsOptions().setPrefix(fileSetPath(id, fileSetName));
    return containerClient.listBlobs(options, null).stream()
        .map(blobItem -> containerClient.getBlobClient(blobItem.getName()).getBlobUrl())
        .toList();
  }

  void deleteBlobs(final List<String> blobUrls) {
    final int size = blobUrls.size();
    for (int i = 0; i < size; i += MAX_DELETE_BATCH_SIZE) {
      final var batch = blobBatchClient.getBlobBatch();
      for (final var url : blobUrls.subList(i, Math.min(i + MAX_DELETE_BATCH_SIZE, size))) {
        batch.deleteBlob(url);
      }
      blobBatchClient.submitBatch(batch);
    }
  }

  public NamedFileSet restore(
      final BackupIdentifier id,
      final String fileSetName,
      final FileSet fileSet,
      final Path targetFolder) {

    final var pathByName =
        fileSet.files().stream()
            .collect(Collectors.toMap(NamedFile::name, f -> targetFolder.resolve(f.name())));

    for (final var entry : pathByName.entrySet()) {
      final var fileName = entry.getKey();
      final var filePath = entry.getValue();

      final BlockBlobClient blobClient =
          containerClient
              .getBlobClient(fileSetPath(id, fileSetName) + fileName)
              .getBlockBlobClient();
      blobClient.downloadToFile(String.valueOf(filePath), true);
    }

    return new NamedFileSetImpl(pathByName);
  }

  void assureContainerCreated() {
    if (!containerCreated) {
      containerCreationLock.lock();
      try {
        if (!containerCreated) {
          containerClient.createIfNotExists();
          containerCreated = true;
        }
      } finally {
        containerCreationLock.unlock();
      }
    }
  }

  private void upload(final BlobClient blobClient, final Path filePath) {
    blobClient.uploadFromFile(
        filePath.toString(),
        CHUNKED_FILE_OPTS,
        new BlobHttpHeaders(),
        null,
        null,
        NO_OVERWRITE_CONDITION,
        null);
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }
}
