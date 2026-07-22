/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.DescriptorSink;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Records every accepted descriptor. Optionally latches the next {@link #accept} call so a test can
 * observe (and prove) that the flush thread is genuinely blocked inside {@code
 * DescriptorSink.accept} before releasing it — the mechanism behind the release-ordering test.
 */
final class FakeDescriptorSink implements DescriptorSink {

  final List<Descriptor> accepted = Collections.synchronizedList(new ArrayList<>());

  private volatile CountDownLatch enteredSignal;
  private volatile CountDownLatch releaseGate;

  /** Arms the next {@link #accept} call to block until {@link #releaseBlockedAccept} is called. */
  void armBlockingNextAccept() {
    enteredSignal = new CountDownLatch(1);
    releaseGate = new CountDownLatch(1);
  }

  /** Waits until a blocked (armed) {@link #accept} call has actually entered. */
  boolean awaitAcceptEntered(final Duration timeout) throws InterruptedException {
    final CountDownLatch latch = enteredSignal;
    return latch != null && latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Unblocks a previously-armed {@link #accept} call. */
  void releaseBlockedAccept() {
    final CountDownLatch gate = releaseGate;
    if (gate != null) {
      gate.countDown();
    }
  }

  @Override
  public void accept(final Descriptor descriptor) {
    // deliberately does not clear the fields afterward: releaseBlockedAccept() reads the same
    // field, possibly well after this method returns, and must still find the latch it counted
    // down. A later, un-armed accept() sees the same (already-released) gate and passes straight
    // through, since CountDownLatch#await is a no-op once the count has reached zero.
    final CountDownLatch entered = enteredSignal;
    final CountDownLatch gate = releaseGate;
    if (entered != null) {
      entered.countDown();
    }
    if (gate != null) {
      try {
        gate.await();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    accepted.add(descriptor);
  }
}
