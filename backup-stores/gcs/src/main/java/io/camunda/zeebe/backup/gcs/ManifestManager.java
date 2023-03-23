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
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.gcs.manifest.Manifest;
import io.camunda.zeebe.backup.gcs.manifest.Manifest.InProgressManifest;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class ManifestManager {
  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(Include.NON_ABSENT);

  private final BucketInfo bucketInfo;
  private final Storage client;

  ManifestManager(final Storage client, final BucketInfo bucketInfo) {
    this.bucketInfo = bucketInfo;
    this.client = client;
  }

  CurrentManifest createInitialManifest(final String prefix, final Backup backup) {
    final var manifestBlobInfo = manifestBlobInfo(prefix);
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

  Manifest getManifest(final String prefix) {
    final var blob = client.get(manifestBlobInfo(prefix).getBlobId());
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

  private BlobInfo manifestBlobInfo(final String prefix) {
    return BlobInfo.newBuilder(bucketInfo, prefix + "manifest.json")
        .setContentType("application/json")
        .build();
  }

  public void failManifest(final CurrentManifest currentManifest, final String message) {
    final var blob = currentManifest.blob();
    final var failed = currentManifest.manifest().fail(message);
    try {
      client.create(
          blob.asBlobInfo(),
          MAPPER.writeValueAsBytes(failed),
          BlobTargetOption.generationMatch(blob.getGeneration()));
    } catch (final StorageException e) {
      if (e.getCode() == 412) { // 412 Precondition Failed, blob have changed
        throw new UnexpectedManifestState(
            "Tried to mark backup %s as failed but manifest was modified unexpectedly"
                .formatted(failed.id()));
      } else {
        throw e;
      }
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  record CurrentManifest(Blob blob, InProgressManifest manifest) {}
}
