/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;

/**
 * In-memory notifications from the {@link LogArchiveRecordsProcessor} to the leader components
 * (archiver, snapshot uploader, publisher).
 *
 * <p>All methods are called on the stream processor thread after the record that caused them is
 * committed, and only while processing, never while replaying. Implementations must hop to their
 * own actor and return quickly. All methods have empty default implementations.
 */
public interface LogArchiveListener {

  /** A {@code TIMELINE_STARTED} event was committed. */
  default void onTimelineStarted(final String timelineId) {}

  /** A {@code LOG_ARCHIVED} event was committed. The arguments are F after applying it. */
  default void onLogArchived(final long archivedIndex, final long archivedPosition) {}

  /**
   * An {@code ARCHIVE_LOG} command was rejected.
   *
   * @param archivedIndex the current F; the archiver resets its cursor to it
   */
  default void onArchiveLogRejected(final long archivedIndex) {}

  /** A {@code SNAPSHOT_ARCHIVED} event was committed. */
  default void onSnapshotArchived(final String snapshotId) {}

  /** An {@code ARCHIVE_SNAPSHOT} command was rejected. */
  default void onSnapshotRejected(final String snapshotId) {}

  /** An {@code INDEX_SEALED} event was committed. */
  default void onIndexSealed(final long chunkNo) {}

  /** A {@code LOG_MERGED} event was committed. The arguments are M after applying it. */
  default void onLogMerged(final long mergedIndex, final long mergedPosition) {}

  /**
   * A {@code LOG_MERGED} event was committed, with the seq of its commit entry; the cleaner needs
   * it to know when the event plane has seen a merge (spec section 20.4). The processor calls this
   * one; by default it forwards to {@link #onLogMerged(long, long)}.
   */
  default void onLogMerged(final long seq, final long mergedIndex, final long mergedPosition) {
    onLogMerged(mergedIndex, mergedPosition);
  }

  /**
   * A {@code MERGE_LOG} command was rejected.
   *
   * @param mergedIndex the current M; the merger looks for the next merge from it
   */
  default void onMergeLogRejected(final long mergedIndex) {}

  /** A {@code LOG_RELEASED} event was committed. The argument is R after applying it. */
  default void onLogReleased(final long releasedIndex) {}

  /**
   * A {@code RELEASE_LOG} command was rejected.
   *
   * @param releasedIndex the current R
   */
  default void onReleaseLogRejected(final long releasedIndex) {}

  /** A {@code PUBLISHED} event was committed. */
  default void onPublished(final long publishedSeq) {}

  /**
   * A {@code START_TIMELINE}, {@code SEAL_INDEX} or {@code CONFIRM_PUBLISHED} command was rejected.
   * ({@code ARCHIVE_LOG} and {@code ARCHIVE_SNAPSHOT} have their own callbacks.)
   */
  default void onCommandRejected(
      final LogArchiveIntent command, final RejectionType type, final String reason) {}

  /**
   * The processor finished recovering on this partition and is about to deliver the outbox to the
   * {@link LogArchivePublication}. The meta is a copy.
   */
  default void onRecovered(final LogArchiveMeta meta) {}

  /**
   * Called on recovery right after {@link #onRecovered(LogArchiveMeta)}, with the archived snapshot
   * that has the highest processed position in the current timeline, or {@code ("", -1)} if none.
   * Called only while processing, from the stream processor thread.
   */
  default void onNewestArchivedSnapshotRecovered(
      final String snapshotId, final long processedPosition) {}
}
