/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.dsl;

import io.camunda.eventbridge.streaming.aggregate.KeySelector;

/**
 * Entry point for the fluent aggregation builder — sugar over the core primitives that reads like a
 * grouped-stream aggregation: {@code Aggregations.groupBy(key).windowedBy(window,
 * time).aggregate(metric).into(store)} builds a {@code Aggregation}. Register the resulting
 * rollup(s) with a projector to derive the values once and feed several aggregations.
 */
public final class Aggregations {

  private Aggregations() {}

  /** Groups values by the selected key. */
  public static <F, K> Grouped<F, K> groupBy(final KeySelector<F, K> keySelector) {
    return new Grouped<>(keySelector);
  }
}
