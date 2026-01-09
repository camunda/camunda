/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.s3.manifest.Manifest;

public abstract sealed class S3BackupStoreException extends RuntimeException {

  private S3BackupStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Thrown when the {@link Manifest} object exists but can't be parsed. This is unlikely to be
   * recoverable and indicates a corrupted backup.
   */
  public static final class ManifestParseException extends S3BackupStoreException {
    public ManifestParseException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Thrown when reading some parts of the backup from S3 failed. This might be a transient error
   * and does not necessarily indicate a failed or corrupted backup.
   */
  public static final class BackupReadException extends S3BackupStoreException {
    public BackupReadException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Thrown when the backup is in an invalid state, for example when attempting to restore a failed
   * backup or deleting an in progress backup.
   */
  public static final class BackupInInvalidStateException extends S3BackupStoreException {
    public BackupInInvalidStateException(final String message) {
      super(message, null);
    }

    public BackupInInvalidStateException(
        final Manifest manifest, final BackupStatusCode expectedStatus) {
      this(
          "Expected %s to be %s but was %s"
              .formatted(manifest.id(), expectedStatus, manifest.statusCode()));
    }
  }

  /**
   * Thrown when not all objects belonging to a backup were deleted successfully. Retrying the
   * deletion might resolve this.
   */
  public static final class BackupDeletionIncomplete extends S3BackupStoreException {

    public BackupDeletionIncomplete(final String message) {
      super(message, null);
    }
  }

  /**
   * Thrown when compression of backup contents failed. This is expected when there's no space
   * available to create a temporary file for the compressed contents but may happen in other cases
   * as well.
   */
  public static final class BackupCompressionFailed extends S3BackupStoreException {

    public BackupCompressionFailed(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
