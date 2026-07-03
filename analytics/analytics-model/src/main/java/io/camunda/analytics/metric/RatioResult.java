/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The read-facing result of the ratio metric: the raw {@code matched}/{@code total} counts plus the
 * derived {@code ratio} ({@code 0} when nothing was folded). Derived from a {@link
 * RatioAccumulator} on read, keeping the accumulator exact under merge.
 */
public record RatioResult(long matched, long total, double ratio) {

  public static RatioResult of(final RatioAccumulator acc) {
    final double ratio = acc.total() == 0 ? 0.0 : (double) acc.matched() / acc.total();
    return new RatioResult(acc.matched(), acc.total(), ratio);
  }
}
