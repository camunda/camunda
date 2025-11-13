/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.manifest;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import java.time.Instant;
import java.util.Optional;

public record NoBackupManifest(BackupIdentifierImpl id) implements Manifest {

  @Override
  public BackupStatusCode statusCode() {
    return BackupStatusCode.DOES_NOT_EXIST;
  }

  @Override
  public BackupStatus toStatus() {
    return new BackupStatusImpl(
        id, Optional.empty(), statusCode(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  @Override
  public FailedBackupManifest asFailed(final String failureReason) {
    final var now = Instant.now();
    return new FailedBackupManifest(
        id, Optional.empty(), failureReason, FileSet.empty(), FileSet.empty(), now, now);
  }

  public InProgressBackupManifest asInProgress(final Backup backup) {
    final var now = Instant.now();
    return new InProgressBackupManifest(
        BackupIdentifierImpl.from(backup.id()),
        BackupDescriptorImpl.from(backup.descriptor()),
        FileSet.withoutMetadata(backup.snapshot().names()),
        FileSet.withoutMetadata(backup.segments().names()),
        now,
        now);
  }
}
