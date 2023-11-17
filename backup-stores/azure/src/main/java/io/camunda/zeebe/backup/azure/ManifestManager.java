/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.InvalidBackupPath;
import io.camunda.zeebe.backup.azure.manifest.Manifest;
import io.camunda.zeebe.backup.azure.manifest.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ManifestManager {
  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code "manifests"}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code nodeId}
   *   <li>{@code "manifest.json"}
   * </ul>
   */
  private static final String MANIFEST_PATH_FORMAT = "manifests/%s/%s/%s/%s";

  /**
   * Format for path to all manifests.
   *
   * <ul>
   *   <li>{@code basePath}
   *   <li>{@code "manifests"}
   * </ul>
   */
  private static final String MANIFEST_BLOB_NAME = "manifest.json";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  private final BlobContainerClient blobContainerClient;

  ManifestManager(final BlobContainerClient blobContainerClient) {
    this.blobContainerClient = blobContainerClient;
  }

  InProgressManifest createInitialManifest(final Backup backup) {
    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;
    try {
      blobContainerClient.createIfNotExists();
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
      final BlockBlobClient blobClient =
          blobContainerClient
              .getBlobClient(
                  MANIFEST_PATH_FORMAT.formatted(
                      backup.id().partitionId(),
                      backup.id().checkpointId(),
                      backup.id().nodeId(),
                      MANIFEST_BLOB_NAME))
              .getBlockBlobClient();
      blobClient.upload(BinaryData.fromBytes(serializedManifest));
      return manifest;
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  void completeManifest(final InProgressManifest inProgressManifest) {
    final byte[] serializedManifest;
    try {
      final var completed = inProgressManifest.complete();
      serializedManifest = MAPPER.writeValueAsBytes(completed);
      final BlobClient blobClient =
          blobContainerClient.getBlobClient(
              MANIFEST_PATH_FORMAT.formatted(
                  completed.id().partitionId(),
                  completed.id().checkpointId(),
                  completed.id().nodeId(),
                  MANIFEST_BLOB_NAME));
      blobClient.upload(BinaryData.fromBytes(serializedManifest), true);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {

    final BlobClient blobClient =
        blobContainerClient.getBlobClient(
            MANIFEST_PATH_FORMAT.formatted(
                id.partitionId(), id.checkpointId(), id.nodeId(), MANIFEST_BLOB_NAME));
    if (!blobClient.exists()) {
      return null;
    }
    final BinaryData binaryData = blobClient.downloadContent();

    try {
      return MAPPER.readValue(binaryData.toStream(), Manifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void markAsFailed(final BackupIdentifier id, final String failureReason) {
    final BlobClient blobClient =
        blobContainerClient.getBlobClient(
            MANIFEST_PATH_FORMAT.formatted(
                id.partitionId(), id.checkpointId(), id.nodeId(), MANIFEST_BLOB_NAME));
    if (blobClient.exists()) {
      final BinaryData binaryData = blobClient.downloadContent();
      try {
        final Manifest manifest = MAPPER.readValue(binaryData.toStream(), Manifest.class);
        markAsFailed(manifest, failureReason);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      final var failed = Manifest.createFailed(id);
      try {
        blobClient.upload(BinaryData.fromBytes(MAPPER.writeValueAsBytes(failed)), false);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  void markAsFailed(final Manifest existingManifest, final String failureReason) {
    final BlobClient blobClient =
        blobContainerClient.getBlobClient(
            MANIFEST_PATH_FORMAT.formatted(
                existingManifest.id().partitionId(),
                existingManifest.id().checkpointId(),
                existingManifest.id().nodeId(),
                MANIFEST_BLOB_NAME));
    final var updatedManifest =
        switch (existingManifest.statusCode()) {
          case FAILED -> existingManifest.asFailed();
          case COMPLETED -> existingManifest.asCompleted().fail(failureReason);
          case IN_PROGRESS -> existingManifest.asInProgress().fail(failureReason);
        };

    if (existingManifest != updatedManifest) {
      try {
        blobClient.upload(BinaryData.fromBytes(MAPPER.writeValueAsBytes(updatedManifest)), true);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    final Collection<Manifest> manifests = new ArrayList<>();

    // fetch all blobs in container and get its paths
    final List<String> backupPaths =
        blobContainerClient.listBlobs().stream().map(BlobItem::getName).toList();

    final List<BackupIdentifierImpl> backupIdentifiers =
        getMatchingBackupsWithWildcard(wildcard, backupPaths);

    backupIdentifiers.stream().forEach(e -> manifests.add(getManifest((e))));

    return manifests;
  }

  public void deleteManifest(final BackupIdentifier id) {
    final BlobClient blobClient =
        blobContainerClient.getBlobClient(
            MANIFEST_PATH_FORMAT.formatted(
                id.partitionId(), id.checkpointId(), id.nodeId(), MANIFEST_BLOB_NAME));
    if (blobClient.exists()) {
      blobClient.delete();
    }
  }

  // returns the BackupIdentifiers of the matching paths with the wildcards.
  private List<BackupIdentifierImpl> getMatchingBackupsWithWildcard(
      final BackupIdentifierWildcard wildcard, final List<String> basePaths) {

    final List<BackupIdentifierImpl> matchingPaths = new ArrayList<>();
    for (final String path : basePaths) {
      // scheme: basePath/partitionId/checkpointId/nodeId
      final String[] output = path.split("/");
      // we only count the manifest's paths
      if (output[0].equals("contents")) {
        continue;
      }
      try {
        final int partitionId = Integer.parseInt(output[1]);
        final long checkpointId = Long.parseLong(output[2]);
        final int nodeId = Integer.parseInt(output[3]);
        // if the wildcard values are empty then it any value can match
        if (wildcard.partitionId().orElse(partitionId) != partitionId) {
          continue;
        }
        if (wildcard.checkpointId().orElse(checkpointId) != checkpointId) {
          continue;
        }
        if (wildcard.nodeId().orElse(nodeId) != nodeId) {
          continue;
        }
        matchingPaths.add(new BackupIdentifierImpl(nodeId, partitionId, checkpointId));
      } catch (final NumberFormatException e) {
        throw new InvalidBackupPath(e.getMessage(), e);
      }
    }
    return matchingPaths;
  }
}
