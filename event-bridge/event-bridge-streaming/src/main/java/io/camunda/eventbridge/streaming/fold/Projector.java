/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.fold;

/**
 * The fold step (stage 1): folds one source record into keyed state and emits any derived values
 * into the {@link Collector}. A projector owns its state and its logic; it does <em>not</em> track
 * source offsets — that is the runtime's concern, advanced once per record around this call.
 *
 * <p>The fold must be a deterministic function of the in-order, per-partition source stream: every
 * replica that applies the same records reaches the same state and emits the same values.
 *
 * @param <R> the source record type
 * @param <F> the derived value type
 */
@FunctionalInterface
public interface Projector<R, F> {

  /** Folds {@code record} into state, emitting zero or more values into {@code out}. */
  void apply(R record, Collector<F> out);

  /**
   * Called once before any {@link #apply}, after persistent state has been restored — open
   * resources or load cached state here.
   */
  default void init() {}

  /**
   * Make the projector's durable fold state persistent. Called at the commit interval, inside the
   * runtime's checkpoint transaction, so the fold state commits atomically with the rollups and the
   * consumed offset. A projector backed only by in-memory or write-through state may leave this a
   * no-op; a write-back-cached store flushes here.
   */
  default void checkpoint() {}

  /**
   * Whether the projector's state should be checkpointed before the regular interval — e.g. its
   * bounded write-back cache is full. Bubbles up so the runtime runs the commit barrier early.
   * Default {@code false}.
   */
  default boolean needsCheckpoint() {
    return false;
  }

  /** Called once on shutdown — release resources. */
  default void close() {}
}
