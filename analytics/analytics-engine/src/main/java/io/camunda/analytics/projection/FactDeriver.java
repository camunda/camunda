/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.zeebe.protocol.record.ValueType;

/**
 * Derives the {@link Fact}s for one source {@link ValueType}: the per-record unit the {@link
 * AnalyticsFactProjector} routes to. A deriver folds the record into the shared read-model ({@link
 * BaseProjectionStore} — the only mutable state) and emits zero or more facts onto {@code out}.
 * Intent-level routing within a value type is the deriver's own concern.
 *
 * <p>Registering one deriver per value type turns the projector's dispatch into a table of
 * capabilities: what the engine folds is read off the registry, not off a chain of {@code if
 * (valueType == … && intent == …)} branches.
 */
@FunctionalInterface
interface FactDeriver {

  /** Folds {@code source} into {@code state} and emits any resulting facts onto {@code out}. */
  void derive(SourceRecord source, BaseProjectionStore state, Collector<Fact> out);
}
