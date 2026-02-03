/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.List;
import java.util.SequencedCollection;
import java.util.function.Function;

/**
 * Represents a generic range with a start and end value.
 *
 * @param <T> the type of the range values, must be comparable
 * @param start the first value in the range (inclusive)
 * @param end the last value in the range (inclusive)
 */
public record Interval<T extends Comparable<T>>(T start, T end) {
  /**
   * @param other interval to check
   * @return true if this interval completely covers the other interval
   */
  public boolean contains(final Interval<T> other) {
    return start.compareTo(other.start) <= 0 && end.compareTo(other.end) >= 0;
  }

  /**
   * @param value the value to check
   * @return true if the value is within this interval
   */
  public boolean contains(final T value) {
    return value.compareTo(start) >= 0 && value.compareTo(end) <= 0;
  }

  public SequencedCollection<T> values() {
    return List.of(start, end);
  }

  /**
   * Maps this interval to a new interval by applying the given function to both start and end
   * values.
   *
   * @param <U> the type of the resulting interval values
   * @param mapper the function to apply to start and end values
   * @return a new interval with transformed values
   */
  public <U extends Comparable<U>> Interval<U> map(final Function<T, U> mapper) {
    return new Interval<>(mapper.apply(start), mapper.apply(end));
  }
}
