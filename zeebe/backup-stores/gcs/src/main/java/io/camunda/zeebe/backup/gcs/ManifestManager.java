/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.InProgressManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManifestManager {

  public static final String ERROR_MSG_MANIFEST_ALREADY_EXISTS =
      "Expected to create new manifest for backup '%s', but already exists.";
  public static final String ERROR_MSG_MANIFEST_MODIFICATION =
      "Expected to complete manifest for backup '%s', but modification was detected unexpectedly.";
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  public static final int PRECONDITION_FAILED = 412;
  private static final int MAX_LIST_ATTEMPTS = 6;
  private static final Duration INITIAL_LIST_RETRY_DELAY = Duration.ofMillis(100);
  private static final Duration MAX_LIST_RETRY_DELAY = Duration.ofSeconds(2);
  private static final Logger LOG = LoggerFactory.getLogger(ManifestManager.class);

  /**
   * Format for path to all manifests.
   *
   * <ul>
   *   <li>{@code basePath}
   *   <li>{@code "manifests"}
   * </ul>
   */
  private static final String MANIFESTS_ROOT_PATH_FORMAT = "%smanifests/";

  /**
   * The path format consists of the following elements:
   *
   * <ul>
   *   <li>{@code basePath}
   *   <li>{@code "manifests"}
   *   <li>{@code partitionId}
   *   <li>{@code checkpointId}
   *   <li>{@code memberId}
   *   <li>{@code "manifest.json"}
   * </ul>
   */
  private static final String MANIFEST_PATH_FORMAT = MANIFESTS_ROOT_PATH_FORMAT + "%s/%s/%s/%s";

  private static final String MANIFEST_BLOB_NAME = "manifest.json";
  private final BucketInfo bucketInfo;
  private final Storage client;
  private final String basePath;
  private final ExecutorService executor;

  ManifestManager(
      final Storage client,
      final BucketInfo bucketInfo,
      final String basePath,
      final ExecutorService executor) {
    this.bucketInfo = bucketInfo;
    this.client = client;
    this.basePath = basePath;
    this.executor = executor;
  }

  PersistedManifest createInitialManifest(final Backup backup) {
    final var manifest = Manifest.createInProgress(backup);
    final var blobInfo = manifestBlobInfoWithMetadata(backup.id(), manifest);
    try {
      final var blob =
          client.create(
              blobInfo, MAPPER.writeValueAsBytes(manifest), BlobTargetOption.doesNotExist());
      return new PersistedManifest(blob.getGeneration(), manifest);
    } catch (final StorageException e) {
      if (e.getCode() == PRECONDITION_FAILED) { // blob must already exist
        throw new UnexpectedManifestState(ERROR_MSG_MANIFEST_ALREADY_EXISTS.formatted(backup.id()));
      }
      throw e;
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  void completeManifest(final PersistedManifest persistedManifest) {
    final var generation = persistedManifest.generation();
    final var completed = persistedManifest.manifest().complete();
    try {
      client.create(
          manifestBlobInfoWithMetadata(completed.id(), completed),
          MAPPER.writeValueAsBytes(completed),
          BlobTargetOption.generationMatch(generation));
    } catch (final StorageException e) {
      if (e.getCode() == PRECONDITION_FAILED) { // blob must have changed
        throw new UnexpectedManifestState(
            ERROR_MSG_MANIFEST_MODIFICATION.formatted(completed.id()));
      }
      throw e;
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    final var blob = client.get(manifestBlobInfo(id).getBlobId());
    if (blob == null) {
      return null;
    }

    try {
      return MAPPER.readValue(blob.getContent(), Manifest.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void markAsFailed(final BackupIdentifier id, final String failureReason) {
    final var blob = client.get(manifestBlobInfo(id).getBlobId());
    try {
      if (blob == null) {
        final var failed = Manifest.createFailed(id);
        final var blobInfo = manifestBlobInfoWithMetadata(id, failed);
        client.create(blobInfo, MAPPER.writeValueAsBytes(failed), BlobTargetOption.doesNotExist());
      } else {
        final var existingManifest = MAPPER.readValue(blob.getContent(), Manifest.class);
        markAsFailed(existingManifest, failureReason);
      }
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void markAsFailed(final Manifest existingManifest, final String failureReason) {
    final var updatedManifest =
        switch (existingManifest.statusCode()) {
          case FAILED -> existingManifest.asFailed();
          case COMPLETED -> existingManifest.asCompleted().fail(failureReason);
          case IN_PROGRESS -> existingManifest.asInProgress().fail(failureReason);
          case DELETED ->
              throw new UnexpectedManifestState(
                  "Cannot fail a deleted manifest" + existingManifest.id());
        };

    if (existingManifest != updatedManifest) {
      try {
        client.create(
            manifestBlobInfoWithMetadata(existingManifest.id(), updatedManifest),
            MAPPER.writeValueAsBytes(updatedManifest));
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Lists backup statuses by reading status information directly from blob custom metadata,
   * avoiding the need to download each manifest's content. Falls back to downloading the manifest
   * for blobs that don't have metadata (written by older versions).
   */
  public Collection<BackupStatus> listBackupStatuses(final BackupIdentifierWildcard wildcard) {
    final var blobFilter = filterBlobsByWildcard(wildcard);
    LOG.debug("Listing backup statuses for wildcard {}", wildcard);
    final var listing = listManifestBlobsWithRetry(wildcardPrefix(wildcard));
    final var statusFutures = new ArrayList<CompletableFuture<BackupStatus>>();
    for (final var blob : listing) {
      if (!blobFilter.test(blob)) {
        continue;
      }
      final var fromMetadata = ManifestMetadata.toBackupStatus(blob, basePath, MANIFEST_BLOB_NAME);
      if (fromMetadata.isPresent()) {
        statusFutures.add(CompletableFuture.completedFuture(fromMetadata.get()));
      } else {
        // Fallback: download the manifest for blobs without metadata
        statusFutures.add(downloadManifestStatus(blob));
      }
    }

    CompletableFuture.allOf(statusFutures.toArray(CompletableFuture[]::new)).join();
    LOG.debug("Found {} matching backup statuses for wildcard {}", statusFutures.size(), wildcard);

    return statusFutures.stream()
        .map(CompletableFuture::join)
        .filter(status -> wildcard.matches(status.id()))
        .toList();
  }

  private List<Blob> listManifestBlobsWithRetry(final String prefix) {
    for (int attempt = 1; ; attempt++) {
      try {
        final var blobs = new ArrayList<Blob>();
        client
            .list(bucketInfo.getName(), BlobListOption.prefix(prefix))
            .iterateAll()
            .forEach(blobs::add);
        return blobs;
      } catch (final StorageException e) {
        if (attempt >= MAX_LIST_ATTEMPTS || !shouldRetryListOperation(e)) {
          throw e;
        }

        LOG.warn(
            "Failed to list GCS backup manifests from bucket '{}' with prefix '{}' on attempt {}/{}. Retrying.",
            bucketInfo.getName(),
            prefix,
            attempt,
            MAX_LIST_ATTEMPTS,
            e);
        waitBeforeListRetry(attempt);
      }
    }
  }

  private boolean shouldRetryListOperation(final StorageException e) {
    return e.isRetryable() || isServerError(e) || isTransientComputeEngineMetadataError(e);
  }

  private boolean isServerError(final StorageException e) {
    final var statusCode = e.getCode();
    return statusCode >= 500 && statusCode < 600;
  }

  private boolean isTransientComputeEngineMetadataError(final StorageException e) {
    for (Throwable current = e; current != null; current = current.getCause()) {
      final var message = current.getMessage();
      if (message != null
          && message.contains("Unexpected Error code 500")
          && message.contains("Compute Engine metadata")) {
        return true;
      }
    }
    return false;
  }

  private void waitBeforeListRetry(final int attempt) {
    final var delay =
        Duration.ofMillis(
            Math.min(
                MAX_LIST_RETRY_DELAY.toMillis(),
                INITIAL_LIST_RETRY_DELAY.toMillis() << (attempt - 1)));
    try {
      Thread.sleep(delay);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StorageException(0, "Interrupted while retrying GCS backup manifest listing", e);
    }
  }

  private CompletableFuture<BackupStatus> downloadManifestStatus(final Blob blob) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            final var manifest =
                MAPPER.readValue(client.readAllBytes(blob.getBlobId()), Manifest.class);
            return Manifest.toStatus(manifest);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        },
        executor);
  }

  public void deleteManifest(final Manifest manifest) {
    final var blobInfo = manifestBlobInfo(manifest.id());
    client.delete(blobInfo.getBlobId());
  }

  public void markAsDeleted(final Manifest manifest) {
    final var deletedManifest =
        switch (manifest.statusCode()) {
          case DELETED -> manifest;
          case COMPLETED -> manifest.asCompleted().delete();
          case IN_PROGRESS -> manifest.asInProgress().delete();
          case FAILED -> manifest.asFailed().delete();
        };
    if (manifest != deletedManifest) {
      try {
        client.create(
            manifestBlobInfoWithMetadata(manifest.id(), deletedManifest),
            MAPPER.writeValueAsBytes(deletedManifest));
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private BlobInfo manifestBlobInfo(final BackupIdentifier id) {
    final var blobName =
        MANIFEST_PATH_FORMAT.formatted(
            basePath, id.partitionId(), id.checkpointId(), id.brokerId().id(), MANIFEST_BLOB_NAME);
    return BlobInfo.newBuilder(bucketInfo, blobName).setContentType("application/json").build();
  }

  private BlobInfo manifestBlobInfoWithMetadata(
      final BackupIdentifier id, final Manifest manifest) {
    final var blobName =
        MANIFEST_PATH_FORMAT.formatted(
            basePath, id.partitionId(), id.checkpointId(), id.brokerId().id(), MANIFEST_BLOB_NAME);
    return BlobInfo.newBuilder(bucketInfo, blobName)
        .setContentType("application/json")
        .setMetadata(ManifestMetadata.fromManifest(manifest))
        .build();
  }

  /**
   * Tries to build the longest possible prefix based on the given wildcard. If the first component
   * of prefix is not present in the wildcard, the prefix will be empty. If the second component of
   * the prefix is empty, the prefix will only contain the first prefix component and so forth.
   */
  private String wildcardPrefix(final BackupIdentifierWildcard wildcard) {
    return MANIFESTS_ROOT_PATH_FORMAT.formatted(basePath)
        + BackupIdentifierWildcard.asPrefix(wildcard);
  }

  private Predicate<Blob> filterBlobsByWildcard(final BackupIdentifierWildcard wildcard) {
    final var pattern =
        Pattern.compile(
                MANIFEST_PATH_FORMAT.formatted(
                    Pattern.quote(basePath),
                    wildcard.partitionId().map(Number::toString).orElse("\\d+"),
                    wildcard.checkpointPattern().asRegex(),
                    BackupIdentifierWildcard.memberIdRegex(wildcard),
                    Pattern.quote(MANIFEST_BLOB_NAME)))
            .asMatchPredicate();
    return (blob -> pattern.test(blob.getName()));
  }

  record PersistedManifest(Long generation, InProgressManifest manifest) {}
}
