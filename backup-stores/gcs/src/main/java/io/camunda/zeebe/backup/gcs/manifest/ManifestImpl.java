/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.gcs.manifest.BackupStatusCode.COMPLETED;
import static io.camunda.zeebe.backup.gcs.manifest.BackupStatusCode.FAILED;
import static io.camunda.zeebe.backup.gcs.manifest.BackupStatusCode.IN_PROGRESS;

import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.time.Instant;

record ManifestImpl(
    BackupIdentifierImpl id,
    BackupDescriptorImpl descriptor,
    BackupStatusCode statusCode,
    Instant createdAt,
    Instant modifiedAt,
    String failureReason)
    implements InProgressManifest, CompletedManifest, FailedManifest {

  ManifestImpl {
    if (failureReason != null && statusCode != FAILED) {
      final var errorMessage =
          String.format(
              "Expected to set failureReason '%s', with status code 'FAILED' but was '%s'",
              failureReason, statusCode);
      throw new InvalidPersistedManifestState(errorMessage);
    }
  }

  ManifestImpl(
      final BackupIdentifierImpl id,
      final BackupDescriptorImpl descriptor,
      final BackupStatusCode statusCode,
      final Instant createdAt,
      final Instant modifiedAt) {
    this(id, descriptor, statusCode, createdAt, modifiedAt, null);
  }

  @Override
  public CompletedManifest complete() {
    return new ManifestImpl(id, descriptor, COMPLETED, createdAt, Instant.now());
  }

  @Override
  public FailedManifest fail(final String failureReason) {
    return new ManifestImpl(id, descriptor, FAILED, createdAt, Instant.now(), failureReason);
  }

  @Override
  public InProgressManifest asInProgress() {
    if (statusCode != IN_PROGRESS) {
      final String errorMsg =
          String.format("Expected to be in 'IN_PROGRESS' state, but was in '%s'", statusCode);
      throw new IllegalStateException(errorMsg);
    }

    return this;
  }

  @Override
  public CompletedManifest asCompleted() {
    if (statusCode != COMPLETED) {
      final String errorMsg =
          String.format("Expected to be in 'COMPLETED' state, but was in '%s'", statusCode);
      throw new IllegalStateException(errorMsg);
    }

    return this;
  }

  @Override
  public FailedManifest asFailed() {
    if (statusCode != FAILED) {
      final String errorMsg =
          String.format("Expected to be in 'FAILED' state, but was in '%s'", statusCode);
      throw new IllegalStateException(errorMsg);
    }

    return this;
  }
}
