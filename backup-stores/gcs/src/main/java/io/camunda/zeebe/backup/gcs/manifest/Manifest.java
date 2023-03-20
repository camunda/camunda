/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.gcs.manifest.Manifest.StatusCode.IN_PROGRESS;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.time.Instant;

@JsonSerialize(as = ManifestImpl.class)
@JsonDeserialize(as = ManifestImpl.class)
public sealed interface Manifest {

  static InProgressManifest create(Backup backup) {
    final var creationTime = Instant.now();
    return new ManifestImpl(
        BackupIdentifierImpl.from(backup.id()),
        BackupDescriptorImpl.from(backup.descriptor()),
        IN_PROGRESS,
        creationTime,
        creationTime);
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
