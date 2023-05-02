/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import io.camunda.zeebe.backup.gcs.manifest.Manifest.InProgressManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
   *   <li>{@code nodeId}
   *   <li>{@code "manifest.json"}
   * </ul>
   */
  private static final String MANIFEST_PATH_FORMAT = MANIFESTS_ROOT_PATH_FORMAT + "%s/%s/%s/%s";

  private static final String MANIFEST_BLOB_NAME = "manifest.json";
  private final BucketInfo bucketInfo;
  private final Storage client;
  private final String basePath;

  ManifestManager(final Storage client, final BucketInfo bucketInfo, final String basePath) {
    this.bucketInfo = bucketInfo;
    this.client = client;
    this.basePath = basePath;
  }

  PersistedManifest createInitialManifest(final Backup backup) {
    final var manifestBlobInfo = manifestBlobInfo(backup.id());
    final var manifest = Manifest.createInProgress(backup);
    try {
      final var blob =
          client.create(
              manifestBlobInfo,
              MAPPER.writeValueAsBytes(manifest),
              BlobTargetOption.doesNotExist());
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
          manifestBlobInfo(completed.id()),
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
    final var blobInfo = manifestBlobInfo(id);
    final var blob = client.get(blobInfo.getBlobId());
    try {
      if (blob == null) {
        final var failed = Manifest.createFailed(id);
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
        };

    if (existingManifest != updatedManifest) {
      try {
        client.create(
            manifestBlobInfo(existingManifest.id()), MAPPER.writeValueAsBytes(updatedManifest));
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    final var spliterator =
        Spliterators.spliteratorUnknownSize(
            client
                .list(bucketInfo.getName(), BlobListOption.prefix(wildcardPrefix(wildcard)))
                .iterateAll()
                .iterator(),
            Spliterator.IMMUTABLE);
    return StreamSupport.stream(spliterator, false)
        .filter(filterBlobsByWildcard(wildcard))
        .parallel()
        .map(Blob::getContent)
        .map(
            content -> {
              try {
                return MAPPER.readValue(content, Manifest.class);
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  public void deleteManifest(final BackupIdentifier id) {
    client.delete(manifestBlobInfo(id).getBlobId());
  }

  private BlobInfo manifestBlobInfo(final BackupIdentifier id) {
    final var blobName =
        MANIFEST_PATH_FORMAT.formatted(
            basePath, id.partitionId(), id.checkpointId(), id.nodeId(), MANIFEST_BLOB_NAME);
    return BlobInfo.newBuilder(bucketInfo, blobName).setContentType("application/json").build();
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
        .collect(Collectors.joining("/", MANIFESTS_ROOT_PATH_FORMAT.formatted(basePath), ""));
  }

  private Predicate<Blob> filterBlobsByWildcard(final BackupIdentifierWildcard wildcard) {
    final var pattern =
        Pattern.compile(
                MANIFEST_PATH_FORMAT.formatted(
                    Pattern.quote(basePath),
                    wildcard.partitionId().map(Number::toString).orElse("\\d+"),
                    wildcard.checkpointId().map(Number::toString).orElse("\\d+"),
                    wildcard.nodeId().map(Number::toString).orElse("\\d+"),
                    Pattern.quote(MANIFEST_BLOB_NAME)))
            .asMatchPredicate();
    return (blob -> pattern.test(blob.getName()));
  }

  record PersistedManifest(Long generation, InProgressManifest manifest) {}
}
