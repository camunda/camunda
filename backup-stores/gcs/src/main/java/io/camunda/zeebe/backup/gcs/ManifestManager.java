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
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import io.camunda.zeebe.backup.gcs.manifest.Manifest.InProgressManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public final class ManifestManager {
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);
  private static final String MANIFEST_PREFIX = "manifests/";
  private static final String MANIFEST_BLOB_NAME = "manifest.json";
  private final BucketInfo bucketInfo;
  private final Storage client;
  private final String prefix;
  private final Pattern backupIdPattern;

  ManifestManager(final Storage client, final BucketInfo bucketInfo, final String prefix) {
    this.bucketInfo = bucketInfo;
    this.client = client;
    this.prefix = prefix + MANIFEST_PREFIX;
    backupIdPattern =
        Pattern.compile(
            "^"
                + Pattern.quote(this.prefix)
                + "(?<partitionId>\\d+)/(?<checkpointId>\\d+)/(?<nodeId>\\d+)/"
                + Pattern.quote(MANIFEST_BLOB_NAME)
                + "$");
  }

  CurrentManifest createInitialManifest(final Backup backup) {
    final var manifestBlobInfo = manifestBlobInfo(backup.id());
    final var manifest = Manifest.create(backup);
    try {
      final var blob =
          client.create(
              manifestBlobInfo,
              MAPPER.writeValueAsBytes(manifest),
              BlobTargetOption.doesNotExist());
      return new CurrentManifest(blob, manifest);
    } catch (final StorageException e) {
      if (e.getCode() == 412) { // 412 Precondition Failed, blob must already exist
        throw new UnexpectedManifestState(
            "Manifest for backup %s already exists".formatted(backup.id()));
      } else {
        throw e;
      }
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  void completeManifest(final CurrentManifest currentManifest) {
    final var blob = currentManifest.blob();
    final var completed = currentManifest.manifest().complete();
    try {
      client.create(
          blob.asBlobInfo(),
          MAPPER.writeValueAsBytes(completed),
          BlobTargetOption.generationMatch(blob.getGeneration()));
    } catch (final StorageException e) {
      if (e.getCode() == 412) { // 412 Precondition Failed, blob have changed
        throw new UnexpectedManifestState(
            "Manifest for backup %s was modified unexpectedly".formatted(completed.id()));
      }
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  Manifest getManifest(final BackupIdentifier id) {
    final var blob = client.get(manifestBlobInfo(id).getBlobId());
    if (blob == null) {
      return null;
    } else {
      try {
        return MAPPER.readValue(blob.getContent(), Manifest.class);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public void markAsFailed(final BackupIdentifier id, final String failureReason) {
    final var blobInfo = manifestBlobInfo(id);
    final var blob = client.get(blobInfo.getBlobId());
    try {
      if (blob == null) {
        final var failed = Manifest.createFailed(id);
        client.create(blobInfo, MAPPER.writeValueAsBytes(failed), BlobTargetOption.doesNotExist());
      } else {
        final var existingManifest = MAPPER.readValue(blob.getContent(), Manifest.class);
        final var updatedManifest =
            switch (existingManifest.statusCode()) {
              case FAILED -> existingManifest.asFailed();
              case COMPLETED -> existingManifest.asCompleted().fail(failureReason);
              case IN_PROGRESS -> existingManifest.asInProgress().fail(failureReason);
            };

        if (existingManifest != updatedManifest) {
          client.create(
              blobInfo,
              MAPPER.writeValueAsBytes(updatedManifest),
              BlobTargetOption.generationMatch(blob.getGeneration()));
        }
      }
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Collection<Manifest> listManifests(final BackupIdentifierWildcard wildcard) {
    final var spliterator =
        Spliterators.spliteratorUnknownSize(
            client
                .list(bucketInfo.getName(), BlobListOption.prefix(prefix))
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
    final var backupPath = "%s/%s/%s/".formatted(id.partitionId(), id.checkpointId(), id.nodeId());
    return BlobInfo.newBuilder(bucketInfo, prefix + backupPath + MANIFEST_BLOB_NAME)
        .setContentType("application/json")
        .build();
  }

  private Predicate<Blob> filterBlobsByWildcard(final BackupIdentifierWildcard wildcard) {
    return (blob -> {
      final var matcher = backupIdPattern.matcher(blob.getName());
      if (!matcher.matches()) {
        return false;
      }
      final var nodeId = Integer.parseInt(matcher.group("nodeId"));
      final var partitionId = Integer.parseInt(matcher.group("partitionId"));
      final var checkpointId = Long.parseLong(matcher.group("checkpointId"));
      return wildcard.matches(new BackupIdentifierImpl(nodeId, partitionId, checkpointId));
    });
  }

  record CurrentManifest(Blob blob, InProgressManifest manifest) {}
}
