/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

import io.camunda.analytics.streaming.aggregate.Rollup;
import io.camunda.analytics.streaming.fold.Projector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A reusable blueprint of stages that instantiates an isolated {@link StreamProcessor} per source
 * partition. Stages are registered as <em>factories</em> ({@code partitionId -> stage}), not
 * instances, so each partition gets its own stage objects and its own state — the single-writer
 * guarantee per partition, with no shared mutable state across partitions.
 *
 * <p>This is the analogue of a topology that the runtime materializes once per task: build the
 * topology once, then call {@link #processorFor(int)} for each assigned partition.
 *
 * @param <R> the source record type
 */
public final class StreamTopology<R> {

  private final List<IntFunction<Stage<R>>> stageFactories = new ArrayList<>();

  /** Adds any stage factory — the agnostic entry point. */
  public StreamTopology<R> add(final IntFunction<Stage<R>> stageFactory) {
    stageFactories.add(stageFactory);
    return this;
  }

  /**
   * Sugar: a fold-then-aggregate stage built per partition — the projector and each rollup are
   * created fresh for the partition, so their state (state store, combiner, serving store) is
   * partition-local.
   */
  public <F> StreamTopology<R> register(
      final IntFunction<Projector<R, F>> projectorFactory,
      final List<IntFunction<Rollup<F>>> rollupFactories) {
    return add(
        partitionId -> {
          final List<Rollup<F>> rollups = new ArrayList<>(rollupFactories.size());
          for (final IntFunction<Rollup<F>> factory : rollupFactories) {
            rollups.add(factory.apply(partitionId));
          }
          return new ProjectionStage<>(projectorFactory.apply(partitionId), rollups);
        });
  }

  /** Sugar: a fold-then-aggregate stage with a single rollup. */
  public <F> StreamTopology<R> register(
      final IntFunction<Projector<R, F>> projectorFactory,
      final IntFunction<Rollup<F>> rollupFactory) {
    return register(projectorFactory, List.of(rollupFactory));
  }

  /** Builds the processor for one partition — fresh, partition-local stages. */
  public StreamProcessor<R> processorFor(final int partitionId) {
    final StreamProcessor<R> processor = new StreamProcessor<>();
    for (final IntFunction<Stage<R>> factory : stageFactories) {
      processor.add(factory.apply(partitionId));
    }
    return processor;
  }
}
