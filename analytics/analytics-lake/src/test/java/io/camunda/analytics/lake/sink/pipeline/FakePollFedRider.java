/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.SortedRun;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A minimal stand-in for {@code io.camunda.analytics.lake.metrics.PollFedRider}: {@link #fold()}
 * increments a poll-thread-only counter instead of folding a real record; {@link #onPollBoundary}/
 * {@link #rollbackPollBoundary} swap it exactly the way the real rider swaps its {@code
 * ActiveWindow}. Used to prove {@link SinkPipeline}/{@link FlushLoop}'s own hook wiring (poll
 * thread: swap before seal; flush thread: drain the frozen queue) without pulling in the metrics
 * package's declaration/algebra machinery.
 */
final class FakePollFedRider implements SealRider {

  private int active;
  private final Deque<Integer> frozen = new ArrayDeque<>();

  /** Every value {@link #onWindowClose()} drained, in order — flush thread only. */
  final List<Integer> drained = new CopyOnWriteArrayList<>();

  /** Poll thread only: simulates folding one record into the currently-active window. */
  void fold() {
    active++;
  }

  @Override
  public void onSealed(final SortedRun run) {}

  @Override
  public void onPollBoundary() {
    frozen.addLast(active);
    active = 0;
  }

  @Override
  public void rollbackPollBoundary() {
    active = frozen.removeLast();
  }

  @Override
  public boolean hasPendingPollFedData() {
    for (final Integer count : frozen) {
      if (count > 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Map<String, List<DataFileResult>> onWindowClose() {
    final Integer count = frozen.pollFirst();
    drained.add(count == null ? 0 : count);
    return Map.of();
  }

  @Override
  public void abortWindow() {
    frozen.clear();
  }

  /** Snapshot of {@link #drained} safe to assert on without racing the flush thread. */
  List<Integer> drainedSoFar() {
    return new ArrayList<>(drained);
  }
}
