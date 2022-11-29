/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.Objects;
import org.agrona.DirectBuffer;

/**
 * A mutable {@link LogAppendEntry} which is meant to be used purely in tests, offering convenience
 * to quickly create entries out of single value buffers and the likes.
 *
 * <p>TODO: consider eventually getting rid of this.
 */
public final class MutableLogAppendEntry implements LogAppendEntry {
  private long key;
  private int sourceIndex;
  private BufferWriter recordMetadata;
  private BufferWriter recordValue;

  public MutableLogAppendEntry() {
    reset();
  }

  @Override
  public long key() {
    return key;
  }

  @Override
  public int sourceIndex() {
    return sourceIndex;
  }

  @Override
  public BufferWriter recordMetadata() {
    return recordMetadata;
  }

  @Override
  public BufferWriter recordValue() {
    return recordValue;
  }

  public MutableLogAppendEntry key(final long key) {
    this.key = key;
    return this;
  }

  public MutableLogAppendEntry sourceIndex(final int sourceIndex) {
    this.sourceIndex = sourceIndex;
    return this;
  }

  public MutableLogAppendEntry recordMetadata(final DirectBuffer recordMetadata) {
    return recordMetadata(recordMetadata, 0, recordMetadata.capacity());
  }

  public MutableLogAppendEntry recordMetadata(
      final DirectBuffer recordMetadata, final int offset, final int length) {
    return recordMetadata(new DirectBufferWriter().wrap(recordMetadata, offset, length));
  }

  public MutableLogAppendEntry recordMetadata(final BufferWriter recordMetadata) {
    this.recordMetadata = Objects.requireNonNull(recordMetadata, "must specify metadata");
    return this;
  }

  public MutableLogAppendEntry recordValue(final DirectBuffer recordValue) {
    return recordValue(recordValue, 0, recordValue.capacity());
  }

  public MutableLogAppendEntry recordValue(
      final DirectBuffer recordValue, final int offset, final int length) {
    return recordValue(new DirectBufferWriter().wrap(recordValue, offset, length));
  }

  public MutableLogAppendEntry recordValue(final BufferWriter recordValue) {
    this.recordValue = Objects.requireNonNull(recordValue, "must specify a value");
    return this;
  }

  public MutableLogAppendEntry reset() {
    key(LogEntryDescriptor.KEY_NULL_VALUE);
    recordMetadata(new DirectBufferWriter());
    recordValue(new DirectBufferWriter());
    sourceIndex(-1);

    return this;
  }
}
