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
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BlobWriteOption;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

final class FileSetManager {
  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  private static final String BATCH_ZIP_NAME = "_batch.zip";
  private static final long SIZE_THRESHOLD = 1024 * 1024; // 1 MiB

  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code basePath}
   *   <li>{@code "contents"}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code nodeId}
   *   <li>{@code fileSetName}
   * </ul>
   */
  private static final String PATH_FORMAT = "%scontents/%s/%s/%s/%s/";

  private final Storage client;
  private final BucketInfo bucketInfo;
  private final String basePath;
  private final ExecutorService executor;
  private final Semaphore concurrencyLimit;
  private final int compressionLevel;

  FileSetManager(
      final Storage client,
      final BucketInfo bucketInfo,
      final String basePath,
      final ExecutorService executor,
      final int maxConcurrentOperations,
      final int compressionLevel) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
    this.executor = executor;
    concurrencyLimit = new Semaphore(maxConcurrentOperations);
    this.compressionLevel = compressionLevel;
  }

  /**
   * Uploads files in parallel using virtual threads. Uses an InputStream-based approach to prevent
   * the GCS client from reading the file twice (once for CRC32C checksum, once for upload) which
   * could cause checksum mismatches if the file is modified during upload.
   *
   * <p>Small files (< 1 MiB) are batched into a single zip archive to reduce roundtrips. Large
   * files are uploaded individually with gzip compression.
   *
   * <p>Concurrency is limited by a semaphore to avoid resource exhaustion.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45636">#45636</a>
   */
  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    // Partition files by size
    final var smallFiles = new HashMap<String, Path>();
    final var largeFiles = new HashMap<String, Path>();

    for (final var entry : fileSet.namedFiles().entrySet()) {
      try {
        final var fileSize = Files.size(entry.getValue());
        if (fileSize < SIZE_THRESHOLD) {
          smallFiles.put(entry.getKey(), entry.getValue());
        } else {
          largeFiles.put(entry.getKey(), entry.getValue());
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    final var uploadFutures = new ArrayList<CompletableFuture<Void>>();

    // Upload batch of small files as a single zip if any exist
    if (!smallFiles.isEmpty()) {
      uploadFutures.add(
          schedule(
              () -> {
                uploadBatch(id, fileSetName, smallFiles);
                return null;
              }));
    }

    // Upload large files individually with compression
    for (final var entry : largeFiles.entrySet()) {
      uploadFutures.add(
          schedule(
              () -> {
                upload(id, fileSetName, entry.getKey(), entry.getValue());
                return null;
              }));
    }

    // Wait for all uploads to complete
    CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
  }

  /**
   * Schedules a task to be executed asynchronously using virtual threads, respecting the
   * concurrency limit imposed by the semaphore.
   */
  private <T> CompletableFuture<T> schedule(final Supplier<T> task) {
    final var result = new CompletableFuture<T>();
    executor.execute(
        () -> {
          try {
            concurrencyLimit.acquire();
            result.complete(task.get());
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(e);
          } catch (final Exception e) {
            result.completeExceptionally(e);
          } finally {
            concurrencyLimit.release();
          }
        });
    return result;
  }

  private void upload(
      final BackupIdentifier id,
      final String fileSetName,
      final String fileName,
      final Path filePath) {
    try {
      if (Files.size(filePath) < SIZE_THRESHOLD) {
        uploadUncompressedFile(id, fileSetName, fileName, filePath);
      } else {
        uploadCompressedFile(id, fileSetName, fileName, filePath);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a zip archive containing all small files and uploads it as a single blob. This reduces
   * the number of roundtrips to GCS when dealing with many small files. Uses piped streams to avoid
   * writing to disk.
   *
   * @param id Backup identifier
   * @param fileSetName Name of the file set (e.g., "snapshot", "segments")
   * @param files Map of file names to file paths to be batched
   */
  private void uploadBatch(
      final BackupIdentifier id, final String fileSetName, final Map<String, Path> files) {
    try (final var zipOutput = new PipedOutputStream();
        final var zipInputStream = new PipedInputStream(zipOutput, 128 * 1024)) {
      // Create the zip in a separate thread, feeding it to the piped output stream
      executor.execute(
          () -> {
            try (final var zipOutputStream = new ZipOutputStream(zipOutput)) {
              for (final var entry : files.entrySet()) {
                final var zipEntry = new ZipEntry(entry.getKey());
                zipOutputStream.putNextEntry(zipEntry);
                Files.copy(entry.getValue(), zipOutputStream);
                zipOutputStream.closeEntry();
              }
            } catch (final IOException e) {
              throw new UncheckedIOException("Failed to create batch zip", e);
            }
          });

      // Upload the zip directly from the piped input stream
      client.createFrom(
          blobInfo(id, fileSetName, BATCH_ZIP_NAME).build(),
          zipInputStream,
          BlobWriteOption.doesNotExist());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to upload batch zip", e);
    }
  }

  private void uploadUncompressedFile(
      final BackupIdentifier id,
      final String fileSetName,
      final String fileName,
      final Path filePath)
      throws IOException {
    try (final var inputStream = Files.newInputStream(filePath)) {
      client.createFrom(
          blobInfo(id, fileSetName, fileName).build(), inputStream, BlobWriteOption.doesNotExist());
    }
  }

  private void uploadCompressedFile(
      final BackupIdentifier id,
      final String fileSetName,
      final String fileName,
      final Path filePath)
      throws IOException {
    final var compressionParams = new GzipParameters();
    compressionParams.setBufferSize(128 * 1024);
    compressionParams.setCompressionLevel(compressionLevel);

    try (final var uncompressedFileContents = Files.newInputStream(filePath);
        final var compressorOutput = new PipedOutputStream();
        final var compressedFileContents = new PipedInputStream(compressorOutput, 128 * 1024)) {
      // Feed the compressor from another thread because piped input/output stream pairs can't be
      // used from the same threads.
      executor.execute(
          () -> {
            try (final var compressorInput =
                new GzipCompressorOutputStream(compressorOutput, compressionParams)) {
              uncompressedFileContents.transferTo(compressorInput);
            } catch (final IOException e) {
              throw new UncheckedIOException(e);
            }
          });

      client.createFrom(
          blobInfo(id, fileSetName, fileName).setContentEncoding("gzip").build(),
          compressedFileContents,
          BlobWriteOption.doesNotExist());
    }
  }

  public void delete(final BackupIdentifier id, final String fileSetName) {
    for (final var blob :
        client
            .list(bucketInfo.getName(), BlobListOption.prefix(fileSetPath(id, fileSetName)))
            .iterateAll()) {
      blob.delete();
    }
  }

  /**
   * Downloads files in parallel using virtual threads. Checks for a batch zip file first and
   * extracts it if present. Falls back to individual file downloads for backward compatibility.
   * Concurrency is limited by the same semaphore used for uploads to avoid resource exhaustion.
   */
  NamedFileSet restore(
      final BackupIdentifier id,
      final String filesetName,
      final FileSet fileSet,
      final Path targetFolder) {
    final var pathByName = new HashMap<String, Path>();

    // Check if batch zip exists (for new-style backups)
    final var batchBlobId = blobInfo(id, filesetName, BATCH_ZIP_NAME).build().getBlobId();
    if (client.get(batchBlobId) != null) {
      // Download and extract batch zip
      final var downloadFuture =
          schedule(
              () -> {
                try {
                  // Create temporary file for the zip download
                  final var zipFile = Files.createTempFile("backup-batch-restore-", ".zip");
                  try {
                    client.downloadTo(batchBlobId, zipFile);
                    final var extractedFiles = extractZipArchive(zipFile, targetFolder);
                    return extractedFiles;
                  } finally {
                    // Clean up temp zip file
                    Files.deleteIfExists(zipFile);
                  }
                } catch (final IOException e) {
                  throw new UncheckedIOException("Failed to download and extract batch zip", e);
                }
              });

      pathByName.putAll(downloadFuture.join());
    }

    // Download remaining files individually (large files or old-style backups)
    final var downloadFutures =
        fileSet.files().stream()
            .filter(namedFile -> !pathByName.containsKey(namedFile.name()))
            .collect(
                Collectors.toMap(
                    NamedFile::name,
                    namedFile ->
                        schedule(
                            () -> downloadFile(id, filesetName, namedFile.name(), targetFolder))));

    // Wait for all downloads to complete
    CompletableFuture.allOf(downloadFutures.values().toArray(new CompletableFuture[0])).join();

    // Collect results
    downloadFutures.forEach((name, future) -> pathByName.put(name, future.join()));

    return new NamedFileSetImpl(pathByName);
  }

  private Path downloadFile(
      final BackupIdentifier id,
      final String filesetName,
      final String fileName,
      final Path targetFolder) {
    final var filePath = targetFolder.resolve(fileName);
    client.downloadTo(blobInfo(id, filesetName, fileName).build().getBlobId(), filePath);
    return filePath;
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(
        basePath, id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }

  private BlobInfo.Builder blobInfo(
      final BackupIdentifier id, final String fileSetName, final String fileName) {
    return BlobInfo.newBuilder(bucketInfo, fileSetPath(id, fileSetName) + fileName)
        .setContentType("application/octet-stream");
  }

  /**
   * Extracts a zip archive to the target folder.
   *
   * @param zipFile Path to the zip file to extract
   * @param targetFolder Folder where files should be extracted
   * @return Map of file names to extracted file paths
   */
  private Map<String, Path> extractZipArchive(final Path zipFile, final Path targetFolder)
      throws IOException {
    final var extractedFiles = new HashMap<String, Path>();
    try (final var zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        final var fileName = entry.getName();
        final var outputPath = targetFolder.resolve(fileName);
        Files.copy(zipInputStream, outputPath);
        extractedFiles.put(fileName, outputPath);
        zipInputStream.closeEntry();
      }
    }
    return extractedFiles;
  }
}
