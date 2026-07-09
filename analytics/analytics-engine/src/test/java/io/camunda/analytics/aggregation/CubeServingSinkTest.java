/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.eventbridge.streaming.aggregate.LongRecordValue;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class CubeServingSinkTest {

  @Test
  void shouldWriteThePreSerializedFormWithoutEncodingAgain() {
    // given a sink whose codec counts encodings, and an already-serialized accumulator
    final RecordingWriter writer = new RecordingWriter();
    final CountingCodec codec = new CountingCodec();
    final CubeServingSink sink = new CubeServingSink(writer, null, 60_000L, codec);
    final byte[] serialized = new LongRecordValue().toBytes(7L);

    // when the aggregation hands over the bytes it already produced for its checkpoint
    sink.upsert(new Windowed<>(null, 0L), new Object[] {7L}, serialized);

    // then those bytes pass through untouched and the sink's codec is never asked
    assertThat(writer.accumulators).singleElement().isSameAs(serialized);
    assertThat(codec.serializations).isZero();
  }

  @Test
  void shouldEncodeItselfWhenNoSerializedFormIsHandedOver() {
    // given
    final RecordingWriter writer = new RecordingWriter();
    final CountingCodec codec = new CountingCodec();
    final CubeServingSink sink = new CubeServingSink(writer, null, 60_000L, codec);

    // when the plain upsert runs (e.g. a sink used outside the serialize-once commit path)
    sink.upsert(new Windowed<>(null, 0L), new Object[] {7L});

    // then the sink encodes the value with its own codec
    assertThat(codec.serializations).isEqualTo(1);
    assertThat(writer.accumulators).singleElement().isEqualTo(new LongRecordValue().toBytes(7L));
  }

  private static final class RecordingWriter implements DatasetWriter {
    private final List<byte[]> accumulators = new ArrayList<>();

    @Override
    public void upsertCell(
        final CompiledDataset dataset,
        final DimensionKey key,
        final long windowStart,
        final long windowSize,
        final byte[] compositeAccumulator) {
      accumulators.add(compositeAccumulator);
    }

    @Override
    public void upsertRow(
        final CompiledTable table, final String rowKey, final List<Object> values) {}

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }

  /** A single-long-slot composite codec that counts encodings. */
  private static final class CountingCodec implements RecordValue<Object[]> {
    private final LongRecordValue delegate = new LongRecordValue();
    private int serializations;
    private Object[] value;

    @Override
    public byte[] toBytes(final Object[] composite) {
      serializations++;
      return delegate.toBytes((Long) composite[0]);
    }

    @Override
    public RecordValue<Object[]> wrapValue(final Object[] composite) {
      value = composite;
      delegate.wrapValue((Long) composite[0]);
      return this;
    }

    @Override
    public Object[] value() {
      return value;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      delegate.wrap(buffer, offset, length);
      value = new Object[] {delegate.value()};
    }

    @Override
    public int getLength() {
      return delegate.getLength();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return delegate.write(buffer, offset);
    }
  }
}
