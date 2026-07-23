/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.management.ThreadMXBean;
import io.camunda.analytics.lake.sink.ColumnType;
import java.lang.management.ManagementFactory;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards each algebra's hot-side promise: {@link Algebra.Accumulator#add(long)}/{@link
 * Algebra.Accumulator#addDouble(double)} allocates (effectively) nothing at steady state, including
 * through periodic {@link Algebra.Accumulator#drain} calls. Same measuring technique as {@code
 * io.camunda.analytics.lake.sink.batch.SinkBatchAllocationGuardTest}: {@code
 * ThreadMXBean#getThreadAllocatedBytes(long)}.
 */
class AlgebraAllocationGuardTest {

  private static Stream<Algebra> algebras() {
    return Stream.of(
        Algebras.scalarStats(),
        Algebras.expHistogram(3),
        Algebras.doubleScalarStats(),
        Algebras.signedDoubleExpHistogram(3));
  }

  @ParameterizedTest
  @MethodSource("algebras")
  void shouldNotAllocateOnTheHotPathAtSteadyState(final Algebra algebra) {
    // given a pooled accumulator and a no-op row writer (draining is part of the measured loop,
    // exercised every 500 adds, exactly like a real flush thread would periodically drain)
    final Algebra.Accumulator accumulator = algebra.create();
    final NoopRowWriter writer = new NoopRowWriter();
    final Random random = new Random(42);
    final boolean isDouble = algebra.valueType() == ColumnType.DOUBLE;

    final ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    assertThat(threadMxBean.isThreadAllocatedMemorySupported()).isTrue();
    threadMxBean.setThreadAllocatedMemoryEnabled(true);
    final long threadId = Thread.currentThread().threadId();

    // warm up: run the same loop shape once before measuring so the JIT compiles the hot methods
    runAddDrainLoop(accumulator, writer, random, 10_000, isDouble);

    // when running 100k more add()/addDouble() calls (with periodic drains) on the now-warm thread
    final long allocatedBefore = threadMxBean.getThreadAllocatedBytes(threadId);
    runAddDrainLoop(accumulator, writer, random, 100_000, isDouble);
    final long allocatedBytes = threadMxBean.getThreadAllocatedBytes(threadId) - allocatedBefore;

    // then: steady-state folding allocates effectively nothing (same 64 KB JIT-noise epsilon as
    // SinkBatchAllocationGuardTest — not a per-add budget)
    assertThat(allocatedBytes)
        .withFailMessage(
            "algebra %s: 100k add() calls allocated %d bytes on the hot thread, expected < 64 KB",
            algebra.scheme(), allocatedBytes)
        .isLessThan(64L * 1024);
  }

  private static void runAddDrainLoop(
      final Algebra.Accumulator accumulator,
      final Algebra.RowWriter writer,
      final Random random,
      final int iterations,
      final boolean isDouble) {
    for (int i = 0; i < iterations; i++) {
      if (isDouble) {
        // occasionally exercise the non-finite path too -- it must stay allocation-free as well
        accumulator.addDouble(
            i % 997 == 0 ? Double.POSITIVE_INFINITY : random.nextDouble() * 1_000_000);
      } else {
        accumulator.add(Math.abs(random.nextLong() % 1_000_000));
      }
      if (i % 500 == 499) {
        accumulator.drain(writer);
      }
    }
    if (!accumulator.isEmpty()) {
      accumulator.drain(writer);
    }
  }

  /** Discards every written value; only exercised to keep drain() on the measured path. */
  private static final class NoopRowWriter implements Algebra.RowWriter {
    @Override
    public void beginRow() {}

    @Override
    public void writeLong(final int columnIndex, final long value) {}

    @Override
    public void writeDouble(final int columnIndex, final double value) {}

    @Override
    public void writeNull(final int columnIndex) {}

    @Override
    public void endRow() {}
  }
}
