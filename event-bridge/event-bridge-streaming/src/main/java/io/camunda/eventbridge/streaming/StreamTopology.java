/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

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
 * topology once, then call {@link #processorFor(int)} for each assigned partition. Concrete stage
 * factories (e.g. a fold-then-aggregate {@code ProjectionStage}) are supplied by the application.
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

  /** Builds the processor for one partition — fresh, partition-local stages. */
  public StreamProcessor<R> processorFor(final int partitionId) {
    final StreamProcessor<R> processor = new StreamProcessor<>();
    for (final IntFunction<Stage<R>> factory : stageFactories) {
      processor.add(factory.apply(partitionId));
    }
    return processor;
  }
}
