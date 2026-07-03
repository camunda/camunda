/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Extracts the grouping key a fact aggregates under. Must be deterministic — the same fact always
 * yields the same key. The key type must be value-equal (a {@code record}), since the {@link
 * PreAggregator} buffers by it in a map.
 *
 * @param <IN> the fact type
 * @param <KEY> the grouping key type
 */
@FunctionalInterface
public interface KeySelector<IN, KEY> {

  /** The grouping key for {@code value}. */
  KEY getKey(IN value);
}
