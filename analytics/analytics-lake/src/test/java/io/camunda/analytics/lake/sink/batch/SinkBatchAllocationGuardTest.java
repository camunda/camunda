/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.ThreadMXBean;
import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the hot path's core promise: appending rows at steady state allocates (effectively)
 * nothing on the poll thread. Uses {@link ThreadMXBean#getThreadAllocatedBytes(long)}, which
 * reports bytes allocated by a specific thread — precise enough to catch a regression that
 * reintroduces per-row allocation (e.g. autoboxing, a new array, a fresh interner entry).
 */
class SinkBatchAllocationGuardTest {

  private static TableSchema schema() {
    return new TableSchema(
        "allocation_guard",
        List.of(
            new TableSchema.Column("ts", ColumnType.LONG, 1, false, -1, true),
            new TableSchema.Column("entity_id", ColumnType.LONG, 2, false, 0, false),
            new TableSchema.Column("kind", ColumnType.STRING_DICT, 3, false, 1, false),
            new TableSchema.Column("count", ColumnType.INT, 4, true, -1, false),
            new TableSchema.Column("payload", ColumnType.BINARY, 5, true, -1, false)));
  }

  @Test
  void shouldNotAllocateOnTheAppendingThreadAtSteadyState() {
    // given a real ring + appender + interner, with the interner already warmed with every
    // distinct dict value this test will ever use (interning a NEW value is the one allocating
    // path in the hot loop, and it must never happen after warmup)
    final Interner interner = new Interner();
    final int rowCapacity = 256;
    final Segment[] segments =
        SegmentFactory.createSegments(
            schema(), 4, rowCapacity, new int[] {0, 0, 0, 0, 32}, interner);
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segments, new NoopGate());
    final RowAppender appender = new SegmentRowAppender(ring);
    final byte[] payload = "payload-bytes".getBytes(StandardCharsets.UTF_8);

    final ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    assertThat(threadMxBean.isThreadAllocatedMemorySupported()).isTrue();
    threadMxBean.setThreadAllocatedMemoryEnabled(true);
    final long threadId = Thread.currentThread().threadId();

    // warm up: 10k rows first, on the calling thread, draining the ring as we go so the appender
    // never actually blocks — this both interns every distinct dict value used below and gives the
    // JIT a chance to compile the hot methods before measurement starts
    appendRows(appender, ring, 10_000, payload);

    // when appending 100k more rows on the now-warm thread
    final long allocatedBefore = threadMxBean.getThreadAllocatedBytes(threadId);
    appendRows(appender, ring, 100_000, payload);
    final long allocatedBytes = threadMxBean.getThreadAllocatedBytes(threadId) - allocatedBefore;

    // then: steady-state appends allocate effectively nothing. The 64 KB epsilon is NOT a
    // per-row allocation budget — it exists solely to absorb JIT artifacts (e.g. a late
    // recompilation or deoptimization triggered during the measured window) and any residual GC/JFR
    // bookkeeping noise that can occur even well after warmup; 64 KB over 100k rows is ~0.64
    // bytes/row, several orders of magnitude below any real per-row allocation (a single boxed Long
    // alone is 16 bytes).
    assertThat(allocatedBytes)
        .withFailMessage(
            "appending 100k rows allocated %d bytes on the poll thread, expected < 64 KB (JIT-noise"
                + " epsilon, not a per-row budget)",
            allocatedBytes)
        .isLessThan(64L * 1024);
  }

  /**
   * Alternates both nullable columns' two code paths every row — a non-null {@code putInt}/null
   * {@code putNull} for {@code count}, and a non-null {@code putBinary}/null {@code putNull} for
   * {@code payload} (which exercises {@code HeapBinaryColumn#setNull}'s own offset-stamping path,
   * not just its non-null append path) — so every branch is covered inside the same steady-state
   * allocation measurement, not just the null/non-null branch each column happened to take before.
   */
  private static void appendRows(
      final RowAppender appender,
      final ColumnarSegmentRing ring,
      final int rows,
      final byte[] payload) {
    for (int i = 0; i < rows; i++) {
      while (!appender.begin()) {
        drain(ring); // stands in for the flush thread so the poll thread is never truly blocked
      }
      appender.putLong(0, i);
      appender.putLong(1, i);
      appender.putDict(2, i % 2 == 0 ? "kind-a" : "kind-b");
      if (i % 2 == 0) {
        appender.putInt(3, i);
      } else {
        appender.putNull(3);
      }
      if (i % 3 == 0) {
        appender.putNull(4);
      } else {
        appender.putBinary(4, payload, 0, payload.length);
      }
      appender.endRow();
    }
    drain(ring);
  }

  private static void drain(final ColumnarSegmentRing ring) {
    Segment sealed;
    while ((sealed = ring.take()) != null) {
      ring.release(sealed);
    }
  }

  private static final class NoopGate implements BackpressureGate {
    @Override
    public void pause() {}

    @Override
    public void resume() {}
  }
}
