/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.manifest;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import java.time.Instant;
import java.util.Optional;

public record CompletedBackupManifest(
    BackupIdentifierImpl id,
    BackupDescriptorImpl descriptor,
    @JsonAlias("snapshotFileNames") FileSet snapshotFiles,
    @JsonAlias("segmentFileNames") FileSet segmentFiles,
    Instant createdAt,
    Instant modifiedAt)
    implements ValidBackupManifest {

  @Override
  public BackupStatusCode statusCode() {
    return BackupStatusCode.COMPLETED;
  }

  @Override
  public BackupStatus toStatus() {
    return new BackupStatusImpl(
        id,
        Optional.of(descriptor),
        statusCode(),
        Optional.empty(),
        Optional.of(createdAt),
        Optional.of(modifiedAt));
  }

  @Override
  public FailedBackupManifest asFailed(final String failureReason) {
    return new FailedBackupManifest(
        id,
        Optional.of(descriptor),
        failureReason,
        snapshotFiles,
        segmentFiles,
        createdAt,
        Instant.now());
  }

  @Override
  public Optional<BackupDescriptor> backupDescriptor() {
    return Optional.of(descriptor);
  }
}
