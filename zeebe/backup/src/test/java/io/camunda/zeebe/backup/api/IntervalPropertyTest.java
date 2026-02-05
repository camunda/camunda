/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link Interval} that verify mathematical properties hold for all valid
 * intervals.
 */
final class IntervalPropertyTest {

  // ==================== Arbitrary Providers ====================

  @Provide
  Arbitrary<Interval<Integer>> intervals() {
    final var start = Arbitraries.integers().between(-1000, 1000);
    final var length = Arbitraries.integers().between(0, 500);
    final var startInclusive = Arbitraries.of(true, false);
    final var endInclusive = Arbitraries.of(true, false);

    return Combinators.combine(start, length, startInclusive, endInclusive)
        .as(
            (s, len, si, ei) -> {
              final var end = s + len;
              // For equal bounds, both must be inclusive
              if (len == 0) {
                return new Interval<>(s, true, end, true);
              }
              return new Interval<>(s, si, end, ei);
            });
  }

  @Provide
  Arbitrary<Integer> values() {
    return Arbitraries.integers().between(-1500, 1500);
  }

  /**
   * Generates a list of contiguous intervals that partition a range. Each interval in the list
   * meets the next one at a boundary point without gaps.
   *
   * <p>Uses cumulative boundaries approach: generate sorted boundary points, then create intervals
   * between consecutive points. This ensures contiguity is maintained even during shrinking.
   */
  @Provide
  Arbitrary<List<Interval<Integer>>> contiguousIntervalLists() {
    // Generate 2-9 unique sorted boundary points, then create intervals between them
    return Arbitraries.integers()
        .between(-500, 500)
        .list()
        .ofMinSize(2)
        .ofMaxSize(9)
        .map(
            points -> {
              // Sort and remove duplicates to get boundaries
              final var boundaries = points.stream().distinct().sorted().toList();
              if (boundaries.size() < 2) {
                // Need at least 2 points for 1 interval
                return List.of(Interval.closed(0, 1));
              }

              final var intervals = new ArrayList<Interval<Integer>>();
              for (int i = 0; i < boundaries.size() - 1; i++) {
                final var start = boundaries.get(i);
                final var end = boundaries.get(i + 1);
                // Use [start, end) for all but last, [start, end] for last
                if (i < boundaries.size() - 2) {
                  intervals.add(Interval.closedOpen(start, end));
                } else {
                  intervals.add(Interval.closed(start, end));
                }
              }
              return intervals;
            })
        .filter(list -> !list.isEmpty());
  }

  // ==================== Equals and HashCode ====================

  @Property(tries = 100)
  void equalsIsReflexive(@ForAll("intervals") final Interval<Integer> a) {
    assertThat(a).isEqualTo(a);
  }

  @Property(tries = 100)
  void equalsIsSymmetric(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    assertThat(a.equals(b)).isEqualTo(b.equals(a));
  }

  @Property(tries = 100)
  void hashCodeIsConsistentWithEquals(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    if (a.equals(b)) {
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
  }

  // ==================== Contains Interval ====================

  @Property(tries = 100)
  void containsIntervalIsReflexive(@ForAll("intervals") final Interval<Integer> a) {
    // Every interval contains itself
    assertThat(a.contains(a)).isTrue();
  }

  @Property(tries = 100)
  void containsIntervalIsAntisymmetric(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If a contains b and b contains a, they must be equal
    if (a.contains(b) && b.contains(a)) {
      assertThat(a).isEqualTo(b);
    }
  }

  @Property(tries = 100)
  void containsIntervalIsTransitive(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b,
      @ForAll("intervals") final Interval<Integer> c) {
    // If a contains b and b contains c, then a contains c
    if (a.contains(b) && b.contains(c)) {
      assertThat(a.contains(c)).isTrue();
    }
  }

  // ==================== IsBefore and IsAfter ====================

  @Property(tries = 100)
  void isBeforeAndIsAfterAreInverses(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // a.isBefore(b) == b.isAfter(a)
    assertThat(a.isBefore(b)).isEqualTo(b.isAfter(a));
  }

  @Property(tries = 100)
  void isBeforeAndIsAfterAreMutuallyExclusive(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // Cannot be both before and after
    assertThat(a.isBefore(b) && a.isAfter(b)).isFalse();
  }

  @Property(tries = 100)
  void isBeforeIsIrreflexive(@ForAll("intervals") final Interval<Integer> a) {
    // An interval is never before itself
    assertThat(a.isBefore(a)).isFalse();
  }

  @Property(tries = 100)
  void isAfterIsIrreflexive(@ForAll("intervals") final Interval<Integer> a) {
    // An interval is never after itself
    assertThat(a.isAfter(a)).isFalse();
  }

  @Property(tries = 100)
  void isBeforeIsAsymmetric(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If a is before b, then b is not before a
    if (a.isBefore(b)) {
      assertThat(b.isBefore(a)).isFalse();
    }
  }

  @Property(tries = 100)
  void isBeforeIsTransitive(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b,
      @ForAll("intervals") final Interval<Integer> c) {
    // If a is before b and b is before c, then a is before c
    if (a.isBefore(b) && b.isBefore(c)) {
      assertThat(a.isBefore(c)).isTrue();
    }
  }

  @Property(tries = 100)
  void isAfterIsTransitive(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b,
      @ForAll("intervals") final Interval<Integer> c) {
    // If a is after b and b is after c, then a is after c
    if (a.isAfter(b) && b.isAfter(c)) {
      assertThat(a.isAfter(c)).isTrue();
    }
  }

  // ==================== OverlapsWith ====================

  @Property(tries = 100)
  void overlapsWithIsCommutative(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // a.overlapsWith(b) == b.overlapsWith(a)
    assertThat(a.overlapsWith(b)).isEqualTo(b.overlapsWith(a));
  }

  @Property(tries = 100)
  void overlapsWithIsReflexive(@ForAll("intervals") final Interval<Integer> a) {
    // Every interval overlaps with itself
    assertThat(a.overlapsWith(a)).isTrue();
  }

  @Property(tries = 100)
  void containmentImpliesOverlap(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If a contains b, then they overlap
    if (a.contains(b)) {
      assertThat(a.overlapsWith(b)).isTrue();
    }
  }

  @Property(tries = 100)
  void noOverlapMeansBeforeOrAfter(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If intervals don't overlap, one must be before or after the other
    if (!a.overlapsWith(b)) {
      assertThat(a.isBefore(b) || a.isAfter(b)).isTrue();
    }
  }

  @Property(tries = 100)
  void beforeOrAfterMeansNoOverlap(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If one is before/after the other, they don't overlap
    if (a.isBefore(b) || a.isAfter(b)) {
      assertThat(a.overlapsWith(b)).isFalse();
    }
  }

  // ==================== Map ====================

  @Property(tries = 100)
  void mapWithIdentityPreservesInterval(@ForAll("intervals") final Interval<Integer> a) {
    // Mapping with identity function should produce equal interval
    assertThat(a.map(x -> x)).isEqualTo(a);
  }

  @Property(tries = 100)
  void mapPreservesBoundBehavior(@ForAll("intervals") final Interval<Integer> a) {
    // Mapping should preserve the bound behavior
    final var mapped = a.map(x -> x * 2L);

    // Start bound behavior preserved
    assertThat(mapped.contains(mapped.start())).isEqualTo(a.startInclusive());
    // End bound behavior preserved
    assertThat(mapped.contains(mapped.end())).isEqualTo(a.endInclusive());
  }

  @Property(tries = 100)
  void mapPreservesContainmentRelationship(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If a contains b, then map(a) contains map(b) for monotonic functions
    final var mappedA = a.map(x -> x * 2L);
    final var mappedB = b.map(x -> x * 2L);

    if (a.contains(b)) {
      assertThat(mappedA.contains(mappedB)).isTrue();
    }
  }

  // ==================== Contains Value ====================

  @Property(tries = 100)
  void containsValueAtStartDependsOnInclusiveness(@ForAll("intervals") final Interval<Integer> a) {
    assertThat(a.contains(a.start())).isEqualTo(a.startInclusive());
  }

  @Property(tries = 100)
  void containsValueAtEndDependsOnInclusiveness(@ForAll("intervals") final Interval<Integer> a) {
    assertThat(a.contains(a.end())).isEqualTo(a.endInclusive());
  }

  @Property(tries = 100)
  void valuesOutsideIntervalAreNeverContained(
      @ForAll("intervals") final Interval<Integer> a, @ForAll("values") final Integer value) {
    if (value < a.start() || value > a.end()) {
      assertThat(a.contains(value)).isFalse();
    }
  }

  @Property(tries = 100)
  void valuesStrictlyInsideIntervalAreAlwaysContained(
      @ForAll("intervals") final Interval<Integer> a, @ForAll("values") final Integer value) {
    if (value > a.start() && value < a.end()) {
      assertThat(a.contains(value)).isTrue();
    }
  }

  // ==================== SmallestCover ====================

  @Property(tries = 100)
  void smallestCoverOfEmptyListIsEmpty(@ForAll("intervals") final Interval<Integer> a) {
    assertThat(a.smallestCover(List.of())).isEmpty();
  }

  @Property(tries = 100)
  void smallestCoverReturnsOnlyOverlappingIntervals(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("contiguousIntervalLists") final List<Interval<Integer>> intervals) {
    // All returned intervals must overlap with the query interval
    final var result = query.smallestCover(intervals);

    for (final var interval : result) {
      assertThat(interval.overlapsWith(query))
          .describedAs("Returned interval %s should overlap with query %s", interval, query)
          .isTrue();
    }
  }

  @Property(tries = 100)
  void smallestCoverDoesNotExcludeOverlappingIntervals(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("contiguousIntervalLists") final List<Interval<Integer>> intervals) {
    // All overlapping intervals from input must be in the result
    final var result = query.smallestCover(intervals);

    for (final var interval : intervals) {
      if (interval.overlapsWith(query)) {
        assertThat(result)
            .describedAs("Overlapping interval %s should be in result", interval)
            .contains(interval);
      }
    }
  }

  @Property(tries = 100)
  void smallestCoverPreservesInputOrder(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("contiguousIntervalLists") final List<Interval<Integer>> intervals) {
    // Result should be a subsequence of input (preserving order)
    final var result = query.smallestCover(intervals);

    var inputIndex = 0;
    for (final var resultInterval : result) {
      // Find this interval in the remaining input
      while (inputIndex < intervals.size() && !intervals.get(inputIndex).equals(resultInterval)) {
        inputIndex++;
      }
      assertThat(inputIndex)
          .describedAs("Result interval %s should appear in input in order", resultInterval)
          .isLessThan(intervals.size());
      inputIndex++;
    }
  }

  @Property(tries = 100)
  void smallestCoverResultIsContiguous(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("contiguousIntervalLists") final List<Interval<Integer>> intervals) {
    // Since input is contiguous and result is a subsequence, result should also be contiguous
    // (because overlapping intervals from a contiguous list must be consecutive)
    final var result = query.smallestCover(intervals);

    if (result.size() > 1) {
      final var resultList = new ArrayList<>(result);
      for (int i = 0; i < resultList.size() - 1; i++) {
        final var current = resultList.get(i);
        final var next = resultList.get(i + 1);
        // They should meet at a boundary
        assertThat(current.end())
            .describedAs("Interval %s should meet interval %s at boundary", current, next)
            .isEqualTo(next.start());
      }
    }
  }

  @Property(tries = 100)
  void smallestCoverUnionContainsQueryWhenFullyCovered(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("contiguousIntervalLists") final List<Interval<Integer>> intervals) {
    // If the query is fully covered by the input intervals, the result's union should contain query
    if (intervals.isEmpty()) {
      return;
    }

    final var inputStart = intervals.getFirst().start();
    final var inputEnd = intervals.getLast().end();

    // Check if query is within the range covered by input
    if (query.start() >= inputStart && query.end() <= inputEnd) {
      final var result = query.smallestCover(intervals);

      if (!result.isEmpty()) {
        final var resultList = new ArrayList<>(result);
        final var resultStart = resultList.getFirst().start();
        final var resultEnd = resultList.getLast().end();

        // The result's union should cover the query
        assertThat(resultStart)
            .describedAs("Result union start should be <= query start")
            .isLessThanOrEqualTo(query.start());
        assertThat(resultEnd)
            .describedAs("Result union end should be >= query end")
            .isGreaterThanOrEqualTo(query.end());
      }
    }
  }
}
