/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/**
 * Write log entries to the log stream write buffer as batch. This ensures that the log entries are
 * written atomically.
 *
 * <p>Note that the log entry data is buffered until {@link #tryWrite()} is called.
 */
public interface LogStreamBatchWriter extends LogStreamWriter {
  /** Set the source event for all log entries. */
  LogStreamBatchWriter sourceRecordPosition(long position);

  /** Returns the builder to add a new log entry to the batch. */
  LogEntryBuilder event();

  int getMaxFragmentLength();

  /** Discard all non-written batch data. */
  void reset();

  /**
   * Returns the actual number of bytes that will be written when adding a {@link LoggedEvent} to
   * the current batch and writing it.
   *
   * @param eventLength the length of the event
   * @return the framed length of the batch, i.e. the actual number of bytes required to write this
   *     batch with the additional event
   */
  int getBatchFramedLength(final int eventLength);

  /** Builder to add a log entry to the batch. */
  interface LogEntryBuilder {
    /** Use the default values as key. */
    LogEntryBuilder keyNull();

    /** Set the log entry key. */
    LogEntryBuilder key(long key);

    /**
     * Can be used if command and event, which is caused by this command is written in batch.
     *
     * @param index the index in this batch
     */
    LogEntryBuilder sourceIndex(int index);

    /** Set the log entry metadata. */
    LogEntryBuilder metadata(DirectBuffer buffer, int offset, int length);

    /** Set the log entry metadata. */
    LogEntryBuilder metadata(DirectBuffer buffer);

    /** Set the log entry metadata. */
    LogEntryBuilder metadataWriter(BufferWriter writer);

    /** Set the log entry value. */
    LogEntryBuilder value(DirectBuffer value, int valueOffset, int valueLength);

    /** Set the log entry value. */
    LogEntryBuilder value(DirectBuffer value);

    /** Set the log entry value. */
    LogEntryBuilder valueWriter(BufferWriter writer);

    /** Add the log entry to the batch. */
    LogStreamBatchWriter done();
  }
}
