/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** A fully controllable clock: {@link SinkPipeline} never reads wall-clock time directly. */
final class FakeClock implements LongSupplier {

  private final AtomicLong now;

  FakeClock(final long start) {
    now = new AtomicLong(start);
  }

  @Override
  public long getAsLong() {
    return now.get();
  }

  void advance(final long deltaMillis) {
    now.addAndGet(deltaMillis);
  }
}
