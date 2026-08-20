/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.manifest;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupInInvalidStateException;
import java.io.PrintWriter;
import java.io.StringWriter;

public sealed interface Manifest permits ValidBackupManifest, NoBackupManifest {
  BackupIdentifier id();

  BackupStatusCode statusCode();

  BackupStatus toStatus();

  default FailedBackupManifest asFailed(final Throwable throwable) {
    final var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return asFailed(writer.toString());
  }

  FailedBackupManifest asFailed(final String failureReason);

  static InProgressBackupManifest fromNewBackup(final Backup backup) {
    return new NoBackupManifest(BackupIdentifierImpl.from(backup.id())).asInProgress(backup);
  }

  static NoBackupManifest expectNoBackup(final Manifest manifest) {
    if (manifest instanceof NoBackupManifest noBackup) {
      return noBackup;
    }
    throw new BackupInInvalidStateException(manifest, BackupStatusCode.DOES_NOT_EXIST);
  }

  static InProgressBackupManifest expectInProgress(final Manifest manifest) {
    if (manifest instanceof InProgressBackupManifest inProgress) {
      return inProgress;
    }
    throw new BackupInInvalidStateException(manifest, BackupStatusCode.IN_PROGRESS);
  }

  static CompletedBackupManifest expectCompleted(final Manifest manifest) {
    if (manifest instanceof CompletedBackupManifest completed) {
      return completed;
    }
    throw new BackupInInvalidStateException(manifest, BackupStatusCode.COMPLETED);
  }
}
