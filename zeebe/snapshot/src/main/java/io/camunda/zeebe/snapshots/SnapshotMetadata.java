/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

public interface SnapshotMetadata {

  /**
   * @return version of the snapshot
   */
  int version();

  /**
   * @return upper bound processed position in the snapshot, as determined after taking the
   *     snapshot. Same as in SnapshotId.
   */
  long processedPosition();

  /**
   * Smallest exported position in the snapshot, as determined before taking the snapshot. Same as
   * in SnapshotId. The true exported position is somewhere between the min and max.
   */
  long minExportedPosition();

  /**
   * Returns the maximum exported position in the snapshot, as determined after taking the snapshot.
   * The true exported position is somewhere between the min and max.
   */
  long maxExportedPosition();

  /**
   * A snapshot is only valid if the logstream consists of the events from the processedPosition up
   * to the followup event position.
   *
   * @return position of the last followUpEvent that must be in the logstream to ensure that the
   *     system can recover from the snapshot and the logstream.
   */
  long lastFollowupEventPosition();

  /**
   * @return true if the snapshot is a bootstrap snapshot, i.e. a snapshot used to bootstrap a new
   *     partition with the "global" data from another partition
   */
  boolean isBootstrap();

  /**
   * Returns the total bytes of the data files in this snapshot, captured at the time the snapshot
   * was persisted. Used by replication-lag accounting to know the size of an in-flight snapshot
   * install without walking the filesystem on the hot path.
   *
   * <p>Returns {@code 0} for snapshots persisted before this field was introduced; consumers that
   * need an exact value for legacy snapshots must fall back to a one-time {@link
   * java.nio.file.Files#size} walk.
   *
   * <p>Excludes the metadata file itself (which is written after the size is captured) and the
   * checksum file (which lives outside the snapshot directory).
   */
  long totalSizeBytes();
}
