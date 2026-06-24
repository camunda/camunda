/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.cloud.BatchResult.Callback;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BlobWriteOption;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.FileSet.NamedFile;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.common.SemaphoreLeasedScheduler;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.BatchOperationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class FileSetManager {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  static final int MAX_DELETE_BATCH_SIZE = 100;

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
  private final int bufferSize;

  FileSetManager(
      final Storage client,
      final BucketInfo bucketInfo,
      final String basePath,
      final ExecutorService executor,
      final int maxConcurrentOperations,
      final int bufferSize) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
    this.executor = executor;
    concurrencyLimit = new Semaphore(maxConcurrentOperations);
    this.bufferSize = bufferSize;
  }

  /**
   * Uploads files in parallel using virtual threads. Uses an InputStream-based approach to prevent
   * the GCS client from reading the file twice (once for CRC32C checksum, once for upload) which
   * could cause checksum mismatches if the file is modified during upload.
   *
   * <p>Concurrency is limited by a semaphore to avoid resource exhaustion.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45636">#45636</a>
   */
  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    final var uploadFutures =
        fileSet.namedFiles().entrySet().stream()
            .map(
                namedFile ->
                    SemaphoreLeasedScheduler.schedule(
                        () -> {
                          uploadFile(id, fileSetName, namedFile.getKey(), namedFile.getValue());
                          return null;
                        },
                        executor,
                        concurrencyLimit))
            .toList();

    // Wait for all uploads to complete
    CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
  }

  private void uploadFile(
      final BackupIdentifier id,
      final String fileSetName,
      final String fileName,
      final Path filePath) {
    try (final var inputStream = Files.newInputStream(filePath)) {
      final int effectiveBufferSize = Math.clamp(Files.size(filePath), 1, bufferSize);
      client.createFrom(
          blobInfo(id, fileSetName, fileName),
          inputStream,
          effectiveBufferSize,
          BlobWriteOption.doesNotExist());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Collection<BlobId> collectBlobIds(final BackupIdentifier id, final String fileSetName) {
    final var ids = new ArrayList<BlobId>();
    for (final var blob :
        client
            .list(bucketInfo.getName(), BlobListOption.prefix(fileSetPath(id, fileSetName)))
            .iterateAll()) {
      ids.add(blob.getBlobId());
    }
    return ids;
  }

  void deleteBlobs(final List<BlobId> blobIds) {
    final int size = blobIds.size();
    for (int i = 0; i < size; i += MAX_DELETE_BATCH_SIZE) {
      final StorageBatch batch = client.batch();
      final List<StorageException> errors = new ArrayList<>();
      for (final var blobId : blobIds.subList(i, Math.min(i + MAX_DELETE_BATCH_SIZE, size))) {
        batch.delete(blobId).notify(new BooleanStorageExceptionCallback(errors::add));
      }
      batch.submit();
      if (!errors.isEmpty()) {
        throw new BatchOperationException(
            "Failures detected in the blob batch deletion", errors.getFirst()) {};
      }
    }
  }

  /**
   * Downloads files in parallel using virtual threads. Concurrency is limited by the same semaphore
   * used for uploads to avoid resource exhaustion.
   */
  NamedFileSet restore(
      final BackupIdentifier id,
      final String filesetName,
      final FileSet fileSet,
      final Path targetFolder) {
    final var downloadFutures =
        fileSet.files().stream()
            .collect(
                Collectors.toMap(
                    NamedFile::name,
                    namedFile ->
                        SemaphoreLeasedScheduler.schedule(
                            () -> downloadFile(id, filesetName, namedFile.name(), targetFolder),
                            executor,
                            concurrencyLimit)));

    // Wait for all downloads to complete
    CompletableFuture.allOf(downloadFutures.values().toArray(new CompletableFuture[0])).join();

    // Collect results
    final var pathByName =
        downloadFutures.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().join()));

    return new NamedFileSetImpl(pathByName);
  }

  private Path downloadFile(
      final BackupIdentifier id,
      final String filesetName,
      final String fileName,
      final Path targetFolder) {
    final var filePath = targetFolder.resolve(fileName);
    client.downloadTo(blobInfo(id, filesetName, fileName).getBlobId(), filePath);
    return filePath;
  }

  private String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return PATH_FORMAT.formatted(
        basePath, id.partitionId(), id.checkpointId(), id.brokerId().id(), fileSetName);
  }

  private BlobInfo blobInfo(
      final BackupIdentifier id, final String fileSetName, final String fileName) {
    return BlobInfo.newBuilder(bucketInfo, fileSetPath(id, fileSetName) + fileName)
        .setContentType("application/octet-stream")
        .build();
  }

  private record BooleanStorageExceptionCallback(Consumer<StorageException> onError)
      implements Callback<Boolean, StorageException> {

    @Override
    public void success(final Boolean deleted) {}

    @Override
    public void error(final StorageException exception) {
      onError.accept(exception);
    }
  }
}
