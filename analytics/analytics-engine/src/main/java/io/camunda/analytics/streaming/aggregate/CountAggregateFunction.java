/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * A standard reusable aggregate that counts facts. The accumulator and result are both the running
 * count; {@code merge} adds, so it pre-aggregates and combines across partitions freely.
 *
 * @param <F> the fact type (its value is irrelevant — only occurrences are counted)
 */
public final class CountAggregateFunction<F> implements AggregateFunction<F, Long, Long> {

  @Override
  public Long createAccumulator() {
    return 0L;
  }

  @Override
  public Long add(final F value, final Long count) {
    return count + 1;
  }

  @Override
  public Long merge(final Long a, final Long b) {
    return a + b;
  }

  @Override
  public Long getResult(final Long count) {
    return count;
  }
}
