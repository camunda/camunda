/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

import io.camunda.eventbridge.streaming.Stage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A graph of {@link Processor}s driven as one {@link Stage}: records enter at the source and flow
 * along the wired edges, each processor {@link ProcessorContext#forward forwarding} to its
 * children. This is the general topology model — branch, merge, fan-out — in contrast to the fixed
 * fold-then-rollup of {@code ProjectionStage}.
 *
 * <p>The topology also owns punctuation: processors register {@link Punctuator}s via {@link
 * ProcessorContext#schedule}, and the runtime's two punctuation ticks drive them — {@link
 * #punctuateWallClock(long)} runs the {@link PunctuationType#WALL_CLOCK_TIME} punctuators (bounded
 * latency, idle-timeout work) and {@link #advanceStreamTime(long)} the {@link
 * PunctuationType#STREAM_TIME} ones (window finalization, retention). State stores are added by
 * name and connected to the processors allowed to reach them; their durability is the runtime's
 * concern (the driver's checkpoint), not the topology's.
 *
 * <p>Single-writer: one topology per source partition, driven on the runtime thread.
 *
 * @param <R> the source record type entering at the topology's source
 */
public final class ProcessorTopology<R> implements Stage<R> {

  private final ProcessorNode<R, ?> source;
  private final List<ProcessorNode<?, ?>> nodes;
  private final Map<String, Object> stores;
  private final Map<String, Set<String>> storesByProcessor;
  private final List<ScheduledPunctuator> punctuators = new ArrayList<>();

  private ProcessorTopology(final Builder<R> builder) {
    source = builder.source();
    nodes = List.copyOf(builder.nodes.values());
    stores = Map.copyOf(builder.stores);
    storesByProcessor = Map.copyOf(builder.storesByProcessor);
  }

  public static <R> Builder<R> builder() {
    return new Builder<>();
  }

  @Override
  public void init() {
    for (final ProcessorNode<?, ?> node : nodes) {
      initNode(node);
    }
  }

  private <In, Out> void initNode(final ProcessorNode<In, Out> node) {
    node.init(contextFor(node));
  }

  @Override
  public void process(final R record) {
    source.deliver(record);
  }

  /** Wall-clock punctuation tick — the runtime supplies the current wall-clock time. */
  @Override
  public void punctuateWallClock(final long wallClockMs) {
    fire(PunctuationType.WALL_CLOCK_TIME, wallClockMs);
  }

  /** Event-time punctuation tick. */
  @Override
  public void advanceStreamTime(final long streamTimeMs) {
    fire(PunctuationType.STREAM_TIME, streamTimeMs);
  }

  @Override
  public void close() {
    nodes.forEach(ProcessorNode::close);
  }

  private void fire(final PunctuationType type, final long nowMs) {
    for (final ScheduledPunctuator scheduled : punctuators) {
      if (scheduled.type == type) {
        scheduled.maybeFire(nowMs);
      }
    }
  }

  private <In, Out> ProcessorContext<Out> contextFor(final ProcessorNode<In, Out> node) {
    final Set<String> reachable = storesByProcessor.getOrDefault(node.name(), Set.of());
    return new ProcessorContext<>() {
      @Override
      public void forward(final Out value) {
        node.forward(value);
      }

      @Override
      public void forward(final Out value, final String childName) {
        node.forward(value, childName);
      }

      @Override
      public void schedule(
          final Duration interval, final PunctuationType type, final Punctuator punctuator) {
        if (interval.isZero() || interval.isNegative()) {
          throw new IllegalArgumentException("punctuation interval must be positive");
        }
        punctuators.add(new ScheduledPunctuator(interval.toMillis(), type, punctuator));
      }

      @Override
      @SuppressWarnings("unchecked")
      public <S> S getStateStore(final String name) {
        if (!reachable.contains(name)) {
          throw new IllegalArgumentException(
              "Processor '" + node.name() + "' is not connected to a store named '" + name + "'");
        }
        return (S) stores.get(name);
      }
    };
  }

  /** A registered punctuator plus the bookkeeping to honour its interval on a monotone clock. */
  private static final class ScheduledPunctuator {

    private final long intervalMs;
    private final PunctuationType type;
    private final Punctuator punctuator;
    private boolean primed;
    private long lastFireMs;

    ScheduledPunctuator(
        final long intervalMs, final PunctuationType type, final Punctuator punctuator) {
      this.intervalMs = intervalMs;
      this.type = type;
      this.punctuator = punctuator;
    }

    void maybeFire(final long nowMs) {
      if (!primed) {
        // Anchor to the first observed time so the first fire is a full interval later, not
        // immediately (stream-time is unknown until the first record arrives).
        primed = true;
        lastFireMs = nowMs;
        return;
      }
      if (nowMs - lastFireMs >= intervalMs) {
        punctuator.punctuate(nowMs);
        lastFireMs = nowMs;
      }
    }
  }

  /**
   * Wires a topology: a single {@code source}, {@code processor}s connected to named parents, and
   * {@code stateStore}s connected to the processors allowed to reach them. Names must be unique and
   * every referenced parent/processor must already be declared.
   *
   * @param <R> the source record type
   */
  public static final class Builder<R> {

    private final Map<String, ProcessorNode<?, ?>> nodes = new LinkedHashMap<>();
    private final Map<String, Object> stores = new HashMap<>();
    private final Map<String, Set<String>> storesByProcessor = new HashMap<>();
    private String sourceName;

    private Builder() {}

    /** Declares the single entry point that receives every source record. */
    public Builder<R> source(final String name, final Processor<R, ?> processor) {
      if (sourceName != null) {
        throw new IllegalStateException("source already set to '" + sourceName + "'");
      }
      addNode(name, processor);
      sourceName = name;
      return this;
    }

    /** Declares a processor and connects it downstream of one or more existing parents. */
    public Builder<R> processor(
        final String name, final Processor<?, ?> processor, final String... parents) {
      if (parents.length == 0) {
        throw new IllegalArgumentException("processor '" + name + "' needs at least one parent");
      }
      final ProcessorNode<?, ?> child = addNode(name, processor);
      for (final String parent : parents) {
        connect(requireNode(parent), child);
      }
      return this;
    }

    /**
     * Adds a state store and grants the named processors access to it via {@code getStateStore}.
     */
    public Builder<R> addStateStore(
        final String name, final Object store, final String... processors) {
      if (stores.putIfAbsent(name, store) != null) {
        throw new IllegalArgumentException("store '" + name + "' already added");
      }
      for (final String processor : processors) {
        requireNode(processor);
        storesByProcessor.computeIfAbsent(processor, key -> new HashSet<>()).add(name);
      }
      return this;
    }

    public ProcessorTopology<R> build() {
      if (sourceName == null) {
        throw new IllegalStateException("no source declared");
      }
      return new ProcessorTopology<>(this);
    }

    @SuppressWarnings("unchecked")
    private ProcessorNode<R, ?> source() {
      return (ProcessorNode<R, ?>) nodes.get(sourceName);
    }

    private ProcessorNode<?, ?> addNode(final String name, final Processor<?, ?> processor) {
      if (nodes.containsKey(name)) {
        throw new IllegalArgumentException("node '" + name + "' already declared");
      }
      final ProcessorNode<?, ?> node = new ProcessorNode<>(name, processor);
      nodes.put(name, node);
      return node;
    }

    private ProcessorNode<?, ?> requireNode(final String name) {
      final ProcessorNode<?, ?> node = nodes.get(name);
      if (node == null) {
        throw new IllegalArgumentException("unknown node '" + name + "'");
      }
      return node;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void connect(final ProcessorNode parent, final ProcessorNode child) {
      parent.addChild(child);
    }
  }
}
