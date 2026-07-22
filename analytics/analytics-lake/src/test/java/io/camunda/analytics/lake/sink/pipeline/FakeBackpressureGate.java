/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.BackpressureGate;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts pause/resume invocations; asserts nothing itself so tests can check call counts. */
final class FakeBackpressureGate implements BackpressureGate {

  final AtomicInteger pauseCount = new AtomicInteger();
  final AtomicInteger resumeCount = new AtomicInteger();

  @Override
  public void pause() {
    pauseCount.incrementAndGet();
  }

  @Override
  public void resume() {
    resumeCount.incrementAndGet();
  }
}
