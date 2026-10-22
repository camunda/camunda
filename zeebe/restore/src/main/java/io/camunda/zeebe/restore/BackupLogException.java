/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import java.io.IOException;
import java.time.Duration;

/** A backup's log or snapshot isn't in the Event Plane's bucket as the backup needs it. */
public abstract sealed class BackupLogException extends IOException {

  private final int partition;
  private final long checkpointId;

  private BackupLogException(final int partition, final long checkpointId, final String message) {
    super(message);
    this.partition = partition;
    this.checkpointId = checkpointId;
  }

  public int partition() {
    return partition;
  }

  public long checkpointId() {
    return checkpointId;
  }

  /** The bucket didn't hold the checkpoint position in time. */
  public static final class NotUploaded extends BackupLogException {

    private final long checkpointPosition;
    private final long uploadedPosition;

    /**
     * @param uploadedPosition the last position the bucket held, or -1 if it held none
     */
    public NotUploaded(
        final int partition,
        final long checkpointId,
        final long checkpointPosition,
        final Duration timeout,
        final long uploadedPosition) {
      super(
          partition,
          checkpointId,
          "Expected the Event Plane's bucket to hold position %d of partition %d, the checkpoint of backup %d, within %s, but it reaches position %d only"
              .formatted(checkpointPosition, partition, checkpointId, timeout, uploadedPosition));
      this.checkpointPosition = checkpointPosition;
      this.uploadedPosition = uploadedPosition;
    }

    public long checkpointPosition() {
      return checkpointPosition;
    }

    public long uploadedPosition() {
      return uploadedPosition;
    }
  }

  /** The bucket doesn't reach back to the first position a backup needs. */
  public static final class NotInBucket extends BackupLogException {

    private final long position;
    private final long missingIndex;

    public NotInBucket(
        final int partition,
        final long checkpointId,
        final long position,
        final long missingIndex) {
      super(
          partition,
          checkpointId,
          "Expected the Event Plane's bucket to hold partition %d's log from position %d, which backup %d needs, but no segment serves index %d"
              .formatted(partition, position, checkpointId, missingIndex));
      this.position = position;
      this.missingIndex = missingIndex;
    }

    public long position() {
      return position;
    }

    public long missingIndex() {
      return missingIndex;
    }
  }

  /** A backup's snapshot isn't one the bucket can describe. */
  public static final class InvalidSnapshot extends BackupLogException {

    private final String snapshotId;

    /**
     * @param expected what the snapshot should have
     */
    public InvalidSnapshot(
        final int partition,
        final long checkpointId,
        final String snapshotId,
        final String expected) {
      super(
          partition,
          checkpointId,
          "Expected snapshot %s of backup %d of partition %d to have %s, but it hasn't"
              .formatted(snapshotId, checkpointId, partition, expected));
      this.snapshotId = snapshotId;
    }

    public String snapshotId() {
      return snapshotId;
    }
  }

  /** The bucket doesn't have a backup's snapshot. */
  public static final class NoSnapshot extends BackupLogException {

    private final String snapshotId;

    public NoSnapshot(final int partition, final long checkpointId, final String snapshotId) {
      super(
          partition,
          checkpointId,
          "Expected snapshot %s of backup %d of partition %d in the Event Plane's bucket, but it has no descriptor there"
              .formatted(snapshotId, checkpointId, partition));
      this.snapshotId = snapshotId;
    }

    public String snapshotId() {
      return snapshotId;
    }
  }

  /** A backup without segment files has no backup log in the bucket. */
  public static final class NoBackupLog extends BackupLogException {

    private final String key;

    public NoBackupLog(final int partition, final long checkpointId, final String key) {
      super(
          partition,
          checkpointId,
          "Expected backup %d of partition %d, which has no segment files, to have its log at '%s', but there is none"
              .formatted(checkpointId, partition, key));
      this.key = key;
    }

    public String key() {
      return key;
    }
  }
}
