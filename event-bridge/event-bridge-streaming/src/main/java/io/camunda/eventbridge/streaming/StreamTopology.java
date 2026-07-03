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
 * <p>Wire it straight into the runtime as its task factory — {@code
 * StreamRuntime.builder().taskFactory(topology::processorFor)} — since {@link #processorFor(int)}
 * returns a {@link StreamProcessor}, which is a {@link Task}. This is the
 * runtime-managed-durability path: the runtime owns offsets and the commit transaction, and each
 * partition gets a fresh partition-local {@link StreamProcessor}. (When a task must own its own
 * durability — its own state backend, offset store and output sink — build an owning {@link Task}
 * instead; see {@link Task#ownsDurability()}.)
 *
 * <p>A stage factory may build any {@link Stage}, including a {@code ProcessorTopology} graph
 * (branch/merge/fan-out) per partition — so this composes with the processor DAG rather than
 * competing with it: this decides <em>per-partition instantiation</em>, the DAG decides
 * <em>wiring</em>. Concrete stage factories (e.g. a fold-then-aggregate {@code ProjectionStage})
 * are supplied by the application.
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
