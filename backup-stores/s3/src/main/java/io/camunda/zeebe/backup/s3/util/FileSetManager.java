/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3.util;

import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.manifest.FileSet;
import io.camunda.zeebe.backup.s3.manifest.FileSet.FileMetadata;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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

/**
 * Can save and restore {@link NamedFileSet NamedFileSets}. Contents might be compressed if {@link
 * S3BackupConfig#enableCompression()} is enabled.
 */
public final class FileSetManager {
  public static final int COMPRESSION_MIN_FILE_SIZE = 1024 * 1024; // 1 MiB
  public static final String COMPRESSION_ALGORITHM = CompressorStreamFactory.getZstandard();
  private static final Logger LOG = LoggerFactory.getLogger(FileSetManager.class);
  private final S3AsyncClient client;
  private final S3BackupConfig config;

  public FileSetManager(final S3AsyncClient client, final S3BackupConfig config) {
    this.client = client;
    this.config = config;
  }

  public CompletableFuture<FileSet> save(final String prefix, final NamedFileSet files) {
    final var futures =
        files.namedFiles().entrySet().stream()
            .map(segmentFile -> saveNamedFile(prefix, segmentFile.getKey(), segmentFile.getValue()))
            .toArray(CompletableFuture[]::new);

    //noinspection unchecked
    return CompletableFuture.allOf(futures).thenApply(ignored -> savedFileSetFromResults(futures));
  }

  public CompletableFuture<NamedFileSet> restore(
      final String sourcePrefix, final FileSet fileSet, final Path targetFolder) {
    LOG.debug(
        "Downloading {} files from prefix {} to {}",
        fileSet.names().size(),
        sourcePrefix,
        targetFolder);
    final var downloadedFiles = new ConcurrentHashMap<String, Path>();
    final CompletableFuture<?>[] futures =
        fileSet.entries().stream()
            .map(
                entry -> {
                  final var fileName = entry.getKey();
                  final var metadata = entry.getValue();
                  return restoreFile(sourcePrefix, fileName, metadata, targetFolder)
                      .thenApply(path -> downloadedFiles.put(fileName, path));
                })
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(futures)
        .thenApply(ignored -> new NamedFileSetImpl(downloadedFiles));
  }

  private CompletableFuture<Path> restoreFile(
      final String sourcePrefix,
      final String fileName,
      final FileMetadata metadata,
      final Path targetFolder) {
    final var objectKey = sourcePrefix + fileName;
    final var compression = metadata.compressionAlgorithm();
    final var targetPath = targetFolder.resolve(fileName);
    if (compression.isPresent()) {
      return restoreAndDecompressFile(objectKey, compression.get(), targetPath);
    } else {
      return restoreFileWithoutDecompression(objectKey, targetPath);
    }
  }

  private CompletableFuture<Path> restoreAndDecompressFile(
      final String objectKey, final String compressionAlgorithm, final Path target) {
    try {
      final var tmpPath = Files.createTempFile("zb-backup-decompress-", null);
      return client
          .getObject(
              req -> req.bucket(config.bucketName()).key(objectKey),
              AsyncResponseTransformer.toFile(
                  tmpPath,
                  cfg ->
                      cfg.fileWriteOption(FileWriteOption.CREATE_OR_REPLACE_EXISTING)
                          .failureBehavior(FailureBehavior.DELETE)))
          .thenRunAsync(() -> decompressFile(tmpPath, target, compressionAlgorithm))
          .thenApply(ignored -> target);

    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private CompletableFuture<Path> restoreFileWithoutDecompression(
      final String objectKey, final Path target) {
    return client
        .getObject(req -> req.bucket(config.bucketName()).key(objectKey), target)
        .thenApply(resp -> target);
  }

  private static FileSet savedFileSetFromResults(
      final CompletableFuture<? extends SavedNamedFile>[] saveResults) {
    final var savedFiles =
        Arrays.stream(saveResults)
            .map(CompletableFuture::join)
            .collect(Collectors.toMap(SavedNamedFile::fileName, SavedNamedFile::metadata));
    return new FileSet(savedFiles);
  }

  private CompletableFuture<? extends SavedNamedFile> saveNamedFile(
      final String prefix, final String fileName, final Path filePath) {
    try {
      if (shouldCompress(filePath)) {
        return compressAndSaveNamedFile(prefix, fileName, filePath);
      } else {
        return saveNamedFileWithoutCompression(prefix, fileName, filePath);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private CompletableFuture<SavedNamedFile> saveNamedFileWithoutCompression(
      final String prefix, final String fileName, final Path filePath) {
    LOG.trace("Saving file {}({}) in prefix {}", fileName, filePath, prefix);
    return client
        .putObject(
            put -> put.bucket(config.bucketName()).key(prefix + fileName),
            AsyncRequestBody.fromFile(filePath))
        .thenApply(response -> new SavedNamedFile(fileName, FileMetadata.withoutCompression()));
  }

  private CompletableFuture<SavedNamedFile> compressAndSaveNamedFile(
      final String prefix, final String fileName, final Path filePath) throws IOException {
    final var compressed = compressFile(filePath, COMPRESSION_ALGORITHM);
    LOG.trace("Saving compressed file {}({}) in prefix {}", fileName, filePath, prefix);
    return client
        .putObject(
            put -> put.bucket(config.bucketName()).key(prefix + fileName),
            AsyncRequestBody.fromFile(compressed))
        .thenApply(response -> null)
        .thenRunAsync(cleanupCompressedFile(fileName, compressed))
        .thenApply(
            resp ->
                new SavedNamedFile(fileName, FileMetadata.withCompression(COMPRESSION_ALGORITHM)));
  }

  private static Runnable cleanupCompressedFile(final String fileName, final Path compressed) {
    return () -> {
      try {
        Files.delete(compressed);
      } catch (final IOException e) {
        LOG.warn(
            "Failed to delete temporary file {} created for compressing {}",
            compressed,
            fileName,
            e);
      }
    };
  }

  private boolean shouldCompress(final Path filePath) {
    if (!config.enableCompression()) {
      return false;
    }
    try {
      final var fileSize = Files.size(filePath);
      return fileSize > COMPRESSION_MIN_FILE_SIZE;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path compressFile(final Path file, final String algorithm) {
    try {
      final var compressedFile = Files.createTempFile("zb-backup-compress-", null);
      LOG.trace("Compressing file {} to {}", file, compressedFile);
      try (final var fileInput = Files.newInputStream(file);
          final var bufferedInput = new BufferedInputStream(fileInput);
          final var output = Files.newOutputStream(compressedFile);
          final var compressedOutput =
              new CompressorStreamFactory().createCompressorOutputStream(algorithm, output)) {
        IOUtils.copy(bufferedInput, compressedOutput);
        return compressedFile;
      }
    } catch (final IOException | CompressorException e) {
      throw new RuntimeException(e);
    }
  }

  private static void decompressFile(final Path from, final Path to, final String algorithm) {
    try (final var fileInput = Files.newInputStream(from);
        final var bufferedInput = new BufferedInputStream(fileInput);
        final var output = Files.newOutputStream(to);
        final var decompressedOutput =
            new CompressorStreamFactory().createCompressorInputStream(algorithm, bufferedInput)) {
      IOUtils.copy(decompressedOutput, output);
    } catch (final IOException | CompressorException e) {
      throw new RuntimeException(e);
    }
  }

  record SavedNamedFile(String fileName, FileMetadata metadata) {}
}
