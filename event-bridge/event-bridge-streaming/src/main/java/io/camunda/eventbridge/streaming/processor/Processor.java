/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

/**
 * A node in a {@link ProcessorTopology}: it consumes {@code In} records, and — via the {@link
 * ProcessorContext} it is handed at {@link #init} — may keep state, {@link ProcessorContext#forward
 * forward} {@code Out} values to its children, and schedule punctuation. A processor is the
 * general, low-level unit of the framework; a processor graph is what expresses
 * branch/merge/fan-out over the source.
 *
 * <p>Single-writer: one instance per source partition, driven on the runtime thread; no
 * synchronization needed.
 *
 * @param <In> the record type this processor consumes
 * @param <Out> the value type this processor emits downstream (use {@link Void} for a terminal
 *     sink)
 */
public interface Processor<In, Out> {

  /** Wires the processor to the runtime. Called once after state is restored, before any record. */
  default void init(final ProcessorContext<Out> context) {}

  /** Processes one record. */
  void process(In record);

  /**
   * Wall-clock freshness tick: emit buffered output so latency stays bounded. Does <em>not</em>
   * make state durable (see {@link #checkpoint()}). Default: no-op.
   */
  default void flush() {}

  /**
   * Commit-interval tick: make this processor's working state durable, inside the task's one commit
   * transaction, so it lands in the same atomic cut as the consumed offset. A processor backed only
   * by in-memory or write-through state may leave this a no-op; a write-back-cached store flushes
   * here. Default: no-op.
   */
  default void checkpoint() {}

  /**
   * Whether this processor holds buffered writes that should be checkpointed before the regular
   * interval — e.g. a bounded write-back cache is full. Bubbles up through the topology so the
   * runtime runs the commit barrier early. Default {@code false}.
   */
  default boolean needsCheckpoint() {
    return false;
  }

  /** Releases resources on shutdown. */
  default void close() {}
}
