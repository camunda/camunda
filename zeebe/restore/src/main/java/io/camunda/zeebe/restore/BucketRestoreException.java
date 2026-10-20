/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import java.io.IOException;

/** A partition can't be restored from the Event Plane's bucket, as the bucket doesn't hold it. */
public abstract sealed class BucketRestoreException extends IOException {

  private final int partition;

  private BucketRestoreException(final int partition, final String message) {
    super(message);
    this.partition = partition;
  }

  /** The partition that can't be restored. */
  public int partition() {
    return partition;
  }

  /** The tenant has no history in the bucket, or its current history doesn't have the partition. */
  public static final class NoHistory extends BucketRestoreException {

    private final String cluster;
    private final String tenant;

    public NoHistory(final String cluster, final String tenant, final int partition) {
      super(
          partition,
          "Expected a history of partition %d of cluster '%s' and tenant '%s' in the bucket, but got none"
              .formatted(partition, cluster, tenant));
      this.cluster = cluster;
      this.tenant = tenant;
    }

    public String cluster() {
      return cluster;
    }

    public String tenant() {
      return tenant;
    }
  }

  /** No segment of the path holds an entry that the restore needs. */
  public static final class MissingEntry extends BucketRestoreException {

    private final long index;

    public MissingEntry(final int partition, final long index) {
      super(
          partition,
          "Expected a segment of the current path with entry %d of partition %d, but got none"
              .formatted(index, partition));
      this.index = index;
    }

    public long index() {
      return index;
    }
  }

  /** The bucket's log ends before the position to restore to. */
  public static final class LogEndsBeforeTarget extends BucketRestoreException {

    private final long target;
    private final long lastIndex;
    private final long lastPosition;

    public LogEndsBeforeTarget(
        final int partition, final long target, final long lastIndex, final long lastPosition) {
      super(
          partition,
          "Expected the bucket to hold partition %d up to position %d, but it ends at index %d, position %d"
              .formatted(partition, target, lastIndex, lastPosition));
      this.target = target;
      this.lastIndex = lastIndex;
      this.lastPosition = lastPosition;
    }

    public long target() {
      return target;
    }

    public long lastIndex() {
      return lastIndex;
    }

    public long lastPosition() {
      return lastPosition;
    }
  }

  /**
   * Neither a snapshot nor the log from its first entry gives a journal with a record up to the
   * target.
   */
  public static final class NothingToRestore extends BucketRestoreException {

    private final long target;

    public NothingToRestore(final int partition, final long target) {
      super(
          partition,
          "Expected a snapshot of partition %d whose positions the log reaches by position %d, or the log from index 1 with a record up to it, but got neither"
              .formatted(partition, target));
      this.target = target;
    }

    public long target() {
      return target;
    }
  }

  /** A segment's entries aren't the consecutive indexes its name says. */
  public static final class UnexpectedEntry extends BucketRestoreException {

    private final String key;
    private final long expectedIndex;
    private final long actualIndex;

    public UnexpectedEntry(
        final int partition, final String key, final long expectedIndex, final long actualIndex) {
      super(
          partition,
          "Expected entry %d next in segment '%s', but got %s"
              .formatted(
                  expectedIndex,
                  key,
                  actualIndex < 0 ? "the segment's end" : "entry " + actualIndex));
      this.key = key;
      this.expectedIndex = expectedIndex;
      this.actualIndex = actualIndex;
    }

    public String key() {
      return key;
    }

    public long expectedIndex() {
      return expectedIndex;
    }

    /** The index of the entry found instead, or -1 if the segment ended. */
    public long actualIndex() {
      return actualIndex;
    }
  }
}
