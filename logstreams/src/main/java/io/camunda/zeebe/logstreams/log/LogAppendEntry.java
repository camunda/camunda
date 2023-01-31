/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.util.Objects;

/** Represents an unmodifiable application record entry to be appended on the log. */
public interface LogAppendEntry {

  /**
   * @return the key of the record
   */
  long key();

  /**
   * @return points to a command which is part of the same batch, which caused that entry
   */
  int sourceIndex();

  /**
   * @return metadata of the record, like ValueType, Intent, RecordType etc.
   */
  RecordMetadata recordMetadata();

  /**
   * @return the actual record value, this method returns a general type but can be casted to the
   *     right record value class if necessary
   */
  UnifiedRecordValue recordValue();

  /**
   * @return the length of the entry, used by writers to determine whether this entry can be written
   */
  default int getLength() {
    return Long.BYTES
        + // key
        Integer.BYTES
        + // source Index
        recordMetadata().getLength()
        + recordValue().getLength();
  }

  /**
   * @return true if the entry was already processed, defaults to false
   */
  default boolean isProcessed() {
    return false;
  }

  /**
   * Creates a default representation of a {@link LogAppendEntry} using default null values for the
   * key and source index.
   *
   * @param recordMetadata the metadata of the entry
   * @param recordValue the value of the entry
   * @throws NullPointerException if either of {@code recordMetadata} or {@code recordValue} is null
   * @return a simple value class implementation of a {@link LogAppendEntry} with the parameters
   */
  static LogAppendEntry of(
      final RecordMetadata recordMetadata, final UnifiedRecordValue recordValue) {
    return new LogAppendEntryImpl(
        LogEntryDescriptor.KEY_NULL_VALUE,
        -1,
        Objects.requireNonNull(recordMetadata, "must specify metadata"),
        Objects.requireNonNull(recordValue, "must specify value"));
  }

  /**
   * Creates a default representation of a {@link LogAppendEntry} using default null values for the
   * source index.
   *
   * @param key the key of the entry
   * @param recordMetadata the metadata of the entry
   * @param recordValue the value of the entry
   * @throws NullPointerException if either of {@code recordMetadata} or {@code recordValue} is null
   * @return a simple value class implementation of a {@link LogAppendEntry} with the parameters
   */
  static LogAppendEntry of(
      final long key, final RecordMetadata recordMetadata, final UnifiedRecordValue recordValue) {
    return new LogAppendEntryImpl(
        key,
        -1,
        Objects.requireNonNull(recordMetadata, "must specify metadata"),
        Objects.requireNonNull(recordValue, "must specify value"));
  }

  /**
   * Creates a new {@link LogAppendEntry} which wraps the given {@link LogAppendEntry} and marks the
   * entry as processed.
   *
   * @param entry the entry which should be written to the log
   * @return a simple value class implementation of a {@link LogAppendEntry} with the parameters
   */
  static LogAppendEntry ofProcessed(final LogAppendEntry entry) {
    return new ProcessedLogAppendEntryImpl(entry);
  }
}
