/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.SortedRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Records one call per sealed segment; shares a log with the encoder factory to check ordering. */
final class FakeSealRider implements SealRider {

  final AtomicInteger callCount = new AtomicInteger();
  final List<Integer> sealedSizes = Collections.synchronizedList(new ArrayList<>());

  private final List<String> log;

  FakeSealRider(final List<String> log) {
    this.log = log;
  }

  @Override
  public void onSealed(final SortedRun run) {
    callCount.incrementAndGet();
    sealedSizes.add(run.size());
    log.add("rider(size=" + run.size() + ")");
  }
}
