/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import com.esotericsoftware.kryo.util.Null;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a generic range with a start and end value, with configurable inclusiveness for each
 * bound. Supports unbounded intervals by allowing null values for start and/or end.
 *
 * <p>Unbounded intervals:
 *
 * <ul>
 *   <li>{@code start = null}: represents negative infinity (-infinity)
 *   <li>{@code end = null}: represents positive infinity (infinity)
 *   <li>Both null: represents the entire domain (-infinity, infinity)
 * </ul>
 *
 * <p>When a bound is null (unbounded), the inclusiveness flag is ignored since infinity is not a
 * reachable value. Unbounded ends are always treated as exclusive.
 *
 * @param <T> the type of the range values, must be comparable
 * @param start the first value in the range, or null for negative infinity
 * @param startInclusive whether the start bound is inclusive (ignored if start is null)
 * @param end the last value in the range, or null for positive infinity
 * @param endInclusive whether the end bound is inclusive (ignored if end is null)
 */
@NullMarked
public record Interval<T extends @Nullable Comparable<T>>(
    T start, boolean startInclusive, T end, boolean endInclusive) {

  /** For start bounds, inclusive is "before" exclusive when values are equal. */
  private static final int START_INCLUSIVE_SIGN = -1;

  /** For end bounds, inclusive is "after" exclusive when values are equal. */
  private static final int END_INCLUSIVE_SIGN = 1;

  public Interval {
    // Both bounds can be null (unbounded), but if both are present, start must be <= end
    if (start != null && end != null) {
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
  }

  /**
   * Creates a closed interval [start, end] where both bounds are inclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (inclusive)
   */
  public Interval(@Nullable final T start, @Null final T end) {
    this(start, true, end, true);
  }

  /**
   * Creates a closed interval [start, end] where both bounds are inclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (inclusive)
   * @return a new closed interval
   */
  public static <T extends @Nullable Comparable<T>> Interval<T> closed(final T start, final T end) {
    return new Interval<>(start, true, end, true);
  }

  /**
   * Creates an open interval (start, end) where both bounds are exclusive.
   *
   * @param start the first value in the range (exclusive)
   * @param end the last value in the range (exclusive)
   * @return a new open interval
   */
  public static <T extends @Nullable Comparable<T>> Interval<T> open(final T start, final T end) {
    return new Interval<>(start, false, end, false);
  }

  /**
   * Creates a half-open interval [start, end) where start is inclusive and end is exclusive.
   *
   * @param start the first value in the range (inclusive)
   * @param end the last value in the range (exclusive)
   * @return a new half-open interval
   */
  public static <T extends @Nullable Comparable<T>> Interval<T> closedOpen(
      final T start, final T end) {
    return new Interval<>(start, true, end, false);
  }

  /**
   * Creates a half-open interval (start, end] where start is exclusive and end is inclusive.
   *
   * @param start the first value in the range (exclusive)
   * @param end the last value in the range (inclusive)
   * @return a new half-open interval
   */
  public static <T extends @Nullable Comparable<T>> Interval<T> openClosed(
      final T start, final T end) {
    return new Interval<>(start, false, end, true);
  }

  public static <T extends Comparable<T>> Interval<T> point(final T value) {
    return Interval.closed(value, value);
  }

  /**
   * Creates a new Interval instance with the specified end value.
   *
   * @param newEnd The new end value for the interval.
   * @return A new Interval instance with the updated end value, while preserving the start, start
   *     inclusion, and end inclusion properties of the original interval.
   */
  public Interval<T> withEnd(@Nullable final T newEnd) {
    return new Interval<>(start, startInclusive, newEnd, endInclusive);
  }

  /**
   * Creates a new Interval instance with a specified start value while preserving the other
   * properties of the original interval.
   *
   * @param newStart the new start value for the interval
   * @return a new Interval instance with the updated start value
   */
  public Interval<T> withStart(@Nullable final T newStart) {
    return new Interval<>(newStart, startInclusive, end, endInclusive);
  }

  public Interval<T> withStartInclusive(final boolean startInclusive) {
    return new Interval<>(start, startInclusive, end, endInclusive);
  }

  public Interval<T> withEndInclusive(final boolean endInclusive) {
    return new Interval<>(start, startInclusive, end, endInclusive);
  }

  /**
   * @return if the interval is unbounded in at least one direction
   */
  public boolean isUnbounded() {
    return start == null || end == null;
  }

  /**
   * Computes the intersection of all intervals in the collection. The intersection is the largest
   * interval that is contained by all input intervals.
   *
   * @param intervals the collection of intervals to intersect
   * @return the intersection interval, or empty if the intervals don't all overlap
   * @throws IllegalArgumentException if the collection is empty
   */
  public static <T extends Comparable<T>> Optional<Interval<T>> intersection(
      final Collection<Interval<T>> intervals) {
    if (intervals.isEmpty()) {
      throw new IllegalArgumentException("Cannot compute intersection of empty collection");
    }

    final var iterator = intervals.iterator();
    final var first = iterator.next();

    var maxStart = first.start;
    var maxStartInclusive = first.startInclusive;
    var minEnd = first.end;
    var minEndInclusive = first.endInclusive;

    while (iterator.hasNext()) {
      final var interval = iterator.next();

      // Update start bound: take the maximum (most restrictive)
      final var startComparison =
          compareBound(
              interval.start,
              interval.startInclusive,
              maxStart,
              maxStartInclusive,
              START_INCLUSIVE_SIGN);
      if (startComparison > 0) {
        maxStart = interval.start;
        maxStartInclusive = interval.startInclusive;
      }

      // Update end bound: take the minimum (most restrictive)
      final var endComparison =
          compareBound(
              interval.end, interval.endInclusive, minEnd, minEndInclusive, END_INCLUSIVE_SIGN);
      if (endComparison < 0) {
        minEnd = interval.end;
        minEndInclusive = interval.endInclusive;
      }
    }

    // Check if the resulting interval is valid
    // If either bound is null (unbounded), the interval is valid
    if (maxStart != null && minEnd != null) {
      final var comparison = maxStart.compareTo(minEnd);
      if (comparison > 0) {
        return Optional.empty();
      }
      if (comparison == 0 && (!maxStartInclusive || !minEndInclusive)) {
        return Optional.empty();
      }
    }

    return Optional.of(new Interval<>(maxStart, maxStartInclusive, minEnd, minEndInclusive));
  }

  /**
   * Convenience overload of {@link #smallestCover(List, Function)} that uses the identity mapper.
   *
   * @see #smallestCover(List, Function)
   */
  public List<T> smallestCover(final List<@NonNull T> sortedPoints) {
    return smallestCover(sortedPoints, Function.identity());
  }

  /**
   * Returns the smallest subset of sorted points that covers this interval, using a mapper function
   * to extract the comparable value from each point.
   *
   * <p>The result includes:
   *
   * <ul>
   *   <li>All points whose mapped value falls within this interval.
   *   <li>The last point before the interval start, if no point has an exact match at the start
   *       boundary.
   *   <li>The first point after the interval end, if no point has an exact match at the end
   *       boundary.
   * </ul>
   *
   * <p>A valid cover requires at least one point at or before the interval start and at least one
   * point at or after the interval end. If the points cannot cover the interval, an empty list is
   * returned.
   *
   * <p>For unbounded intervals:
   *
   * <ul>
   *   <li>If start is null (-infinity), start coverage is always satisfied
   *   <li>If end is null (+infinity), end coverage is always satisfied
   * </ul>
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Interval [3, 7], points [1, 3, 5, 7, 9] &rarr; [3, 5, 7] (exact matches at both
   *       boundaries)
   *   <li>Interval [4, 6], points [1, 3, 5, 7, 9] &rarr; [3, 5, 7] (3 is last before 4, 7 is first
   *       after 6)
   *   <li>Interval [4, 6], points [1, 3, 7, 9] &rarr; [3, 7] (no points within, neighbors cover)
   *   <li>Interval [10, 20], points [1, 3, 5] &rarr; [] (no coverage at end)
   *   <li>Interval [5, infinity), points [1, 3, 7, 9] &rarr; [3, 7, 9] (all from lastBefore onward)
   *   <li>Interval (-infinity, 5], points [1, 3, 7, 9] &rarr; [1, 3, 7] (all up to firstAfter)
   * </ul>
   *
   * <p>This is useful when the points are of a different type than the interval bounds. For
   * example, you might have a list of {@code BackupStatus} objects but want to query using an
   * {@code Interval<Instant>} by mapping each backup status to its timestamp.
   *
   * @param sortedPoints the points in strictly ascending order with no duplicates (by the mapped
   *     value)
   * @param mapper function to extract the comparable value from each point
   * @return the subset of points that covers this interval, or empty if no cover exists
   * @param <S> the type of the points
   */
  public <S> List<S> smallestCover(
      final List<S> sortedPoints, final Function<S, @NonNull T> mapper) {
    S lastBefore = null;
    S firstAfter = null;
    final var within = new ArrayList<S>();

    T previousValue = null;
    for (final var point : sortedPoints) {
      final var value = mapper.apply(point);
      if (previousValue != null && value.compareTo(previousValue) <= 0) {
        throw new IllegalArgumentException(
            "Points must be strictly sorted in ascending order with no duplicates, but found %s after %s"
                .formatted(value, previousValue));
      }
      previousValue = value;

      // Determine where this point falls relative to the interval
      final boolean beforeStart = start != null && value.compareTo(start) < 0;
      final boolean afterEnd = end != null && value.compareTo(end) > 0;

      if (beforeStart) {
        lastBefore = point;
      } else if (afterEnd) {
        if (firstAfter == null) {
          firstAfter = point;
        }
      } else {
        // Point is within the interval (or unbounded on that side)
        within.add(point);
      }
    }

    // A valid cover requires at least one point at-or-before start (or start is unbounded)
    // and at least one point at-or-after end (or end is unbounded)
    final boolean hasStartCoverage;
    if (start == null) {
      // Unbounded start: always covered
      hasStartCoverage = true;
    } else {
      hasStartCoverage =
          lastBefore != null
              || (!within.isEmpty() && mapper.apply(within.getFirst()).compareTo(start) <= 0);
    }

    final boolean hasEndCoverage;
    if (end == null) {
      // Unbounded end: always covered
      hasEndCoverage = true;
    } else {
      hasEndCoverage =
          firstAfter != null
              || (!within.isEmpty() && mapper.apply(within.getLast()).compareTo(end) >= 0);
    }

    if (!hasStartCoverage || !hasEndCoverage) {
      return List.of();
    }

    final var result = new ArrayList<S>();

    // Include last point before start if no point has an exact match at start
    if (lastBefore != null) {
      if (within.isEmpty() || mapper.apply(within.getFirst()).compareTo(start) != 0) {
        result.add(lastBefore);
      }
    }

    result.addAll(within);

    // Include first point after end if no point has an exact match at end
    if (firstAfter != null) {
      if (within.isEmpty() || mapper.apply(within.getLast()).compareTo(end) != 0) {
        result.add(firstAfter);
      }
    }

    return result;
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
   * @param value the value to check (must not be null)
   * @return true if the value is within this interval
   */
  public boolean contains(final @NonNull T value) {
    // Null start means -infinity, so any value is after start
    final boolean afterStart;
    if (start == null) {
      afterStart = true;
    } else {
      final var startComparison = value.compareTo(start);
      afterStart = startInclusive ? startComparison >= 0 : startComparison > 0;
    }

    // Null end means +infinity, so any value is before end
    final boolean beforeEnd;
    if (end == null) {
      beforeEnd = true;
    } else {
      final var endComparison = value.compareTo(end);
      beforeEnd = endInclusive ? endComparison <= 0 : endComparison < 0;
    }

    return afterStart && beforeEnd;
  }

  /**
   * Checks if a value is contained in this interval by mapping the interval bounds to a comparable
   * type.
   *
   * @param value the value to check
   * @param mapper function to map interval bounds to the comparable type
   * @return true if the value is within the mapped interval
   * @param <U> the comparable type to map to
   */
  public <U extends Comparable<U>> boolean contains(final U value, final Function<T, U> mapper) {
    return map(mapper).contains(value);
  }

  /**
   * Checks if another interval is completely contained in this interval by mapping both intervals
   * to a comparable type.
   *
   * @param other the interval to check
   * @param mapper function to map interval bounds to the comparable type
   * @return true if the other interval is completely within the mapped interval
   * @param <U> the comparable type to map to
   */
  public <U extends Comparable<U>> boolean contains(
      final Interval<U> other, final Function<T, U> mapper) {
    return map(mapper).contains(other);
  }

  /**
   * @param other interval to check
   * @return true if this interval is entirely before the other (no overlap)
   */
  public boolean isBefore(final Interval<T> other) {
    // If this interval extends to +infinity, it can never be entirely before another
    if (end == null) {
      return false;
    }
    // If other interval extends from -infinity, this can never be before it
    if (other.start == null) {
      return false;
    }
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
   * Maps this interval to a new interval by applying the given function to both start and end
   * values. Null bounds (representing infinity) are preserved as null in the result.
   *
   * @param <U> the type of the resulting interval values
   * @param mapper the function to apply to start and end values (not called for null bounds)
   * @return a new interval with transformed values
   */
  public <U extends @Nullable Comparable<U>> Interval<U> map(final Function<@NonNull T, U> mapper) {
    return new Interval<>(
        Optional.ofNullable(start).map(mapper).orElse(null),
        startInclusive,
        Optional.ofNullable(end).map(mapper).orElse(null),
        endInclusive);
  }

  /**
   * Returns the mathematical notation for this interval.
   *
   * <p>Unbounded intervals use "infinity" notation:
   *
   * <ul>
   *   <li>{@code (-infinity, 10]} - left-unbounded
   *   <li>{@code [5, infinity)} - right-unbounded
   *   <li>{@code (-infinity, infinity)} - fully unbounded
   * </ul>
   *
   * @return string representation using [ ] for inclusive and ( ) for exclusive bounds
   */
  @Override
  public String toString() {
    // Null bounds are always exclusive (infinity is not reachable)
    final var startBracket = (start == null || !startInclusive) ? "(" : "[";
    final var endBracket = (end == null || !endInclusive) ? ")" : "]";
    final var startStr = start != null ? start.toString() : "-infinity";
    final var endStr = end != null ? end.toString() : "infinity";
    return "%s%s, %s%s".formatted(startBracket, startStr, endStr, endBracket);
  }

  /**
   * Compares bounds considering inclusiveness and null values (representing infinity).
   *
   * <p>For start bounds (inclusiveSign = -1):
   *
   * <ul>
   *   <li>null represents -infinity (smallest possible value)
   *   <li>null is always "before" any non-null value
   * </ul>
   *
   * <p>For end bounds (inclusiveSign = +1):
   *
   * <ul>
   *   <li>null represents +infinity (largest possible value)
   *   <li>null is always "after" any non-null value
   * </ul>
   *
   * @param bound1 the first bound value (null for infinity)
   * @param inclusive1 whether the first bound is inclusive (ignored if null)
   * @param bound2 the second bound value (null for infinity)
   * @param inclusive2 whether the second bound is inclusive (ignored if null)
   * @param inclusiveSign -1 for start bounds (inclusive is "before"), +1 for end bounds (inclusive
   *     is "after")
   * @return negative if first bound is "before", positive if "after", 0 if equal
   */
  private static <T extends Comparable<T>> int compareBound(
      @Nullable final T bound1,
      final boolean inclusive1,
      @Nullable final T bound2,
      final boolean inclusive2,
      final int inclusiveSign) {
    // Handle null (infinity) cases
    if (bound1 == null && bound2 == null) {
      return 0; // Both are infinity (same direction based on inclusiveSign)
    }
    if (bound1 == null) {
      // For start bounds: null (-infinity) is before any value -> negative
      // For end bounds: null (+infinity) is after any value -> positive
      return inclusiveSign;
    }
    if (bound2 == null) {
      // Opposite of above
      return -inclusiveSign;
    }

    // Both bounds are non-null, compare normally
    final var comparison = bound1.compareTo(bound2);
    if (comparison != 0) {
      return comparison;
    }
    if (inclusive1 == inclusive2) {
      return 0;
    }
    return inclusive1 ? inclusiveSign : -inclusiveSign;
  }
}
