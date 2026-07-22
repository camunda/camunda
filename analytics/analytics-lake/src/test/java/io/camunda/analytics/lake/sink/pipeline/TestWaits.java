/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Bounded spin-wait for cross-thread test assertions (the flush thread runs in the background).
 * Awaitility is not a dependency of this module; {@code Thread.sleep} must never be used for
 * synchronization, so this busy-polls with {@link Thread#onSpinWait()} against a deadline instead.
 */
final class TestWaits {

  private TestWaits() {}

  static void awaitTrue(final BooleanSupplier condition, final Duration timeout) {
    final long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() - deadlineNanos > 0) {
        throw new AssertionError("condition not satisfied within " + timeout);
      }
      Thread.onSpinWait();
    }
  }
}
