/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import static io.camunda.zeebe.backup.gcs.manifest.BackupStatusCode.IN_PROGRESS;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.time.Instant;

@JsonSerialize(as = ManifestImpl.class)
@JsonDeserialize(as = ManifestImpl.class)
public interface Manifest {

  static InProgressManifest createManifest(
      final BackupIdentifierImpl id, final BackupDescriptorImpl descriptor) {
    final Instant creationTime = Instant.now();
    return new ManifestImpl(id, descriptor, IN_PROGRESS, creationTime, creationTime);
  }

  BackupIdentifierImpl id();

  BackupDescriptorImpl descriptor();

  BackupStatusCode statusCode();

  Instant createdAt();

  Instant modifiedAt();

  InProgressManifest asInProgress();

  CompletedManifest asCompleted();

  FailedManifest asFailed();
}
