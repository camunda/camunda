/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupReadRateLimiter;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.common.SemaphoreLeasedScheduler;
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
import java.time.Duration;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.FileTransformerConfiguration.FailureBehavior;
import software.amazon.awssdk.core.FileTransformerConfiguration.FileWriteOption;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/** Can save and restore {@link NamedFileSet NamedFileSets}. */
final class FileSetManager {

  private static final Logger LOG = LoggerFactory.getLogger(FileSetManager.class);
  private static final int COMPRESSION_SIZE_THRESHOLD = 8 * 1024 * 1024; // 8 MiB
  private static final String TMP_COMPRESSION_PREFIX = "zb-backup-compress-";
  private static final String TMP_DECOMPRESSION_PREFIX = "zb-backup-decompress-";

  /**
   * The SDK cannot switch off the client-wide API call timeout for a single request, only replace
   * it. No upload gets near this value, so {@link #joinWithin} is what ends a stuck one.
   */
  private static final Duration SDK_API_CALL_TIMEOUT_DISABLED = Duration.ofDays(1);

  private final S3AsyncClient client;
  private final S3BackupConfig config;
  private final Semaphore concurrencyLimit;
  private final ExecutorService schedulerExecutor;
  private final BackupReadRateLimiter readRateLimiter;

  FileSetManager(
      final S3AsyncClient client,
      final S3BackupConfig config,
      final BackupReadRateLimiter readRateLimiter) {
    this.client = client;
    this.config = config;
    this.readRateLimiter = readRateLimiter;
    // We try not to exhaust the available connections by restricting the number of
    // concurrent uploads to half of the number of available connections.
    // This should prevent ConnectionAcquisitionTimeout for backups with many and/or large files
    // where we would otherwise occupy all connections, preventing some uploads from starting.
    concurrencyLimit = new Semaphore(Math.max(1, config.maxConcurrentConnections() / 2));
    schedulerExecutor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("zeebe-backup-s3-", 0).factory());
  }

  CompletableFuture<FileSet> save(final String prefix, final NamedFileSet files) {
    LOG.debug("Saving {} files to prefix {}", files.files().size(), prefix);
    return CompletableFutureUtils.mapAsync(
            files.namedFiles().entrySet(),
            Entry::getKey,
            namedFile ->
                SemaphoreLeasedScheduler.schedule(
                    () -> saveFile(prefix, namedFile.getKey(), namedFile.getValue()),
                    schedulerExecutor,
                    concurrencyLimit))
        .thenApply(FileSet::new);
  }

  private FileSet.FileMetadata saveFile(
      final String prefix, final String fileName, final Path filePath) {
    if (shouldCompressFile(filePath)) {
      final var algorithm = config.compressionAlgorithm().orElseThrow();
      final var compressedFile = compressFile(filePath, algorithm);
      LOG.trace("Saving compressed file {}({}) in prefix {}", fileName, compressedFile, prefix);
      try {
        client
            .putObject(
                put -> put.bucket(config.bucketName()).key(prefix + fileName),
                AsyncRequestBody.fromFile(compressedFile))
            .join();
      } finally {
        cleanupCompressedFile(compressedFile);
      }
      return FileSet.FileMetadata.withCompression(algorithm);
    } else {
      LOG.trace("Saving file {}({}) in prefix {}", fileName, filePath, prefix);
      // Use InputStream instead of File to bypass AWS SDK's file modification time
      // check: https://github.com/camunda/camunda/issues/44035
      try (final var inputStream = readRateLimiter.wrap(Files.newInputStream(filePath))) {
        final var fileSize = Files.size(filePath);
        // The SDK counts the time an upload waits for the read limit towards its API call timeout,
        // and that time depends on how many other uploads share the limit. Take the SDK's timeout
        // out of the way and enforce the configured one here, where the waits can be left out.
        final var apiCallTimeout =
            readRateLimiter.isUnlimited() ? Optional.<Duration>empty() : config.apiCallTimeout();
        final var upload =
            client.putObject(
                put -> {
                  put.bucket(config.bucketName()).key(prefix + fileName);
                  if (apiCallTimeout.isPresent()) {
                    put.overrideConfiguration(c -> c.apiCallTimeout(SDK_API_CALL_TIMEOUT_DISABLED));
                  }
                },
                AsyncRequestBody.fromInputStream(inputStream, fileSize, schedulerExecutor));
        if (apiCallTimeout.isPresent()) {
          joinWithin(upload, apiCallTimeout.get(), inputStream::waited);
        } else {
          upload.join();
        }
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to upload file: " + filePath, e);
      }
      return FileSet.FileMetadata.none();
    }
  }

  /**
   * Waits for the upload like {@link CompletableFuture#join()}, but fails it once it took longer
   * than the timeout, not counting the time it was held back by the read limit. An upload that
   * shares the limit with many others is slow without being stuck, and how many share it changes
   * while it runs; only the time it actually waited tells the two apart.
   *
   * @param waitedForReadLimit how long the upload was held back by the read limit so far
   */
  static <T> T joinWithin(
      final CompletableFuture<T> upload,
      final Duration timeout,
      final Supplier<Duration> waitedForReadLimit) {
    final long startNanos = System.nanoTime();
    while (true) {
      final var waited = waitedForReadLimit.get();
      final long remainingNanos = timeout.plus(waited).toNanos() - (System.nanoTime() - startNanos);
      if (remainingNanos <= 0) {
        upload.cancel(true);
        throw new CompletionException(
            ApiCallTimeoutException.builder()
                .message(
                    "Upload did not complete within the API call timeout of %s, not counting the %s it waited for the backup read limit"
                        .formatted(timeout, waited))
                .build());
      }
      try {
        return upload.get(remainingNanos, TimeUnit.NANOSECONDS);
      } catch (final TimeoutException e) {
        // the upload may have waited for the read limit in the meantime, which moves the deadline
      } catch (final ExecutionException e) {
        throw new CompletionException(e.getCause());
      } catch (final InterruptedException e) {
        upload.cancel(true);
        Thread.currentThread().interrupt();
        throw new CompletionException(e);
      }
    }
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
      LOG.warn("Failed to determine if file should be compressed, assuming no: {}", filePath, e);
      return false;
    }
  }

  private Path compressFile(final Path file, final String algorithm) {
    try {
      final var compressedFile = Files.createTempFile(TMP_COMPRESSION_PREFIX, null);
      LOG.trace("Compressing file {} to {} using {}", file, compressedFile, algorithm);
      // Limiting the read of the source also paces the write of the compressed copy, which cannot
      // be faster than the data it is compressed from.
      try (final var input =
              new BufferedInputStream(readRateLimiter.wrap(Files.newInputStream(file)));
          final var output = new BufferedOutputStream(Files.newOutputStream(compressedFile));
          final var compressedOutput =
              new CompressorStreamFactory().createCompressorOutputStream(algorithm, output)) {
        input.transferTo(compressedOutput);
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
    } catch (final IOException e) {
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
                SemaphoreLeasedScheduler.schedule(
                    () ->
                        restoreFile(
                            sourcePrefix, targetFolder, namedFile.getKey(), namedFile.getValue()),
                    schedulerExecutor,
                    concurrencyLimit))
        .thenApply(NamedFileSetImpl::new);
  }

  private Path restoreFile(
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
        client
            .getObject(
                req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName),
                AsyncResponseTransformer.toFile(
                    compressed,
                    cfg ->
                        cfg.fileWriteOption(FileWriteOption.CREATE_OR_REPLACE_EXISTING)
                            .failureBehavior(FailureBehavior.DELETE)))
            .join();
        decompressFile(compressed, decompressed, compressionAlgorithm.get());
        return decompressed;
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    LOG.trace("Restoring file {} from prefix {} to {}", fileName, sourcePrefix, targetFolder);
    final var path = targetFolder.resolve(fileName);
    client
        .getObject(req -> req.bucket(config.bucketName()).key(sourcePrefix + fileName), path)
        .join();
    return path;
  }

  private void decompressFile(
      final Path compressed, final Path decompressed, final String algorithm) {
    try (final var input = new BufferedInputStream(Files.newInputStream(compressed));
        final var output = new BufferedOutputStream(Files.newOutputStream(decompressed));
        final var decompressedInput =
            new CompressorStreamFactory().createCompressorInputStream(algorithm, input)) {
      decompressedInput.transferTo(output);
      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Decompressed file {} to {} using {}. Compressed: {} bytes, uncompressed: {} bytes",
            compressed,
            decompressed,
            algorithm,
            Files.size(compressed),
            Files.size(decompressed));
      }
    } catch (final IOException e) {
      throw new BackupCompressionFailed(
          "Failed to decompress from %s to %s using %s"
              .formatted(compressed, decompressed, algorithm),
          e);
    } finally {
      cleanupCompressedFile(compressed);
    }
  }

  void close() {
    schedulerExecutor.shutdown();
    try {
      if (!schedulerExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
        schedulerExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      schedulerExecutor.shutdownNow();
    }
  }
}
