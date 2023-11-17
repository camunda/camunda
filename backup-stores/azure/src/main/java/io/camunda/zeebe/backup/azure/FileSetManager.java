/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlockBlobClient;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.azure.manifest.FileSet;
import io.camunda.zeebe.backup.azure.manifest.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

final class FileSetManager {

  private static final String PATH_FORMAT = "contents/%s/%s/%s/%s/";
  private final BlobContainerClient containerClient;

  FileSetManager(final BlobContainerClient containerClient) {
    this.containerClient = containerClient;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet)
      throws NoSuchFileException {
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();

      if (!filePath.toFile().isFile()) {
        throw new NoSuchFileException(
            String.format("File %s does not exist.", filePath.toString()));
      }
      containerClient.createIfNotExists();
      final BlockBlobClient blobClient =
          containerClient
              .getBlobClient(fileSetPath(id, fileSetName) + fileName)
              .getBlockBlobClient();

      final BinaryData binaryData = BinaryData.fromFile(filePath);
      blobClient.upload(binaryData);
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

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }

  public void delete(final BackupIdentifier id, final String fileSetName) {
    if (!containerClient.exists()) {
      return;
    }
    final ListBlobsOptions options = new ListBlobsOptions().setPrefix(fileSetPath(id, fileSetName));
    containerClient
        .listBlobs(options, Duration.of(10, ChronoUnit.SECONDS))
        .forEach(
            blobItem ->
                containerClient.getBlobClient(blobItem.getName()).getBlockBlobClient().delete());
  }
}
