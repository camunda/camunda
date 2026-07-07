/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * Extracts the grouping key a value aggregates under. Must be deterministic — the same value always
 * yields the same key. The key type must be value-equal (a {@code record}), since a aggregation
 * buffers its per-key cells in a map.
 *
 * @param <IN> the value type
 * @param <KEY> the grouping key type
 */
@FunctionalInterface
public interface KeySelector<IN, KEY> {

  /** The grouping key for {@code value} — always an owned, storable key. */
  KEY getKey(IN value);

  /**
   * The grouping key for {@code value}, for an immediate map lookup only: the result may be a
   * reusable view valid only until the selector's next call, so it must never be stored. A stateful
   * selector overrides this to probe without allocating the owned key; the default is {@link
   * #getKey}.
   */
  default KEY probeKey(final IN value) {
    return getKey(value);
  }

  /**
   * An owned, storable key equal to {@code key} — called before a probe key is inserted into a map.
   * The default assumes {@link #probeKey} already returned an owned key.
   */
  default KEY ownKey(final KEY key) {
    return key;
  }
}
