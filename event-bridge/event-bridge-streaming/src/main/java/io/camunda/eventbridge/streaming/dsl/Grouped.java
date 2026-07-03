/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.dsl;

import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import io.camunda.eventbridge.streaming.window.Windows;
import java.util.function.ToLongFunction;

/**
 * Values grouped by a key, awaiting a windowing. The single materialization model is windowed, so a
 * group is scoped into event-time windows with {@link #windowedBy} before it can be aggregated.
 *
 * @param <F> the value type
 * @param <K> the grouping key type
 */
public final class Grouped<F, K> {

  private final KeySelector<F, K> keySelector;

  Grouped(final KeySelector<F, K> keySelector) {
    this.keySelector = keySelector;
  }

  /** Scopes the group into event-time windows by the value's event time. */
  public WindowedGrouped<F, K> windowedBy(
      final Windows windows, final ToLongFunction<F> eventTime) {
    return new WindowedGrouped<>(keySelector, windows, eventTime);
  }
}
