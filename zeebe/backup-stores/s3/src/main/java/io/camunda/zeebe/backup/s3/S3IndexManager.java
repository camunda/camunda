/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.BackupIndexFile;
import io.camunda.zeebe.backup.api.BackupIndexIdentifier;
import io.camunda.zeebe.backup.common.BackupIndexIdentifierImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.FileTransformerConfiguration;
import software.amazon.awssdk.core.FileTransformerConfiguration.FailureBehavior;
import software.amazon.awssdk.core.FileTransformerConfiguration.FileWriteOption;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

final class S3IndexManager {
  private static final Logger LOG = LoggerFactory.getLogger(S3IndexManager.class);

  private final S3AsyncClient client;
  private final S3BackupConfig config;

  S3IndexManager(final S3AsyncClient client, final S3BackupConfig config) {
    this.client = client;
    this.config = config;
  }

  CompletableFuture<Void> upload(final S3BackupIndexFile indexFile) {
    final var requestBuilder =
        PutObjectRequest.builder()
            .bucket(config.bucketName())
            .key(objectKey(indexFile.id()))
            .contentType("application/octet-stream");

    if (indexFile.getETag() != null) {
      requestBuilder.ifMatch(indexFile.getETag());
    } else {
      requestBuilder.ifNoneMatch("*");
    }

    return client
        .putObject(requestBuilder.build(), AsyncRequestBody.fromFile(indexFile.path()))
        .thenApply(
            response -> {
              indexFile.setETag(response.eTag());
              LOG.debug("Uploaded index {}", indexFile.id());
              return null;
            });
  }

  CompletableFuture<BackupIndexFile> download(
      final BackupIndexIdentifier id, final Path targetPath) {
    if (Files.exists(targetPath)) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Index file already exists at " + targetPath));
    }

    final var request =
        GetObjectRequest.builder().bucket(config.bucketName()).key(objectKey(id)).build();

    return client
        .getObject(
            request,
            AsyncResponseTransformer.toFile(
                targetPath,
                FileTransformerConfiguration.builder()
                    .failureBehavior(FailureBehavior.LEAVE)
                    .fileWriteOption(FileWriteOption.CREATE_NEW)
                    .build()))
        .<BackupIndexFile>thenApply(
            response -> {
              LOG.debug("Downloaded index {} to {}", id, targetPath);
              return new S3BackupIndexFile(
                  targetPath,
                  new BackupIndexIdentifierImpl(id.partitionId(), id.nodeId()),
                  response.eTag());
            })
        .exceptionally(
            throwable -> {
              if (throwable.getCause() instanceof NoSuchKeyException) {
                LOG.debug("Index {} not found in S3", id);
                try {
                  Files.createFile(targetPath);
                } catch (final IOException e) {
                  throw new UncheckedIOException(e);
                }
                return new S3BackupIndexFile(
                    targetPath, new BackupIndexIdentifierImpl(id.partitionId(), id.nodeId()));
              }
              throw new S3BackupStoreException.IndexReadException(
                  "Failed to download index %s".formatted(id), throwable);
            });
  }

  private String objectKey(final BackupIndexIdentifier id) {
    final var basePath = config.basePath().map(base -> base + "/").orElse("");
    return "%sindex/%s/%s/index.bin".formatted(basePath, id.partitionId(), id.nodeId());
  }
}
