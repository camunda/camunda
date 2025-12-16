/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobWriteOption;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GcsIndexManager {
  private static final Logger LOG = LoggerFactory.getLogger(GcsIndexManager.class);

  private final Storage client;
  private final BucketInfo bucketInfo;
  private final String basePath;

  public GcsIndexManager(final Storage client, final BucketInfo bucketInfo, final String basePath) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
  }

  void upload(final GcsBackupIndexFile indexFile) {
    final Blob newBlob;
    try {
      final BlobWriteOption mode;
      if (indexFile.getGeneration() != null) {
        mode = BlobWriteOption.generationMatch(indexFile.getGeneration());
      } else {
        mode = BlobWriteOption.doesNotExist();
      }
      newBlob = client.createFrom(indexBlobInfo(indexFile.id()), indexFile.path(), mode);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    indexFile.setGeneration(newBlob.getGeneration());
    LOG.debug("Uploaded index {}", indexFile.id());
  }

  GcsBackupIndexFile download(final BackupIndexIdentifier id, final Path targetPath) {
    if (Files.exists(targetPath)) {
      throw new IllegalArgumentException("Index file already exists at " + targetPath);
    }
    final var blob = client.get(indexBlobInfo(id).getBlobId());
    if (blob == null) {
      LOG.debug("Index {} not found in GCS", id);
      try {
        Files.createFile(targetPath);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      return new GcsBackupIndexFile(targetPath, id);
    }
    blob.downloadTo(targetPath);
    LOG.debug("Downloaded index {} to {}", id, targetPath);
    final var indexFile = new GcsBackupIndexFile(targetPath, id);
    indexFile.setGeneration(blob.getGeneration());
    return indexFile;
  }

  private BlobInfo indexBlobInfo(final BackupIndexIdentifier id) {
    return BlobInfo.newBuilder(
            bucketInfo,
            "%sindex/%s/%s/index.bin".formatted(basePath, id.partitionId(), id.nodeId()))
        .setContentType("application/octet-stream")
        .build();
  }
}
