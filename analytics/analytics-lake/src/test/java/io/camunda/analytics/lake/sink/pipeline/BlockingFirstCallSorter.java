/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A sorter function whose first invocation blocks the calling (flush) thread until the test
 * releases it — used to stall {@link FlushLoop} right after it takes a sealed segment off the ring,
 * so a test can observe genuine ring-full backpressure deterministically. Every later invocation
 * passes straight through.
 */
final class BlockingFirstCallSorter implements Function<Segment, SortedRun> {

  private final long epochDay;
  private final AtomicBoolean first = new AtomicBoolean(true);
  private final CountDownLatch enteredSignal = new CountDownLatch(1);
  private final CountDownLatch releaseGate = new CountDownLatch(1);

  BlockingFirstCallSorter(final long epochDay) {
    this.epochDay = epochDay;
  }

  @Override
  public SortedRun apply(final Segment segment) {
    if (first.compareAndSet(true, false)) {
      enteredSignal.countDown();
      try {
        releaseGate.await();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return new IdentitySortedRun(segment, epochDay);
  }

  boolean awaitEntered(final Duration timeout) throws InterruptedException {
    return enteredSignal.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  void release() {
    releaseGate.countDown();
  }
}
