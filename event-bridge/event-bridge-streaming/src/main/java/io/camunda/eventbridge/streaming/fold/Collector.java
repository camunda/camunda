/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.fold;

/**
 * The emit channel a {@link Projector} writes derived values into. The runtime owns the collector
 * and decides what happens to collected values (route them to a sink, pre-aggregate them, …); the
 * fold just emits. A single fold step may collect zero, one, or many values.
 *
 * @param <T> the emitted value type
 */
@FunctionalInterface
public interface Collector<T> {

  /** Emits one value downstream. */
  void collect(T value);
}
