/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.eventbridge.streaming.Stage;
import io.camunda.eventbridge.streaming.aggregate.MergingRollup;
import io.camunda.eventbridge.streaming.shuffle.Partial;
import java.util.Map;

/**
 * Stage 2's work as a {@link Stage}: route each shuffled {@link Partial} to the {@link
 * MergingRollup} for its {@code aggId}, and share the rollups' flush/checkpoint lifecycle. Wrapping
 * it in a {@link io.camunda.eventbridge.streaming.StreamProcessor} lets Stage 2 reuse the same
 * {@link StreamProcessorTask} runtime seam as Stage 1.
 */
final class MergeStage implements Stage<Partial> {

  private final Map<Integer, MergingRollup<?, ?>> mergers;

  MergeStage(final Map<Integer, MergingRollup<?, ?>> mergers) {
    this.mergers = mergers;
  }

  @Override
  public void process(final Partial partial) {
    final MergingRollup<?, ?> merger = mergers.get(partial.aggId());
    if (merger != null) {
      merger.accept(partial);
    }
  }

  @Override
  public void flush() {
    mergers.values().forEach(MergingRollup::flush);
  }

  @Override
  public void checkpoint() {
    mergers.values().forEach(MergingRollup::checkpoint);
  }
}
