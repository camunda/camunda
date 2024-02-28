/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.azure;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.common.implementation.Constants;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ManifestManager {
  public static final int PRECONDITION_FAILED = 412;

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
  private static final String MANIFEST_PATH_FORMAT = "manifests/%s/%s/%s/manifest.json";

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

  PersistedManifest createInitialManifest(final Backup backup) {

    final var manifest = Manifest.createInProgress(backup);
    final byte[] serializedManifest;
    assureContainerCreated();
    try {
      serializedManifest = MAPPER.writeValueAsBytes(manifest);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(manifest));

    try {
      final BlobRequestConditions blobRequestConditions = new BlobRequestConditions();
      disableOverwrite(blobRequestConditions);
      final Response<BlockBlobItem> blockBlobItemResponse =
          blobClient.uploadWithResponse(
              new BlobParallelUploadOptions(BinaryData.fromBytes(serializedManifest))
                  .setRequestConditions(blobRequestConditions),
              null,
              Context.NONE);

      return new PersistedManifest(blockBlobItemResponse.getValue().getETag(), manifest);
    } catch (final BlobStorageException e) {
      if (e.getErrorCode() == BlobErrorCode.BLOB_ALREADY_EXISTS) {
        throw new UnexpectedManifestState("Manifest already exists.", e);
      }
      throw e;
    }
  }

  void completeManifest(final PersistedManifest inProgressManifest) {
    final byte[] serializedManifest;
    final var completed = inProgressManifest.manifest().complete();
    assureContainerCreated();
    try {
      serializedManifest = MAPPER.writeValueAsBytes(completed);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestPath(completed));
    try {
      final Manifest manifest = getManifest(inProgressManifest.manifest().id());

      if (manifest == null) {
        throw new UnexpectedManifestState("Manifest does not exist.");
      } else if (manifest.statusCode() != StatusCode.IN_PROGRESS) {
        throw new UnexpectedManifestState(
            "Expected manifest to be in progress but was in %s"
                .formatted(manifest.statusCode().name()));
      }

      // we only complete the manifest if the eTag matches, thus
      // assuring no other client has written in the meantime.
      final BlobRequestConditions blobRequestConditions =
          new BlobRequestConditions().setIfMatch(inProgressManifest.eTag());
      blobClient.uploadWithResponse(
          new BlobParallelUploadOptions(BinaryData.fromBytes(serializedManifest))
              .setRequestConditions(blobRequestConditions),
          null,
          Context.NONE);
    } catch (final BlobStorageException e) {
      // will throw precondition failed if etag does not match.
      if (e.getStatusCode() == PRECONDITION_FAILED) {
        throw new UnexpectedManifestState(e.getMessage());
      }
      throw new RuntimeException(e);
    }
  }

  void markAsFailed(final BackupIdentifier manifestId, final String failureReason) {
    assureContainerCreated();

    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestIdPath(manifestId));
    Manifest manifest = getManifest(manifestId);
    if (manifest == null) {
      manifest = Manifest.createFailed(manifestId);
    }

    final var updatedManifest =
        switch (manifest.statusCode()) {
          case FAILED -> manifest.asFailed();
          case COMPLETED -> manifest.asCompleted().fail(failureReason);
          case IN_PROGRESS -> manifest.asInProgress().fail(failureReason);
        };

    if (manifest != updatedManifest) {
      try {
        blobClient.upload(BinaryData.fromBytes(MAPPER.writeValueAsBytes(updatedManifest)), true);
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void deleteManifest(final BackupIdentifier id) {
    final BlobClient blobClient = blobContainerClient.getBlobClient(manifestIdPath(id));
    final Manifest manifest = getManifest(id);
    if (manifest == null) {
      return;
    } else if (manifest.statusCode() == StatusCode.IN_PROGRESS) {
      throw new UnexpectedManifestState(
          "Cannot delete Backup with id '%s' while saving is in progress."
              .formatted(id.toString()));
    }

    blobClient.delete();
  }

  Manifest getManifest(final BackupIdentifier id) {
    return getManifestWithPath(
        MANIFEST_PATH_FORMAT.formatted(id.partitionId(), id.checkpointId(), id.nodeId()));
  }

  private Manifest getManifestWithPath(final String path) {
    final BlobClient blobClient;
    final BinaryData binaryData;
    blobClient = blobContainerClient.getBlobClient(path);
    try {
      binaryData = blobClient.downloadContent();
    } catch (final BlobStorageException e) {
      if (e.getErrorCode() == BlobErrorCode.CONTAINER_NOT_FOUND
          || e.getErrorCode() == BlobErrorCode.BLOB_NOT_FOUND) {
        return null;
      }
      throw new RuntimeException(e);
    }

    try {
      return MAPPER.readValue(binaryData.toStream(), Manifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    assureContainerCreated();
    return blobContainerClient
        .listBlobs(new ListBlobsOptions().setPrefix(wildcardPrefix(wildcard)), null)
        .stream()
        .map(BlobItem::getName)
        .filter(path -> filterBlobsByWildcard(wildcard, path))
        .map(this::getManifestWithPath)
        .toList();
  }

  public static String manifestPath(final Manifest manifest) {
    return manifestIdPath(manifest.id());
  }

  private static String manifestIdPath(final BackupIdentifier backupIdentifier) {
    return MANIFEST_PATH_FORMAT.formatted(
        backupIdentifier.partitionId(), backupIdentifier.checkpointId(), backupIdentifier.nodeId());
  }

  private boolean filterBlobsByWildcard(
      final BackupIdentifierWildcard wildcard, final String path) {
    final var pattern =
        Pattern.compile(
                MANIFEST_PATH_FORMAT.formatted(
                    wildcard.partitionId().map(Number::toString).orElse("\\d+"),
                    wildcard.checkpointId().map(Number::toString).orElse("\\d+"),
                    wildcard.nodeId().map(Number::toString).orElse("\\d+")))
            .asMatchPredicate();
    return pattern.test(path);
  }

  /**
   * Tries to build the longest possible prefix based on the given wildcard. If the first component
   * of prefix is not present in the wildcard, the prefix will be empty. If the second component of
   * the prefix is empty, the prefix will only contain the first prefix component and so forth.
   */
  private String wildcardPrefix(final BackupIdentifierWildcard wildcard) {
    //noinspection OptionalGetWithoutIsPresent -- checked by takeWhile
    return Stream.of(wildcard.partitionId(), wildcard.checkpointId(), wildcard.nodeId())
        .takeWhile(Optional::isPresent)
        .map(Optional::get)
        .map(Number::toString)
        .collect(Collectors.joining("/", "manifests/", ""));
  }

  void assureContainerCreated() {
    if (!containerCreated) {
      blobContainerClient.createIfNotExists();
      containerCreated = true;
    }
  }

  public static void disableOverwrite(final BlobRequestConditions blobRequestConditions) {
    // Optionally limit requests to resources that do not match the passed ETag.
    // None will match therefore it will not overwrite.
    blobRequestConditions.setIfNoneMatch(Constants.HeaderConstants.ETAG_WILDCARD);
  }

  record PersistedManifest(String eTag, InProgressManifest manifest) {}
}
