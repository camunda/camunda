/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.processor;

import java.time.Duration;

/**
 * The handle a {@link Processor} is given at {@link Processor#init init} time to interact with the
 * runtime: emit downstream, reach its state stores, and schedule periodic work. It is the seam that
 * lets a processor be a node in a graph without knowing what is wired around it.
 *
 * @param <Out> the type this processor emits downstream
 */
public interface ProcessorContext<Out> {

  /** Emits a value to every child of this processor. */
  void forward(Out value);

  /**
   * Emits a value to a single named child — the basis for branching, where a processor routes each
   * record to one of several downstream paths.
   *
   * @throws IllegalArgumentException if no child with that name is wired to this processor
   */
  void forward(Out value, String childName);

  /**
   * Registers a periodic {@link Punctuator} on the given clock. Punctuators run on the runtime
   * thread between records, so they may touch the same state and forward downstream. The interval
   * is a lower bound honoured at the runtime's pump cadence, not a hard timer.
   */
  void schedule(Duration interval, PunctuationType type, Punctuator punctuator);

  /**
   * Returns a state store wired to this processor by name (see {@code
   * ProcessorTopology.Builder.addStateStore}). The caller supplies the store's static type; a
   * mismatch surfaces as a {@link ClassCastException} at the call site.
   *
   * @throws IllegalArgumentException if no store with that name is connected to this processor
   */
  <S> S getStateStore(String name);
}
