/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/** Builder to add a log entry to the batch. */
public interface LogEntrysBuilder {

  boolean canWriteAdditionalEvent(final int length);

  int getMaxFragmentLength();

  /** Discard all non-written batch data. */
  void reset();
  /** Returns the builder to add a new log entry to the batch. */
  LogEntrysBuilder event();

  /** Use the default values as key. */
  LogEntrysBuilder keyNull();

  /** Set the log entry key. */
  LogEntrysBuilder key(long key);

  /**
   * Can be used if command and event, which is caused by this command is written in batch.
   *
   * @param index the index in this batch
   */
  LogEntrysBuilder sourceIndex(int index);

  /** Set the log entry metadata. */
  LogEntrysBuilder metadata(DirectBuffer buffer, int offset, int length);

  /** Set the log entry metadata. */
  LogEntrysBuilder metadata(DirectBuffer buffer);

  /** Set the log entry metadata. */
  LogEntrysBuilder metadataWriter(BufferWriter writer);

  /** Set the log entry value. */
  LogEntrysBuilder value(DirectBuffer value, int valueOffset, int valueLength);

  /** Set the entry value. */
  LogEntrysBuilder value(DirectBuffer value);

  /** Set the log entry value. */
  LogEntrysBuilder valueWriter(BufferWriter writer);

  /** Add the log entry to the batch. */
  LogEntrysBuilder done();
}
