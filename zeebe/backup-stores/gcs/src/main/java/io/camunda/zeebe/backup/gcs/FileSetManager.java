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
import com.google.cloud.storage.transfermanager.ParallelUploadConfig;
import com.google.cloud.storage.transfermanager.TransferManager;
import com.google.cloud.storage.transfermanager.TransferStatus;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

final class FileSetManager {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";

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
  private final TransferManager transferManager;
  private final BucketInfo bucketInfo;
  private final String basePath;

  FileSetManager(
      final Storage client,
      final TransferManager transferManager,
      final BucketInfo bucketInfo,
      final String basePath) {
    this.client = client;
    this.transferManager = transferManager;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
  }

  /**
   * Uploads snapshot files in parallel using the GCS {@link TransferManager}. Snapshot files are
   * immutable, so the Path-based upload (which reads the file twice for CRC32C verification) is
   * safe.
   */
  void saveSnapshot(final BackupIdentifier id, final NamedFileSet fileSet) {
    final var prefix = fileSetPath(id, SNAPSHOT_FILESET_NAME);
    final var namedFiles = fileSet.namedFiles();
    final var pathToName =
        namedFiles.entrySet().stream()
            .collect(
                Collectors.toMap(e -> e.getValue().toAbsolutePath().toString(), Map.Entry::getKey));
    final var paths = namedFiles.values().stream().toList();

    final var uploadConfig =
        ParallelUploadConfig.newBuilder()
            .setBucketName(bucketInfo.getName())
            .setUploadBlobInfoFactory(
                (bucketName, fileName) ->
                    BlobInfo.newBuilder(bucketName, prefix + pathToName.get(fileName))
                        .setContentType("application/octet-stream")
                        .build())
            .setSkipIfExists(true)
            .build();

    try {
      final var uploadJob = transferManager.uploadFiles(paths, uploadConfig);
      for (final var result : uploadJob.getUploadResults()) {
        if (result.getStatus() != TransferStatus.SUCCESS
            && result.getStatus() != TransferStatus.SKIPPED) {
          throw result.getException();
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Uploads segment files sequentially using an InputStream-based approach. Segment files may be
   * modified during upload, so we use InputStream to prevent the GCS client from reading the file
   * twice (once for CRC32C checksum, once for upload) which would cause checksum mismatches.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45636">#45636</a>
   */
  void saveSegments(final BackupIdentifier id, final NamedFileSet fileSet) {
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();
      try (final var inputStream = Files.newInputStream(filePath)) {
        client.createFrom(
            blobInfo(id, SEGMENTS_FILESET_NAME, fileName),
            inputStream,
            BlobWriteOption.doesNotExist());
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
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

  public NamedFileSet restore(
      final BackupIdentifier id,
      final String filesetName,
      final FileSet fileSet,
      final Path targetFolder) {
    final var pathByName =
        fileSet.files().stream()
            .collect(Collectors.toMap(NamedFile::name, (f) -> targetFolder.resolve(f.name())));

    for (final var entry : pathByName.entrySet()) {
      final var fileName = entry.getKey();
      final var filePath = entry.getValue();
      client.downloadTo(blobInfo(id, filesetName, fileName).getBlobId(), filePath);
    }

    return new NamedFileSetImpl(pathByName);
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
