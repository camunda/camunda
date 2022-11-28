/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import java.util.Arrays;
import java.util.Collections;

/**
 * A {@link LogStreamWriter} which can write multiple entries at the same time.
 *
 * <p>TODO: should be collapsed with the {@link LogStreamWriter} once the dispatcher is replaced,
 * since we won't really need to differentiate between batches and single entries.
 */
public interface LogStreamBatchWriter extends LogStreamWriter {
  /**
   * Returns true if the given eventCount with the given batchLength could potentially be written
   * with the BatchWriter, false otherwise.
   *
   * @param eventCount the potential event count we want to check
   * @param batchSize the potential batch Size (in bytes) we want to check
   * @return true if the event count with corresponding size could be written, false otherwise
   */
  boolean canWriteEvents(final int eventCount, final int batchSize);

  /** {@inheritDoc} */
  @Override
  default long tryWrite(final LogAppendEntry appendEntry, final long sourcePosition) {
    return tryWrite(Collections.singleton(appendEntry), sourcePosition);
  }

  /**
   * Attempts to write the events to the underlying stream. This method is atomic, either all events
   * are written, or not are.
   *
   * @param appendEntries a set of entries to append; these will be appended in the order
   * @return the last (i.e. highest) event position or a negative value if fails to write the events
   */
  default long tryWrite(final LogAppendEntry... appendEntries) {
    return tryWrite(Arrays.asList(appendEntries));
  }

  /**
   * Attempts to write the events to the underlying stream. This method is atomic, either all events
   * are written, or not are.
   *
   * @param appendEntries a set of entries to append; these will be appended in the order in which
   *     the collection is iterated.
   * @return the last (i.e. highest) event position or a negative value if fails to write the events
   */
  default long tryWrite(final Iterable<? extends LogAppendEntry> appendEntries) {
    return tryWrite(appendEntries, LogEntryDescriptor.KEY_NULL_VALUE);
  }

  /**
   * Attempts to write the events to the underlying stream. This method is atomic, either all events
   * are written, or not are.
   *
   * @param appendEntries a set of entries to append; these will be appended in the order in which
   *     the collection is iterated.
   * @param sourcePosition a back-pointer to the record whose processing created these entries
   * @return the last (i.e. highest) event position or a negative value if fails to write the events
   */
  long tryWrite(final Iterable<? extends LogAppendEntry> appendEntries, final long sourcePosition);
}
