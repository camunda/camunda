/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;

@FunctionalInterface
public interface LogStreamWriter {

  /**
   * Returns true if the given eventCount with the given batchLength could potentially be written,
   * false otherwise.
   *
   * @param eventCount the potential event count we want to check
   * @param batchSize the potential batch Size (in bytes) we want to check
   * @return true if the event count with corresponding size could be written, false otherwise
   */
  default boolean canWriteEvents(final int eventCount, final int batchSize) {
    return true;
  }

  /**
   * Attempts to write the event to the underlying stream.
   *
   * @param appendEntry the entry to write
   * @return the event position, a negative value if fails to write the event, or 0 if the value is
   *     empty
   */
  default Either<WriteFailure, Long> tryWrite(final LogAppendEntry appendEntry) {
    return tryWrite(appendEntry, LogEntryDescriptor.KEY_NULL_VALUE);
  }

  /** {@inheritDoc} */
  default Either<WriteFailure, Long> tryWrite(
      final LogAppendEntry appendEntry, final long sourcePosition) {
    return tryWrite(Collections.singletonList(appendEntry), sourcePosition);
  }

  /**
   * Attempts to write the events to the underlying stream. This method is atomic, either all events
   * are written, or none are.
   *
   * @param appendEntries a set of entries to append; these will be appended in the order
   * @return the last (i.e. highest) event position, a negative value if fails to write the events,
   *     or 0 if the batch is empty
   */
  default Either<WriteFailure, Long> tryWrite(final List<LogAppendEntry> appendEntries) {
    return tryWrite(appendEntries, LogEntryDescriptor.KEY_NULL_VALUE);
  }

  /**
   * Attempts to write the events to the underlying stream. This method is atomic, either all events
   * are written, or none are.
   *
   * @param appendEntries a list of entries to append; append order is maintained
   * @param sourcePosition a back-pointer to the record whose processing created these entries
   * @return the last (i.e. highest) event position, a negative value if fails to write the events,
   *     or 0 if the batch is empty
   */
  Either<WriteFailure, Long> tryWrite(
      final List<LogAppendEntry> appendEntries, final long sourcePosition);

  enum WriteFailure {
    CLOSED,
    FULL,
    INVALID_ARGUMENT
  }
}
