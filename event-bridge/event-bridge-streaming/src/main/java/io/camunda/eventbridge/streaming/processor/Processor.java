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
 * general, low-level unit of the framework; simple linear folds can still use the higher-level
 * {@code ProjectionStage}, but a processor graph is what expresses branch/merge/fan-out.
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

  /** Releases resources on shutdown. */
  default void close() {}
}
