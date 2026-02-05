/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Function;

/**
 * Represents a generic range with a start and end value, with configurable inclusiveness for each
 * bound.
 *
 * @param <T> the type of the range values, must be comparable
 * @param start the first value in the range
 * @param startInclusive whether the start bound is inclusive
 * @param end the last value in the range
 * @param endInclusive whether the end bound is inclusive
 */
public record Interval<T extends Comparable<T>>(
    T start, boolean startInclusive, T end, boolean endInclusive) {

  /** For start bounds, inclusive is "before" exclusive when values are equal. */
  private static final int START_INCLUSIVE_SIGN = -1;

  /** For end bounds, inclusive is "after" exclusive when values are equal. */
  private static final int END_INCLUSIVE_SIGN = 1;

  public Interval {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    final var comparison = start.compareTo(end);
    if (comparison > 0) {
      throw new IllegalArgumentException(
          "Expected start <= end, but got start = %s, end = %s".formatted(start, end));
    }
    // For equal bounds, both must be inclusive or the interval is empty/invalid
    if (comparison == 0 && (!startInclusive || !endInclusive)) {
      throw new IllegalArgumentException(
          "Interval with equal bounds must be inclusive on both sides, got %s%s, %s%s"
              .formatted(startInclusive ? "[" : "(", start, end, endInclusive ? "]" : ")"));
    }
  }

  /**
   * Creates a closed interval [start, end] where both bounds are inclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (inclusive)
   */
  public Interval(final T start, final T end) {
    this(start, true, end, true);
  }

  /**
   * Creates a closed interval [start, end] where both bounds are inclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (inclusive)
   * @return a new closed interval
   */
  public static <T extends Comparable<T>> Interval<T> closed(final T start, final T end) {
    return new Interval<>(start, true, end, true);
  }

  /**
   * Creates an open interval (start, end) where both bounds are exclusive.
   *
   * @param start the first value in the range (exclusive)
   * @param end the last value in the range (exclusive)
   * @return a new open interval
   */
  public static <T extends Comparable<T>> Interval<T> open(final T start, final T end) {
    return new Interval<>(start, false, end, false);
  }

  /**
   * Creates a half-open interval [start, end) where start is inclusive and end is exclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (exclusive)
   * @return a new half-open interval
   */
  public static <T extends Comparable<T>> Interval<T> closedOpen(final T start, final T end) {
    return new Interval<>(start, true, end, false);
  }

  /**
   * Creates a half-open interval (start, end] where start is exclusive and end is inclusive.
   *
   * @param start the first value in the range (exclusive)
   * @param end the last value in the range (inclusive)
   * @return a new half-open interval
   */
  public static <T extends Comparable<T>> Interval<T> openClosed(final T start, final T end) {
    return new Interval<>(start, false, end, true);
  }

  /**
   * @param other interval to check
   * @return true if this interval completely covers the other interval
   */
  public boolean contains(final Interval<T> other) {
    return compareBound(
                start, startInclusive, other.start, other.startInclusive, START_INCLUSIVE_SIGN)
            <= 0
        && compareBound(end, endInclusive, other.end, other.endInclusive, END_INCLUSIVE_SIGN) >= 0;
  }

  /**
   * @param value the value to check
   * @return true if the value is within this interval
   */
  public boolean contains(final T value) {
    final var startComparison = value.compareTo(start);
    final var endComparison = value.compareTo(end);

    final var afterStart = startInclusive ? startComparison >= 0 : startComparison > 0;
    final var beforeEnd = endInclusive ? endComparison <= 0 : endComparison < 0;

    return afterStart && beforeEnd;
  }

  /**
   * @param other interval to check
   * @return true if this interval is entirely before the other (no overlap)
   */
  public boolean isBefore(final Interval<T> other) {
    final var comparison = end.compareTo(other.start);
    return comparison < 0 || (comparison == 0 && (!endInclusive || !other.startInclusive));
  }

  /**
   * @param other interval to check
   * @return true if this interval is entirely after the other (no overlap)
   */
  public boolean isAfter(final Interval<T> other) {
    return other.isBefore(this);
  }

  /**
   * @param other interval to check
   * @return true if this interval overlaps with the other interval
   */
  public boolean overlapsWith(final Interval<T> other) {
    return !isBefore(other) && !isAfter(other);
  }

  /**
   * Given a sorted collections of intervals where interval[n-1].end == interval[n].start, returns
   * the smallest interval that covers all intervals in the collection.
   *
   * @param intervals the collection of contiguous intervals
   * @return the intervals that overlap with this interval
   */
  public SequencedCollection<Interval<T>> smallestCover(
      final SequencedCollection<Interval<T>> intervals) {
    final var result = new ArrayList<Interval<T>>();
    Interval<T> previousInterval = null;
    var i = 0;
    for (final var interval : intervals) {
      if (previousInterval != null && !areContiguous(previousInterval, interval)) {
        throw new IllegalArgumentException(
            "Expected intervals to be contiguous, but interval at index %d is %s, interval at index %d is %s"
                .formatted(i - 1, previousInterval, i, interval));
      }
      if (interval.overlapsWith(this)) {
        result.add(interval);
      }
      previousInterval = interval;
      i++;
    }
    return result;
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
    return new Interval<>(mapper.apply(start), startInclusive, mapper.apply(end), endInclusive);
  }

  /**
   * Returns the mathematical notation for this interval.
   *
   * @return string representation using [ ] for inclusive and ( ) for exclusive bounds
   */
  @Override
  public String toString() {
    return "%s%s, %s%s".formatted(startInclusive ? "[" : "(", start, end, endInclusive ? "]" : ")");
  }

  /**
   * Compares bounds considering inclusiveness.
   *
   * @param thisBound this interval's bound value
   * @param thisInclusive whether this bound is inclusive
   * @param otherBound the other bound value
   * @param otherInclusive whether the other bound is inclusive
   * @param inclusiveSign -1 for start bounds (inclusive is "before"), +1 for end bounds (inclusive
   *     is "after")
   * @return negative if this bound is "before", positive if "after", 0 if equal
   */
  private int compareBound(
      final T thisBound,
      final boolean thisInclusive,
      final T otherBound,
      final boolean otherInclusive,
      final int inclusiveSign) {
    final var comparison = thisBound.compareTo(otherBound);
    if (comparison != 0) {
      return comparison;
    }
    if (thisInclusive == otherInclusive) {
      return 0;
    }
    return thisInclusive ? inclusiveSign : -inclusiveSign;
  }

  /**
   * Checks if two intervals are contiguous (meet at a boundary point without gap).
   *
   * @return true if the intervals are contiguous
   */
  private static <T extends Comparable<T>> boolean areContiguous(
      final Interval<T> first, final Interval<T> second) {
    // Contiguous means end == start and at least one bound is inclusive (no gap)
    // [a, b] and [b, c] - both inclusive, contiguous (overlap at b is ok)
    // [a, b) and [b, c] - first exclusive, second inclusive, contiguous
    // [a, b] and (b, c] - first inclusive, second exclusive, contiguous
    // [a, b) and (b, c] - both exclusive, NOT contiguous (gap at b)
    return first.end.compareTo(second.start) == 0 && (first.endInclusive || second.startInclusive);
  }
}
