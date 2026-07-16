/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

/**
 * The cleaner's read seam over the raw ("dirty") Raft log — the part of the log not yet folded into
 * the clean set. It yields the individual records in a position range in ascending position order.
 * The cleaner reads the dirty range twice per pass: once to build the {@link KeyOffsetMap}, once
 * during the sweep.
 *
 * <p>The production implementation ({@link EventStreamDirtyLogReader}) adapts the journal's {@link
 * io.camunda.eventbridge.messaging.stream.EventStreamReader}, decoding each batch into its
 * constituent records; tests feed records directly.
 *
 * <p>Threading: implementations are driven by the single cleaner actor.
 */
public interface DirtyLogReader {

  /**
   * Visits every record with a position in the half-open-then-closed range {@code (fromExclusive,
   * toInclusive]}, in ascending position order, until the range is exhausted or the visitor returns
   * {@code false}.
   *
   * @param fromExclusive positions strictly greater than this are visited (pass {@link
   *     CompactionManifest#NO_CLEANER_POINT} to include from the very start)
   * @param toInclusive positions up to and including this are visited
   * @param visitor invoked per record; returning {@code false} stops the scan early (used when the
   *     key-offset map overflows)
   */
  void read(long fromExclusive, long toInclusive, DirtyRecordVisitor visitor);

  /** Callback for {@link DirtyLogReader#read}. */
  @FunctionalInterface
  interface DirtyRecordVisitor {
    /**
     * Handles one record.
     *
     * @param record the record
     * @return {@code true} to continue, {@code false} to stop the scan
     */
    boolean visit(CompactionRecord record);
  }
}
