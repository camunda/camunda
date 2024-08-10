/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupCompressionFailed;
import io.camunda.zeebe.backup.s3.manifest.FileSet;
import io.camunda.zeebe.backup.s3.manifest.FileSet.FileMetadata;
import io.camunda.zeebe.backup.s3.util.CompletableFutureUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.FileTransformerConfiguration.FailureBehavior;
import software.amazon.awssdk.core.FileTransformerConfiguration.FileWriteOption;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/** Can save and restore {@link NamedFileSet NamedFileSets}. */
final class FileSetManager {

  private static final Logger LOG = LoggerFactory.getLogger(FileSetManager.class);
  private static final int COMPRESSION_SIZE_THRESHOLD = 8 * 1024 * 1024; // 8 MiB
  private static final String TMP_COMPRESSION_PREFIX = "zb-backup-compress-";
  private static final String TMP_DECOMPRESSION_PREFIX = "zb-backup-decompress-";

  private final S3AsyncClient client;
  private final S3BackupConfig config;
  private final Semaphore uploadLimit;

  public FileSetManager(final S3AsyncClient client, final S3BackupConfig config) {
    this.client = client;
    this.config = config;

    // We try not to exhaust the available connections by restricting the number of
    // concurrent uploads to half of the number of available connections.
    // This should prevent ConnectionAcquisitionTimeout for backups with many and/or large files
    // where we would otherwise occupy all connections, preventing some uploads from starting.
    uploadLimit = new Semaphore(Math.max(1, config.maxConcurrentConnections() / 2));
  }

  CompletableFuture<FileSet> save(final String prefix, final NamedFileSet files) {
    LOG.debug("Saving {} files to prefix {}", files.files().size(), prefix);
    return CompletableFutureUtils.mapAsync(
            files.namedFiles().entrySet(),
            Entry::getKey,
            namedFile -> saveFile(prefix, namedFile.getKey(), namedFile.getValue()))
        .thenApply(FileSet::new);
  }

  private CompletableFuture<FileSet.FileMetadata> saveFile(
      final String prefix, final String fileName, final Path filePath) {

    if (shouldCompressFile(filePath)) {
      final var algorithm = config.compressionAlgorithm().orElseThrow();
      return CompletableFuture.runAsync(uploadLimit::acquireUninterruptibly)
          .thenApply((success) -> compressFile(filePath, algorithm))
          .thenCompose(
              (compressedFile) -> {
                LOG.trace(
                    "Saving compressed file {}({}) in prefix {}", fileName, compressedFile, prefix);
                return client
                    .putObject(
                        put -> put.bucket(config.bucketName()).key(prefix + fileName),
                        AsyncRequestBody.fromFile(compressedFile))
                    .thenRunAsync(() -> cleanupCompressedFile(compressedFile))
                    .thenApply(unused -> FileSet.FileMetadata.withCompression(algorithm));
              })
          .whenComplete((success, error) -> uploadLimit.release());
    }

    return CompletableFuture.runAsync(uploadLimit::acquireUninterruptibly)
        .thenCompose(
            (nothing) -> {
              LOG.trace("Saving file {}({}) in prefix {}", fileName, filePath, prefix);
              return client
                  .putObject(
                      put -> put.bucket(config.bucketName()).key(prefix + fileName),
                      AsyncRequestBody.fromFile(filePath))
                  .thenApply(unused -> FileSet.FileMetadata.none());
            })
        .whenComplete((success, error) -> uploadLimit.release());
  }

  private void cleanupCompressedFile(final Path compressedFile) {
    try {
      Files.delete(compressedFile);
    } catch (final IOException e) {
      LOG.warn(
          "Failed to clean up temporary file used for (de-)compression: {}", compressedFile, e);
    }
  }

  private boolean shouldCompressFile(final Path filePath) {
    try {
      return config.compressionAlgorithm().isPresent()
          && Files.size(filePath) > COMPRESSION_SIZE_THRESHOLD;
    } catch (final IOException e) {
      LOG.warn("Failed to determine if file should be compressed, assuming no: {}", filePath);
      return false;
    }
  }

  private Path compressFile(final Path file, final String algorithm) {
    try {
      final var compressedFile = Files.createTempFile(TMP_COMPRESSION_PREFIX, null);
      LOG.trace("Compressing file {} to {} using {}", file, compressedFile, algorithm);
      try (final var input = new BufferedInputStream(Files.newInputStream(file));
          final var output = new BufferedOutputStream(Files.newOutputStream(compressedFile));
          final var compressedOutput =
              new CompressorStreamFactory().createCompressorOutputStream(algorithm, output)) {
        IOUtils.copy(input, compressedOutput);
        if (LOG.isTraceEnabled()) {
          LOG.trace(
              "Compressed file {} to {}. Uncompressed: {} bytes, compressed: {} bytes",
              file,
              compressedFile,
              Files.size(file),
              Files.size(compressedFile));
        }
        return compressedFile;
      }
    } catch (final IOException | CompressorException e) {
      throw new BackupCompressionFailed(
          "Failed to compress file %s using %s".formatted(file, algorithm), e);
    }
  }

  CompletableFuture<NamedFileSet> restore(
      final String sourcePrefix, final FileSet fileSet, final Path targetFolder) {
    LOG.debug(
        "Restoring {} files from prefix {} to {}",
        fileSet.files().size(),
        sourcePrefix,
        targetFolder);
    return CompletableFutureUtils.mapAsync(
            fileSet.files().entrySet(),
            Entry::getKey,
            namedFile ->
                restoreFile(sourcePrefix, targetFolder, namedFile.getKey(), namedFile.getValue()))
        .thenApply(NamedFileSetImpl::new);
  }

  private CompletableFuture<Path> restoreFile(
      final String sourcePrefix,
      final Path targetFolder,
      final String fileName,
      final FileMetadata metadata) {
    final var compressionAlgorithm = metadata.compressionAlgorithm();
    if (compressionAlgorithm.isPresent()) {
      final var decompressed = targetFolder.resolve(fileName);
      LOG.trace(
          "Restoring compressed file {} from prefix {} to {}",
          fileName,
          sourcePrefix,
          targetFolder);
      try {
        final var compressed = Files.createTempFile(TMP_DECOMPRESSION_PREFIX, null);
        return client
            .getObject(
                req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName),
                AsyncResponseTransformer.toFile(
                    compressed,
                    cfg ->
                        cfg.fileWriteOption(FileWriteOption.CREATE_OR_REPLACE_EXISTING)
                            .failureBehavior(FailureBehavior.DELETE)))
            .thenApplyAsync(
                response -> decompressFile(compressed, decompressed, compressionAlgorithm.get()));

      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    LOG.trace("Restoring file {} from prefix {} to {}", fileName, sourcePrefix, targetFolder);
    final var path = targetFolder.resolve(fileName);
    return client
        .getObject(req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName), path)
        .thenApply(response -> path);
  }

  private Path decompressFile(
      final Path compressed, final Path decompressed, final String algorithm) {
    try (final var input = new BufferedInputStream(Files.newInputStream(compressed));
        final var output = new BufferedOutputStream(Files.newOutputStream(decompressed));
        final var decompressedOutput =
            new CompressorStreamFactory().createCompressorInputStream(algorithm, input)) {
      IOUtils.copy(decompressedOutput, output);
      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Decompressed file {} to {} using {}. Compressed: {} bytes, uncompressed: {} bytes",
            compressed,
            decompressed,
            algorithm,
            Files.size(compressed),
            Files.size(decompressed));
      }
      cleanupCompressedFile(compressed);
      return decompressed;
    } catch (final IOException | CompressorException e) {
      throw new BackupCompressionFailed(
          "Failed to decompress from %s to %s using %s"
              .formatted(compressed, decompressed, algorithm),
          e);
    }
  }
}
