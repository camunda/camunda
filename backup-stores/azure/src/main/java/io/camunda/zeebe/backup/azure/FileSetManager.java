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
import com.azure.storage.blob.specialized.BlockBlobClient;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import java.nio.file.NoSuchFileException;

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
        throw new NoSuchFileException(String.format("File %s does not exist.", filePath));
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

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }
}
