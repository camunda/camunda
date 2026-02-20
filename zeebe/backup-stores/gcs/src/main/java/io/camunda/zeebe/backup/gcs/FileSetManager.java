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
import com.google.cloud.storage.transfermanager.ParallelUploadConfig;
import com.google.cloud.storage.transfermanager.TransferManager;
import com.google.cloud.storage.transfermanager.TransferManagerConfig;
import com.google.cloud.storage.transfermanager.TransferStatus;
import com.google.cloud.storage.transfermanager.UploadResult;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FileSetManager implements AutoCloseable {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  private static final Logger LOG = LoggerFactory.getLogger(FileSetManager.class);
  private static final String TAR_GZ_EXTENSION = ".tar.gz";
  private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer for better I/O throughput

  /**
   * Maximum uncompressed size (in bytes) per tar.gz archive batch. Files are grouped into batches
   * until this size threshold is reached, with each batch compressed into a separate archive for
   * parallel upload. Default is 128MB matching the segment size
   */
  private static final long MAX_BATCH_SIZE_BYTES = 128 * 1024 * 1024; // 128MB

  /**
   * Maximum number of files per batch to prevent creating archives with too many entries, which can
   * impact extraction performance.
   */
  private static final int MAX_FILES_PER_BATCH = 50;

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
  private final TransferManager transferManager;
  private final ExecutorService executor;

  FileSetManager(final Storage client, final BucketInfo bucketInfo, final String basePath) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
    executor = Executors.newVirtualThreadPerTaskExecutor();
    transferManager =
        TransferManagerConfig.newBuilder()
            .setStorageOptions(client.getOptions())
            .build()
            .getService();
  }

  @Override
  public void close() throws Exception {
    transferManager.close();
    executor.shutdown();
  }

  /**
   * Saves the file set to GCS by batching files into tar.gz archives. Files are grouped into
   * batches and each batch is compressed into a separate tar.gz archive. Multiple batches are
   * compressed and uploaded in parallel for better throughput.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45636">#45636</a>
   */
  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    if (fileSet.namedFiles().isEmpty()) {
      return;
    }

    // Batch files into groups
    final List<Map<String, Path>> batches = batchFiles(fileSet.namedFiles());

    if (batches.size() == 1) {
      // Single batch - no need for parallel processing
      saveSingleBatch(id, fileSetName, batches.get(0), 0);
    } else {
      // Multiple batches - compress and upload in parallel
      saveBatchesInParallel(id, fileSetName, batches);
    }
  }

  /**
   * Batches files based on total size and file count limits. A new batch is created when either
   * {@link #MAX_BATCH_SIZE_BYTES} or {@link #MAX_FILES_PER_BATCH} threshold is reached.
   *
   * <p>Files larger than {@link #MAX_BATCH_SIZE_BYTES} are placed in their own batch to ensure they
   * are still backed up. This may result in batches exceeding the size threshold, but guarantees
   * all files are included in the backup.
   */
  private List<Map<String, Path>> batchFiles(final Map<String, Path> files) {
    final List<Map<String, Path>> batches = new ArrayList<>();
    Map<String, Path> currentBatch = new HashMap<>();
    long currentBatchSize = 0;

    for (final var entry : files.entrySet()) {
      final String fileName = entry.getKey();
      final Path filePath = entry.getValue();
      long fileSize;
      try {
        fileSize = Files.size(filePath);
      } catch (final IOException e) {
        // If we can't read file size, assume a reasonable default to avoid blocking
        fileSize = BUFFER_SIZE;
      }

      // Start a new batch if adding this file would exceed limits (unless batch is empty)
      // This ensures large files (> MAX_BATCH_SIZE_BYTES) still get their own batch
      if (!currentBatch.isEmpty()
          && (currentBatchSize + fileSize > MAX_BATCH_SIZE_BYTES
              || currentBatch.size() >= MAX_FILES_PER_BATCH)) {
        batches.add(currentBatch);
        currentBatch = new HashMap<>();
        currentBatchSize = 0;
      }

      // Log if a single file exceeds the batch size threshold
      if (fileSize > MAX_BATCH_SIZE_BYTES) {
        LOG.debug(
            "File '{}' ({} bytes) exceeds batch size threshold ({} bytes), placing in dedicated batch",
            fileName,
            fileSize,
            MAX_BATCH_SIZE_BYTES);
      }

      currentBatch.put(fileName, filePath);
      currentBatchSize += fileSize;
    }

    // Add remaining files as last batch
    if (!currentBatch.isEmpty()) {
      batches.add(currentBatch);
    }

    return batches;
  }

  /** Saves a single batch as a tar.gz archive. */
  private void saveSingleBatch(
      final BackupIdentifier id,
      final String fileSetName,
      final Map<String, Path> batch,
      final int batchIndex) {
    final String archiveName = fileSetName + "-" + batchIndex + TAR_GZ_EXTENSION;
    Path tempArchive = null;

    try {
      tempArchive = Files.createTempFile("backup-batch-", TAR_GZ_EXTENSION);
      createTarGzArchive(batch, tempArchive);
      uploadSingleFile(id, fileSetName, tempArchive, archiveName);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      if (tempArchive != null) {
        try {
          Files.deleteIfExists(tempArchive);
        } catch (final IOException ignored) {
          // Best effort cleanup
        }
      }
    }
  }

  /** Compresses and uploads multiple batches in parallel. */
  private void saveBatchesInParallel(
      final BackupIdentifier id, final String fileSetName, final List<Map<String, Path>> batches) {
    final List<Path> tempArchives = new ArrayList<>();
    final Map<String, String> pathToArchiveName = new HashMap<>();

    try {
      // Compress all batches in parallel
      final List<CompletableFuture<BatchArchiveResult>> compressionFutures = new ArrayList<>();

      for (int i = 0; i < batches.size(); i++) {
        final Map<String, Path> batch = batches.get(i);
        final String archiveName = fileSetName + "-" + i + TAR_GZ_EXTENSION;
        final int batchIndex = i;

        final CompletableFuture<BatchArchiveResult> future =
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    final Path tempArchive =
                        Files.createTempFile("backup-batch-" + batchIndex + "-", TAR_GZ_EXTENSION);
                    createTarGzArchive(batch, tempArchive);
                    return new BatchArchiveResult(tempArchive, archiveName);
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                },
                executor);
        compressionFutures.add(future);
      }

      // Wait for all compression tasks to complete
      try {
        CompletableFuture.allOf(compressionFutures.toArray(CompletableFuture[]::new)).join();
      } catch (final Exception e) {
        throw new UncheckedIOException(
            new IOException("Failed to compress batches for backup", e.getCause()));
      }

      // Collect archive paths and mappings
      for (final CompletableFuture<BatchArchiveResult> future : compressionFutures) {
        final BatchArchiveResult result = future.join();
        tempArchives.add(result.tempPath());
        pathToArchiveName.put(result.tempPath().toAbsolutePath().toString(), result.archiveName());
      }

      // Upload all archives in parallel
      uploadFiles(id, fileSetName, tempArchives, pathToArchiveName);

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      cleanupTempFiles(tempArchives);
    }
  }

  /** Creates a tar.gz archive containing the specified files. */
  private void createTarGzArchive(final Map<String, Path> files, final Path archivePath)
      throws IOException {
    try (final var fileOut = Files.newOutputStream(archivePath);
        final var bufferedOut = new java.io.BufferedOutputStream(fileOut, BUFFER_SIZE);
        final var gzipOut = new GZIPOutputStream(bufferedOut, BUFFER_SIZE);
        final var tarOut = new TarArchiveOutputStream(gzipOut)) {
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

      for (final var entry : files.entrySet()) {
        final String fileName = entry.getKey();
        final Path filePath = entry.getValue();
        final long fileSize = Files.size(filePath);

        final TarArchiveEntry tarEntry = new TarArchiveEntry(filePath.toFile(), fileName);
        tarEntry.setSize(fileSize);
        tarOut.putArchiveEntry(tarEntry);

        Files.copy(filePath, tarOut);
        tarOut.closeArchiveEntry();
      }

      tarOut.finish();
    }
  }

  private void uploadSingleFile(
      final BackupIdentifier id,
      final String fileSetName,
      final Path filePath,
      final String targetName)
      throws IOException {
    final ParallelUploadConfig uploadConfig =
        ParallelUploadConfig.newBuilder()
            .setBucketName(bucketInfo.getName())
            .setUploadBlobInfoFactory(
                (pathString, contentType) -> blobInfo(id, fileSetName, targetName))
            .build();

    final List<UploadResult> results =
        transferManager.uploadFiles(List.of(filePath), uploadConfig).getUploadResults();

    for (final UploadResult result : results) {
      if (result.getStatus() != TransferStatus.SUCCESS) {
        final String errorMessage =
            result.getStatus() == TransferStatus.FAILED_TO_FINISH
                ? "Failed to upload archive: " + result.getException().getMessage()
                : "Failed to upload archive with status: " + result.getStatus();
        final Exception cause =
            result.getStatus() == TransferStatus.FAILED_TO_FINISH ? result.getException() : null;
        throw new UncheckedIOException(new IOException(errorMessage, cause));
      }
    }
  }

  private void uploadFiles(
      final BackupIdentifier id,
      final String fileSetName,
      final List<Path> files,
      final Map<String, String> pathToFileName)
      throws IOException {
    final ParallelUploadConfig uploadConfig =
        ParallelUploadConfig.newBuilder()
            .setBucketName(bucketInfo.getName())
            .setUploadBlobInfoFactory(
                (pathString, contentType) ->
                    blobInfo(id, fileSetName, pathToFileName.get(pathString)))
            .build();

    final List<UploadResult> results =
        transferManager.uploadFiles(files, uploadConfig).getUploadResults();

    // Check for upload failures
    for (final UploadResult result : results) {
      if (result.getStatus() != TransferStatus.SUCCESS) {
        final String errorMessage =
            result.getStatus() == TransferStatus.FAILED_TO_FINISH
                ? "Failed to upload file: " + result.getException().getMessage()
                : "Failed to upload file with status: " + result.getStatus();
        final Exception cause =
            result.getStatus() == TransferStatus.FAILED_TO_FINISH ? result.getException() : null;
        throw new UncheckedIOException(new IOException(errorMessage, cause));
      }
    }
  }

  private void cleanupTempFiles(final List<Path> tempFiles) {
    for (final Path tempFile : tempFiles) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (final IOException ignored) {
        // Best effort cleanup
      }
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
   * Downloads tar.gz archives from GCS and extracts their contents to the target folder. Multiple
   * archives are downloaded and extracted in parallel for better throughput.
   */
  NamedFileSet restore(
      final BackupIdentifier id,
      final String filesetName,
      final FileSet fileSet,
      final Path targetFolder) {
    if (fileSet.files().isEmpty()) {
      return new NamedFileSetImpl(Map.of());
    }

    final Map<String, Path> extractedFiles = new HashMap<>();
    final String fileSetPrefix = fileSetPath(id, filesetName);

    // List all tar.gz archives in the fileset path
    final List<com.google.cloud.storage.Blob> archiveBlobs = new ArrayList<>();
    for (final var blob :
        client.list(bucketInfo.getName(), BlobListOption.prefix(fileSetPrefix)).iterateAll()) {
      if (blob.getName().endsWith(TAR_GZ_EXTENSION)) {
        archiveBlobs.add(blob);
      }
    }

    if (archiveBlobs.size() == 1) {
      // Single archive - download and extract directly
      final var blob = archiveBlobs.get(0);
      try {
        final Map<String, Path> files = downloadAndExtractTarGz(blob.getBlobId(), targetFolder);
        extractedFiles.putAll(files);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else if (archiveBlobs.size() > 1) {
      // Multiple archives - download and extract in parallel
      final List<CompletableFuture<Map<String, Path>>> downloadFutures = new ArrayList<>();

      for (final var blob : archiveBlobs) {
        final CompletableFuture<Map<String, Path>> downloadFuture =
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    return downloadAndExtractTarGz(blob.getBlobId(), targetFolder);
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                },
                executor);

        downloadFutures.add(downloadFuture);
      }

      // Wait for all downloads to complete and collect extracted files
      try {
        CompletableFuture.allOf(downloadFutures.toArray(CompletableFuture[]::new)).join();
        for (final var future : downloadFutures) {
          extractedFiles.putAll(future.join());
        }
      } catch (final Exception e) {
        throw new UncheckedIOException(
            new IOException("Failed to download archives from backup", e.getCause()));
      }
    }

    return new NamedFileSetImpl(extractedFiles);
  }

  /** Downloads a tar.gz archive and extracts its contents to the target folder. */
  private Map<String, Path> downloadAndExtractTarGz(
      final com.google.cloud.storage.BlobId blobId, final Path targetFolder) throws IOException {
    final Path tempArchive = Files.createTempFile("backup-restore-", TAR_GZ_EXTENSION);
    try {
      client.downloadTo(blobId, tempArchive);
      return extractTarGzArchive(tempArchive, targetFolder);
    } finally {
      Files.deleteIfExists(tempArchive);
    }
  }

  /** Extracts a tar.gz archive to the target folder and returns the extracted files. */
  private Map<String, Path> extractTarGzArchive(final Path archivePath, final Path targetFolder)
      throws IOException {
    final Map<String, Path> extractedFiles = new HashMap<>();

    try (final var fileIn = Files.newInputStream(archivePath);
        final var bufferedIn = new java.io.BufferedInputStream(fileIn, BUFFER_SIZE);
        final var gzipIn = new GZIPInputStream(bufferedIn, BUFFER_SIZE);
        final var tarIn = new TarArchiveInputStream(gzipIn)) {

      TarArchiveEntry entry;
      while ((entry = tarIn.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }

        final String fileName = entry.getName();
        final Path filePath = targetFolder.resolve(fileName);

        // Ensure parent directories exist
        Files.createDirectories(filePath.getParent());

        try (final var fileOut = Files.newOutputStream(filePath)) {
          tarIn.transferTo(fileOut);
        }

        extractedFiles.put(fileName, filePath);
      }
    }

    return extractedFiles;
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(
        basePath, id.partitionId(), id.checkpointId(), id.nodeId(), fileSetName);
  }

  private BlobInfo blobInfo(
      final BackupIdentifier id, final String fileSetName, final String fileName) {
    return BlobInfo.newBuilder(bucketInfo, fileSetPath(id, fileSetName) + fileName)
        .setContentType("application/octet-stream")
        .build();
  }

  /** Result of compressing a batch of files into an archive. */
  private record BatchArchiveResult(Path tempPath, String archiveName) {}
}
