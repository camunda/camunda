/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.util.List;

/**
 * The read-facing result of {@link TopKAggregateFunction}: the heavy hitters in descending
 * estimated frequency. Each {@link Item} carries the estimated count and the sketch's guaranteed
 * {@code lowerBound}..{@code upperBound} interval around it.
 *
 * @param items the top items, most frequent first
 */
public record TopKResult(List<Item> items) {

  /**
   * One heavy hitter.
   *
   * @param item the value
   * @param estimate its estimated occurrence count
   * @param lowerBound the guaranteed lower bound on the true count
   * @param upperBound the guaranteed upper bound on the true count
   */
  public record Item(String item, long estimate, long lowerBound, long upperBound) {}
}
