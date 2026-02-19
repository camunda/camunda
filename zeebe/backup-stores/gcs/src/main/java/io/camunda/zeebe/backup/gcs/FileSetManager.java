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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class FileSetManager implements AutoCloseable {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  private static final String ARCHIVE_FILENAME = "fileset.zip";
  private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer for better I/O throughput

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

  FileSetManager(final Storage client, final BucketInfo bucketInfo, final String basePath) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
    transferManager =
        TransferManagerConfig.newBuilder()
            .setStorageOptions(client.getOptions())
            .build()
            .getService();
  }

  @Override
  public void close() throws Exception {
    transferManager.close();
  }

  /**
   * Compresses all files from the file set into a single GZIP-compressed ZIP archive and uploads it
   * to GCS. This approach reduces the number of network operations and storage costs.
   *
   * <p>The archive is created in a temporary file, then uploaded to GCS.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45636">#45636</a>
   */
  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    if (fileSet.namedFiles().isEmpty()) {
      return;
    }

    try {
      final Path tempArchive = Files.createTempFile("backup-", ".zip");
      try {
        createCompressedArchive(fileSet, tempArchive);
        uploadArchive(id, fileSetName, tempArchive);
      } finally {
        Files.deleteIfExists(tempArchive);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void createCompressedArchive(final NamedFileSet fileSet, final Path archivePath)
      throws IOException {
    try (final var fileOut = Files.newOutputStream(archivePath);
        final var bufferedOut = new java.io.BufferedOutputStream(fileOut, BUFFER_SIZE);
        final var zipOut = new ZipOutputStream(bufferedOut)) {
      zipOut.setLevel(java.util.zip.Deflater.BEST_SPEED);
      for (final var entry : fileSet.namedFiles().entrySet()) {
        final String fileName = entry.getKey();
        final Path filePath = entry.getValue();
        zipOut.putNextEntry(new ZipEntry(fileName));
        Files.copy(filePath, zipOut);
        zipOut.closeEntry();
      }
    }
  }

  private void uploadArchive(
      final BackupIdentifier id, final String fileSetName, final Path archivePath)
      throws IOException {
    final ParallelUploadConfig uploadConfig =
        ParallelUploadConfig.newBuilder()
            .setBucketName(bucketInfo.getName())
            // Use UploadBlobInfoFactory to control the blob name instead of using the file's path
            // name
            .setUploadBlobInfoFactory(
                (path, contentType) -> blobInfo(id, fileSetName, ARCHIVE_FILENAME))
            .build();

    final List<UploadResult> results =
        transferManager.uploadFiles(List.of(archivePath), uploadConfig).getUploadResults();

    // Check for upload failures
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

  public void delete(final BackupIdentifier id, final String fileSetName) {
    for (final var blob :
        client
            .list(bucketInfo.getName(), BlobListOption.prefix(fileSetPath(id, fileSetName)))
            .iterateAll()) {
      blob.delete();
    }
  }

  /**
   * Downloads the compressed archive from GCS and extracts its contents to the target folder.
   *
   * <p>The archive is downloaded to a temporary file, then extracted to the target folder.
   */
  NamedFileSet restore(
      final BackupIdentifier id,
      final String filesetName,
      final FileSet fileSet,
      final Path targetFolder) {
    if (fileSet.files().isEmpty()) {
      return new NamedFileSetImpl(Map.of());
    }

    try {
      final Path tempArchive = Files.createTempFile("backup-restore-", ".zip");
      try {
        downloadArchive(id, filesetName, tempArchive);
        return extractCompressedArchive(tempArchive, targetFolder);
      } finally {
        Files.deleteIfExists(tempArchive);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void downloadArchive(
      final BackupIdentifier id, final String filesetName, final Path archivePath) {
    client.downloadTo(blobInfo(id, filesetName, ARCHIVE_FILENAME).getBlobId(), archivePath);
  }

  private NamedFileSet extractCompressedArchive(final Path archivePath, final Path targetFolder)
      throws IOException {
    final Map<String, Path> extractedFiles = new HashMap<>();
    try (final var fileIn = Files.newInputStream(archivePath);
        final var zipIn = new ZipInputStream(fileIn)) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        final String fileName = entry.getName();
        final Path filePath = targetFolder.resolve(fileName);
        // Use OutputStream to properly extract the content from ZipInputStream
        try (final var fileOut = Files.newOutputStream(filePath)) {
          zipIn.transferTo(fileOut);
        }
        extractedFiles.put(fileName, filePath);
        zipIn.closeEntry();
      }
    }
    return new NamedFileSetImpl(extractedFiles);
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
}
