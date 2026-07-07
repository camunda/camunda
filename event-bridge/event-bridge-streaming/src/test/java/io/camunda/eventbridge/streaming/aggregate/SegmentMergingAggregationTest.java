/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class SegmentMergingAggregationTest {

  private static final AggregateFunction<Object, Long, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Long createAccumulator() {
          return 0L;
        }

        @Override
        public Long add(final Object item, final Long acc) {
          return acc;
        }

        @Override
        public Long merge(final Long a, final Long b) {
          return a + b;
        }

        @Override
        public Long getResult(final Long acc) {
          return acc;
        }
      };

  private static SegmentMergingAggregation<String, Long> merger(
      final KeyValueStore<DbBytes, DbBytes> store,
      final InMemoryResultSink<Windowed<String>, Long> sink,
      final Windows windows) {
    return new SegmentMergingAggregation<>(
        1,
        SUM,
        windows,
        sink,
        store,
        new StringRecordValue(),
        new LongRecordValue(),
        Runnable::run);
  }

  @Test
  void shouldFoldDeltasIntoARunningTotal() {
    // given
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()), sink, keepOpen());
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when two segment deltas for the cell are merged
    merger.merge(cell, 3L);
    merger.merge(cell, 4L);
    merger.flush();

    // then the served value is their sum (deltas ADD into one running total, unlike per-writer
    // slots)
    assertThat(sink.get(cell)).hasValue(7L);

    // when a third delta arrives
    merger.merge(cell, 10L);
    merger.flush();

    // then it folds in — 7 + 10
    assertThat(sink.get(cell)).hasValue(17L);
  }

  @Test
  void shouldFinalizeClosedWindowsAndDropLateDeltas() {
    // given a no-grace window so it closes as soon as event time passes its end
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            sink,
            TumblingWindows.of(1_000L));
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when the window's delta is merged and a checkpoint runs (event time reached the window end)
    merger.merge(cell, 5L);
    merger.checkpoint();

    // then the window is finalized to its value
    assertThat(sink.get(cell)).hasValue(5L);

    // and a late delta for the now-evicted window is dropped, not resurrected
    merger.merge(cell, 100L);
    merger.flush();
    assertThat(sink.get(cell)).hasValue(5L);
  }

  @Test
  void shouldRecoverRunningTotalsFromTheStore() {
    // given a running total checkpointed to a shared store
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final Windowed<String> cell = new Windowed<>("k", 0L);
    final SegmentMergingAggregation<String, Long> before =
        merger(store, new InMemoryResultSink<>(), keepOpen());
    before.merge(cell, 6L);
    before.checkpoint();

    // when a fresh operator recovers from the same store (a restart)
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> after = merger(store, sink, keepOpen());
    after.merge(cell, 4L);
    after.flush();

    // then it continued from the recovered total — 6 + 4
    assertThat(sink.get(cell)).hasValue(10L);
  }

  @Test
  void shouldNotAliasACallerOwnedDeltaAsTheRunningTotal() {
    // given an aggregate with a mutable accumulator and an in-place mergeInto
    final InMemoryResultSink<Windowed<String>, long[]> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, long[]> merger =
        new SegmentMergingAggregation<>(
            1,
            MUTABLE_SUM,
            keepOpen(),
            sink,
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            new StringRecordValue(),
            new LongArrayValue(),
            Runnable::run);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    final long[] delta = {3L};

    // when the first delta is merged and the caller then reuses its own instance
    merger.merge(cell, delta);
    delta[0] = 100L;
    merger.merge(cell, new long[] {4L});
    merger.flush();

    // then the running total owns its state — the caller's later mutation did not leak in
    assertThat(sink.get(cell)).hasValueSatisfying(total -> assertThat(total[0]).isEqualTo(7L));
  }

  @Test
  void shouldSerializeAChangedCellOnceAcrossFlushAndCheckpoint() {
    // given a codec that counts serializations and a sink that receives the serialized form
    final CountingLongValue codec = new CountingLongValue();
    final Map<Windowed<String>, byte[]> served = new HashMap<>();
    final ResultSink<Windowed<String>, Long> sink =
        new ResultSink<>() {
          @Override
          public void upsert(final Windowed<String> key, final Long value) {
            fail("the aggregation should always hand the sink the serialized form");
          }

          @Override
          public void upsert(final Windowed<String> key, final Long value, final byte[] bytes) {
            served.put(key, bytes);
          }
        };
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            1, SUM, keepOpen(), sink, store, new StringRecordValue(), codec, Runnable::run);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 3L);
    merger.merge(cell, 4L);

    // when one commit runs (flush, then checkpoint — the runtime's produce-before-commit order)
    merger.flush();
    merger.checkpoint();

    // then the total was serialized exactly once, and the durable cell holds those same bytes
    assertThat(codec.serializations).isEqualTo(1);
    assertThat(codec.fromBytes(served.get(cell))).isEqualTo(7L);
    final List<byte[]> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(value.getBytes().clone()));
    assertThat(stored).singleElement().isEqualTo(served.get(cell));
  }

  /** A window wide enough with grace that it stays open across the test. */
  private static Windows keepOpen() {
    return TumblingWindows.ofSizeAndGrace(1_000L, 5_000L);
  }

  /** A sum over a mutable accumulator, with the in-place {@code mergeInto} the sketches use. */
  private static final AggregateFunction<Object, long[], Long> MUTABLE_SUM =
      new AggregateFunction<>() {
        @Override
        public long[] createAccumulator() {
          return new long[1];
        }

        @Override
        public long[] add(final Object item, final long[] acc) {
          return acc;
        }

        @Override
        public long[] merge(final long[] a, final long[] b) {
          return new long[] {a[0] + b[0]};
        }

        @Override
        public long[] mergeInto(final long[] target, final long[] delta) {
          target[0] += delta[0];
          return target;
        }

        @Override
        public Long getResult(final long[] acc) {
          return acc[0];
        }
      };

  /** Serializes a {@code long[1]} accumulator through {@link DbLong}. */
  private static final class LongArrayValue implements RecordValue<long[]> {
    private final DbLong delegate = new DbLong();

    @Override
    public RecordValue<long[]> wrapValue(final long[] value) {
      delegate.wrapLong(value[0]);
      return this;
    }

    @Override
    public long[] value() {
      return new long[] {delegate.getValue()};
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      delegate.wrap(buffer, offset, length);
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

  /** A {@link LongRecordValue}-shaped codec that counts {@link #toBytes} calls. */
  private static final class CountingLongValue implements RecordValue<Long> {
    private final LongRecordValue delegate = new LongRecordValue();
    private int serializations;

    @Override
    public RecordValue<Long> wrapValue(final Long value) {
      delegate.wrapValue(value);
      return this;
    }

    @Override
    public Long value() {
      return delegate.value();
    }

    @Override
    public byte[] toBytes(final Long value) {
      serializations++;
      return RecordValue.super.toBytes(value);
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      delegate.wrap(buffer, offset, length);
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
