/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

public abstract sealed class S3BackupStoreException extends RuntimeException {

  private S3BackupStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Thrown when the {@link Status} object exists but can't be parsed. This is unlikely to be
   * recoverable and indicates a corrupted backup.
   */
  public static final class StatusParseException extends S3BackupStoreException {
    public StatusParseException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Thrown when the {@link Metadata} object exists but can't be parsed. This is unlikely to be
   * recoverable and indicates a corrupted backup.
   */
  public static final class MetadataParseException extends S3BackupStoreException {
    public MetadataParseException(final String message, final Throwable cause) {
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
}
