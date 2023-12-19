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
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.azure.manifest.Manifest;
import io.camunda.zeebe.backup.azure.manifest.Manifest.InProgressManifest;

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
   *
   * The path format is constructed by partitionId/checkpointId/nodeId/manifest.json
   */
  private static final String MANIFEST_PATH_FORMAT = "%s/%s/%s/manifest.json";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  private boolean containerCreated = false;
  private final BlobContainerClient blobContainerClient;

  ManifestManager(final BlobContainerClient blobContainerClient) {
    this.blobContainerClient = blobContainerClient;
  }

  InProgressManifest createInitialManifest(final Backup backup) {
    try {
      final var manifest = Manifest.createInProgress(backup);
      final byte[] serializedManifest;
      if (!containerCreated) {
        blobContainerClient.createIfNotExists();
        containerCreated = true;
      }
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
      final BlockBlobClient blobClient =
          blobContainerClient.getBlobClient(manifestPath((manifest))).getBlockBlobClient();
      blobClient.upload(BinaryData.fromBytes(serializedManifest));
      return manifest;
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (final BlobStorageException e) {
      throw new UnexpectedManifestState(e.getMessage());
    }
  }

  void completeManifest(final InProgressManifest inProgressManifest) {
    try {
      final byte[] serializedManifest;
      final var completed = inProgressManifest.complete();
      serializedManifest = MAPPER.writeValueAsBytes(completed);
      final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(completed));
      blobClient.upload(BinaryData.fromBytes(serializedManifest), true);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (final BlobStorageException e) {
      throw new UnexpectedManifestState(e.getMessage());
    }
  }

  void markAsFailed(final Manifest existingManifest, final String failureReason) {
    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(existingManifest));
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

  private String manifestPath(final Manifest manifest) {
    return MANIFEST_PATH_FORMAT.formatted(
        manifest.id().partitionId(), manifest.id().checkpointId(), manifest.id().nodeId());
  }
}
