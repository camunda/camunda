/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

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

  GcsBackupIndexHandle upload(final GcsBackupIndexHandle indexFile) {
    try {
      final BlobWriteOption mode;
      if (indexFile.generation() != null) {
        mode = BlobWriteOption.generationMatch(indexFile.generation());
      } else {
        mode = BlobWriteOption.doesNotExist();
      }
      final var newBlob = client.createFrom(indexBlobInfo(indexFile.id()), indexFile.path(), mode);
      LOG.debug("Uploaded index {}", indexFile.id());
      return new GcsBackupIndexHandle(indexFile.id(), indexFile.path(), newBlob.getGeneration());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  GcsBackupIndexHandle download(final BackupIndexIdentifier id, final Path targetPath) {
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
      return new GcsBackupIndexHandle(targetPath, id);
    }
    blob.downloadTo(targetPath);
    LOG.debug("Downloaded index {} to {}", id, targetPath);
    return new GcsBackupIndexHandle(id, targetPath, blob.getGeneration());
  }

  private BlobInfo indexBlobInfo(final BackupIndexIdentifier id) {
    return BlobInfo.newBuilder(
            bucketInfo,
            "%sindex/%s/%s/index.bin".formatted(basePath, id.partitionId(), id.nodeId()))
        .setContentType("application/octet-stream")
        .build();
  }
}
