/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.COMPLETED;
import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.FAILED;
import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.IN_PROGRESS;

import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.InvalidPersistedManifestState;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.UnexpectedManifestState;
import java.time.Instant;

record ManifestImpl(
    BackupIdentifierImpl id,
    BackupDescriptorImpl descriptor,
    StatusCode statusCode,
    FileSet snapshot,
    FileSet segments,
    Instant createdAt,
    Instant modifiedAt,
    String failureReason)
    implements Manifest.InProgressManifest, Manifest.CompletedManifest, Manifest.FailedManifest {

  ManifestImpl {
    if (failureReason != null && statusCode != FAILED) {
      throw new InvalidPersistedManifestState(
          "Manifest in state '%s' must be 'FAILED to have have failureReason '%s'"
              .formatted(statusCode, failureReason));
    }
  }

  ManifestImpl(
      final BackupIdentifierImpl id,
      final BackupDescriptorImpl descriptor,
      final StatusCode statusCode,
      final FileSet snapshot,
      final FileSet segments,
      final Instant createdAt,
      final Instant modifiedAt) {
    this(id, descriptor, statusCode, snapshot, segments, createdAt, modifiedAt, null);
  }

  @Override
  public CompletedManifest complete() {
    return new ManifestImpl(
        id, descriptor, COMPLETED, snapshot, segments, createdAt, Instant.now());
  }

  @Override
  public FailedManifest fail(final String failureReason) {
    return new ManifestImpl(
        id, descriptor, FAILED, snapshot, segments, createdAt, Instant.now(), failureReason);
  }

  @Override
  public InProgressManifest asInProgress() {
    if (statusCode != IN_PROGRESS) {
      throw new UnexpectedManifestState(IN_PROGRESS, statusCode);
    }

    return this;
  }

  @Override
  public CompletedManifest asCompleted() {
    if (statusCode != COMPLETED) {
      throw new UnexpectedManifestState(COMPLETED, statusCode);
    }

    return this;
  }

  @Override
  public FailedManifest asFailed() {
    if (statusCode != FAILED) {
      throw new UnexpectedManifestState(FAILED, statusCode);
    }

    return this;
  }
}
