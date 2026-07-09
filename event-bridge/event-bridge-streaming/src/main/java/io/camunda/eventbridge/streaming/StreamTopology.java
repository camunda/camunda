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
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * A reusable blueprint of stages that instantiates an isolated {@link StreamProcessor} per source
 * partition. Stages are registered as <em>factories</em> ({@code partitionId -> stage}), not
 * instances, so each partition gets its own stage objects and its own state — the single-writer
 * guarantee per partition, with no shared mutable state across partitions.
 *
 * <p>Wire it straight into the runtime as its task factory — {@code
 * StreamRuntime.builder().taskFactory(topology::processorFor)} — since {@link #processorFor(int)}
 * returns a {@link StreamProcessor}, which is a {@link Task}. Each partition gets a fresh
 * partition-local {@link StreamProcessor} that owns its shard through the {@link ShardDurability}
 * the durability factory provisions for it.
 *
 * <p>A stage factory may build any {@link Stage}, including a {@code ProcessorTopology} graph
 * (branch/merge/fan-out) per partition — so this composes with the processor DAG rather than
 * competing with it: this decides <em>per-partition instantiation</em>, the DAG decides
 * <em>wiring</em>. Concrete stage factories (e.g. one building a {@code ProcessorTopology}) are
 * supplied by the application.
 *
 * @param <R> the source record type
 */
public final class StreamTopology<R> {

  private final IntFunction<ShardDurability> durabilityFactory;
  private final List<IntFunction<Stage<R>>> stageFactories = new ArrayList<>();

  /**
   * @param durabilityFactory provisions each partition's {@link ShardDurability}
   */
  public StreamTopology(final IntFunction<ShardDurability> durabilityFactory) {
    this.durabilityFactory = Objects.requireNonNull(durabilityFactory, "durabilityFactory");
  }

  /** Adds any stage factory — the agnostic entry point. */
  public StreamTopology<R> add(final IntFunction<Stage<R>> stageFactory) {
    stageFactories.add(stageFactory);
    return this;
  }

  /** Builds the processor for one partition — fresh, partition-local stages and shard. */
  public StreamProcessor<R> processorFor(final int partitionId) {
    final StreamProcessor<R> processor =
        new StreamProcessor<>(durabilityFactory.apply(partitionId));
    for (final IntFunction<Stage<R>> factory : stageFactories) {
      processor.add(factory.apply(partitionId));
    }
    return processor;
  }
}
