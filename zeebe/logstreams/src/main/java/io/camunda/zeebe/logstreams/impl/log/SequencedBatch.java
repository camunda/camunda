/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

public record SequencedBatch(
    long timestamp,
    long firstPosition,
    long sourcePosition,
    List<LogAppendEntry> entries,
    int length)
    implements BufferWriter {

  public SequencedBatch(
      final long timestamp,
      final long firstPosition,
      final long sourcePosition,
      final List<LogAppendEntry> entries) {
    this(
        timestamp,
        firstPosition,
        sourcePosition,
        Objects.requireNonNull(entries, "must specify a list of entries"),
        SequencedBatchSerializer.calculateBatchLength(entries));
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    SequencedBatchSerializer.serializeBatch(buffer, offset, this);
    return length;
  }
}
