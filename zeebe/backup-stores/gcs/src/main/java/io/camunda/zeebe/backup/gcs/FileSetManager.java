/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static io.camunda.zeebe.backup.gcs.GcsBackupStore.SEGMENTS_FILESET_NAME;
import static io.camunda.zeebe.backup.gcs.GcsBackupStore.SNAPSHOT_FILESET_NAME;

import com.google.cloud.storage.BlobId;
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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class FileSetManager {
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

  FileSetManager(final Storage client, final BucketInfo bucketInfo, final String basePath) {
    this.client = client;
    this.bucketInfo = bucketInfo;
    this.basePath = basePath;
  }

  void save(final BackupIdentifier id, final String fileSetName, final NamedFileSet fileSet) {
    for (final var namedFile : fileSet.namedFiles().entrySet()) {
      final var fileName = namedFile.getKey();
      final var filePath = namedFile.getValue();
      try {
        client.createFrom(
            blobInfo(id, fileSetName, fileName), filePath, BlobWriteOption.doesNotExist());
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

  public Map<BackupIdentifier, Collection<BlobId>> backupDataUrls(
      final Collection<BackupIdentifier> ids) {

    return ids.stream().parallel().collect(Collectors.toMap((id) -> id, this::listBackupObjects));
  }

  private Collection<BlobId> listBackupObjects(final BackupIdentifier id) {
    final var snapshotBlobs = listBlobUrls(id, SNAPSHOT_FILESET_NAME);
    final var segmentBlobs = listBlobUrls(id, SEGMENTS_FILESET_NAME);

    return Stream.concat(snapshotBlobs.stream(), segmentBlobs.stream()).collect(Collectors.toSet());
  }

  private Collection<BlobId> listBlobUrls(final BackupIdentifier id, final String fileSetName) {
    final var spliterator =
        Spliterators.spliteratorUnknownSize(
            client
                .list(bucketInfo.getName(), BlobListOption.prefix(fileSetPath(id, fileSetName)))
                .iterateAll()
                .iterator(),
            Spliterator.IMMUTABLE);
    return StreamSupport.stream(spliterator, true)
        .map(BlobInfo::getBlobId)
        .collect(Collectors.toSet());
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
