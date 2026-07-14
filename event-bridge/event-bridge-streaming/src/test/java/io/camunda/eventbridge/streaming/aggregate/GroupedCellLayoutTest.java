/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.Test;

/**
 * Golden wire-format test for the durable grouped-cell layout the segment aggregations persist:
 * cell rows keyed {@code group(int) ++ windowStart(long) ++ keyCodec(key)} (big-endian framing) and
 * the sealing aggregation's meta row keyed by the bare 4-byte {@code group} with a {@code
 * openSegment(long) ++ sourcePartition(int)} value.
 *
 * <p>The layout is <b>durable identity</b>: deployed state was written under exactly these bytes,
 * so recovery depends on them bit-for-bit. The key codec's own bytes are pinned by the golden tests
 * of the concrete codecs (e.g. the dimension-key codec in analytics-model); this test pins the
 * group/windowStart framing around them, which has no other coverage. If it fails, the durable
 * layout changed — that orphans every persisted cell, so fix the code, never the expected bytes.
 */
final class GroupedCellLayoutTest {

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

  @Test
  void shouldPersistMergedCellsUnderTheGoldenKeyLayout() {
    // given a merging aggregation on group 42 and a grace wide enough to keep the window open
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            42,
            SUM,
            TumblingWindows.ofSizeAndGrace(1_000L, 5_000L),
            new InMemoryResultSink<>(),
            store,
            new StringRecordValue(),
            new LongRecordValue(),
            Runnable::run);

    // when a delta for windowStart 3_600_000 is merged and checkpointed
    merger.merge(new Windowed<>("order", 3_600_000L), 7L);
    merger.checkpoint();

    // then the one durable cell is keyed group(42) ++ windowStart(3_600_000) ++ codec("order"),
    // big-endian — the framing bytes are durable identity and must never change
    final List<byte[]> keys = new ArrayList<>();
    store.forEach((key, value) -> keys.add(key.getBytes().clone()));
    final byte[] framing = {0, 0, 0, 42, 0, 0, 0, 0, 0, 0x36, (byte) 0xEE, (byte) 0x80};
    assertThat(keys)
        .singleElement()
        .isEqualTo(concat(framing, new StringRecordValue().toBytes("order")));
  }

  @Test
  void shouldPersistOpenSegmentCellsAndMetaUnderTheGoldenLayout() {
    // given a durable (Model-F) sealing aggregation on group 7 with segment stride 10
    record Ev(int partition, long position, long eventTime, String key, long value) {}
    final SourceCoordinate<Ev> coordinate =
        new SourceCoordinate<>() {
          @Override
          public int partition(final Ev value) {
            return value.partition();
          }

          @Override
          public long position(final Ev value) {
            return value.position();
          }
        };
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentSealingAggregation<Ev, String, MutableLong> aggregation =
        new SegmentSealingAggregation<>(
            7,
            new SumAggregateFunction<>(Ev::value),
            Ev::key,
            coordinate,
            Ev::eventTime,
            TumblingWindows.of(1_000_000L),
            Segments.ofStride(10L),
            (cell, partition, segment, delta) -> {},
            store,
            new StringRecordValue(),
            new MutableLongRecordValue(),
            Runnable::run);

    // when a record from source partition 3 lands in the open segment 0 and is checkpointed
    aggregation.accept(new Ev(3, 5L, 100L, "a", 4L));
    aggregation.checkpoint();

    // then the open cell is keyed group(7) ++ windowStart(0) ++ codec("a"), and the meta row is
    // keyed by the bare 4-byte group with an openSegment(0L) ++ sourcePartition(3) value — the
    // shorter meta key is what keeps it collision-free in the shared store, so it too is pinned
    final Map<List<Byte>, byte[]> rows = new LinkedHashMap<>();
    store.forEach((key, value) -> rows.put(boxed(key.getBytes()), value.getBytes().clone()));
    final byte[] metaKey = {0, 0, 0, 7};
    final byte[] cellFraming = {0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0};
    final byte[] cellKey = concat(cellFraming, new StringRecordValue().toBytes("a"));
    assertThat(rows.keySet()).containsExactlyInAnyOrder(boxed(metaKey), boxed(cellKey));
    assertThat(rows.get(boxed(metaKey))).isEqualTo(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3});
  }

  private static byte[] concat(final byte[] head, final byte[] tail) {
    final byte[] out = new byte[head.length + tail.length];
    System.arraycopy(head, 0, out, 0, head.length);
    System.arraycopy(tail, 0, out, head.length, tail.length);
    return out;
  }

  private static List<Byte> boxed(final byte[] bytes) {
    final List<Byte> out = new ArrayList<>(bytes.length);
    for (final byte b : bytes) {
      out.add(b);
    }
    return out;
  }
}
