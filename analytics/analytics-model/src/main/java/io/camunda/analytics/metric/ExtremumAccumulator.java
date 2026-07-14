/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The accumulator for the standalone {@code min}/{@code max} meters: the running extremum plus the
 * observation count that distinguishes "no observations" from a legitimate value. Empty uses the
 * identity sentinel of its direction ({@code Long.MAX_VALUE} for min, {@code Long.MIN_VALUE} for
 * max) so an empty slot never wins a merge or poisons a store-side {@code MIN}/{@code MAX} column
 * roll-up; the read maps {@code count == 0} to {@code 0} instead of surfacing the sentinel.
 */
public record ExtremumAccumulator(long count, long extremum) {

  /** The empty min accumulator ({@code Long.MAX_VALUE}: any observation wins). */
  public static ExtremumAccumulator emptyMin() {
    return new ExtremumAccumulator(0L, Long.MAX_VALUE);
  }

  /** The empty max accumulator ({@code Long.MIN_VALUE}: any observation wins). */
  public static ExtremumAccumulator emptyMax() {
    return new ExtremumAccumulator(0L, Long.MIN_VALUE);
  }
}
