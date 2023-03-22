/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.FAILED;
import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.IN_PROGRESS;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import java.time.Instant;
import java.util.Optional;

@JsonSerialize(as = ManifestImpl.class)
@JsonDeserialize(as = ManifestImpl.class)
public sealed interface Manifest {

  static InProgressManifest create(final Backup backup) {
    final var creationTime = Instant.now();
    return new ManifestImpl(
        BackupIdentifierImpl.from(backup.id()),
        BackupDescriptorImpl.from(backup.descriptor()),
        IN_PROGRESS,
        FileSet.of(backup.snapshot()),
        FileSet.of(backup.segments()),
        creationTime,
        creationTime);
  }

  static FailedManifest createFailed(final BackupIdentifier id) {
    final var creationTime = Instant.now();
    return new ManifestImpl(
        BackupIdentifierImpl.from(id), null, FAILED, null, null, creationTime, creationTime);
  }

  static BackupStatus toStatus(final Manifest manifest) {
    return switch (manifest.statusCode()) {
      case IN_PROGRESS -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.IN_PROGRESS,
          Optional.empty(),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
      case COMPLETED -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.COMPLETED,
          Optional.empty(),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
      case FAILED -> new BackupStatusImpl(
          manifest.id(),
          Optional.ofNullable(manifest.descriptor()),
          BackupStatusCode.FAILED,
          Optional.ofNullable(manifest.asFailed().failureReason()),
          Optional.ofNullable(manifest.createdAt()),
          Optional.ofNullable(manifest.modifiedAt()));
    };
  }

  BackupIdentifierImpl id();

  BackupDescriptorImpl descriptor();

  StatusCode statusCode();

  Instant createdAt();

  Instant modifiedAt();

  InProgressManifest asInProgress();

  CompletedManifest asCompleted();

  FailedManifest asFailed();

  sealed interface InProgressManifest extends Manifest permits ManifestImpl {

    CompletedManifest complete();

    FailedManifest fail(final String failureReason);
  }

  sealed interface CompletedManifest extends Manifest permits ManifestImpl {

    FailedManifest fail(final String failureReason);

    FileSet snapshot();

    FileSet segments();
  }

  sealed interface FailedManifest extends Manifest permits ManifestImpl {

    String failureReason();
  }

  enum StatusCode {
    IN_PROGRESS,
    COMPLETED,
    FAILED
  }
}
