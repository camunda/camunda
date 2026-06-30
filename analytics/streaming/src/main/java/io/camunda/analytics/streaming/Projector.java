/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

/**
 * The fold step (stage 1): folds one source record into keyed state and emits any derived facts
 * into the {@link Collector}. A projector owns its state and its logic; it does <em>not</em> track
 * source offsets — that is the runtime's concern, advanced once per record around this call.
 *
 * <p>The fold must be a deterministic function of the in-order, per-partition source stream: every
 * replica that applies the same records reaches the same state and emits the same facts.
 *
 * @param <R> the source record type
 * @param <F> the derived fact type
 */
@FunctionalInterface
public interface Projector<R, F> {

  /** Folds {@code record} into state, emitting zero or more facts into {@code out}. */
  void apply(R record, Collector<F> out);

  /**
   * Called once before any {@link #apply}, after persistent state has been restored — open
   * resources or load cached state here.
   */
  default void init() {}

  /** Called once on shutdown — release resources. */
  default void close() {}
}
