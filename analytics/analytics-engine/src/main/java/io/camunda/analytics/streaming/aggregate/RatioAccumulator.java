/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * The accumulator for {@link RatioAggregateFunction}: how many facts matched the predicate and how
 * many were seen in total. Both are additive, which is what makes the merge exact.
 *
 * @param matched the number of facts satisfying the predicate
 * @param total the number of facts folded in
 */
public record RatioAccumulator(long matched, long total) {

  public static RatioAccumulator empty() {
    return new RatioAccumulator(0L, 0L);
  }
}
