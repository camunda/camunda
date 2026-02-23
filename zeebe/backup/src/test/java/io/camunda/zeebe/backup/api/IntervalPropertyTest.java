/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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
   * Generates a sorted list of unique integers to use as points for smallestCover tests.
   *
   * <p>Generates 2-10 unique sorted boundary points, ensuring the list is non-empty and sorted in
   * ascending order.
   */
  @Provide
  Arbitrary<List<Integer>> sortedPoints() {
    return Arbitraries.integers()
        .between(-500, 500)
        .list()
        .ofMinSize(2)
        .ofMaxSize(10)
        .map(points -> points.stream().distinct().sorted().toList())
        .filter(list -> list.size() >= 2);
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

  @Property(tries = 100)
  void containsWithMapperIsEquivalentToMapThenContains(
      @ForAll("intervals") final Interval<Integer> a, @ForAll("values") final Integer value) {
    // contains(value, mapper) should be equivalent to map(mapper).contains(value)
    final var mapperResult = a.contains(value * 2L, i -> i * 2L);
    final var mapThenContainsResult = a.map(i -> i * 2L).contains(value * 2L);

    assertThat(mapperResult)
        .describedAs("contains with mapper should equal map().contains()")
        .isEqualTo(mapThenContainsResult);
  }

  @Property(tries = 100)
  void containsIntervalWithMapperIsEquivalentToMapThenContains(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // contains(interval, mapper) should be equivalent to map(mapper).contains(mapped interval)
    final var mappedB = b.map(i -> i * 2L);
    final var mapperResult = a.contains(mappedB, i -> i * 2L);
    final var mapThenContainsResult = a.map(i -> i * 2L).contains(mappedB);

    assertThat(mapperResult)
        .describedAs("contains interval with mapper should equal map().contains()")
        .isEqualTo(mapThenContainsResult);
  }

  @Property(tries = 100)
  void smallestCoverWithMapperReturnsEquivalentResults(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    // smallestCover with mapper should return the same results as
    // mapping the query and calling smallestCover on mapped points
    final var mapper = (Function<Integer, Long>) Integer::longValue;
    final var mappedQuery = query.map(mapper);
    final var mappedPoints = points.stream().map(mapper).toList();

    // Get result using mapper version (query on Long interval, points are Integer)
    final var mapperResult = mappedQuery.smallestCover(points, mapper);
    // Get result by mapping everything first
    final var mappedResult = mappedQuery.smallestCover(mappedPoints);

    // Results should have the same size
    assertThat(mapperResult)
        .describedAs("Mapper result should have same size as mapped result")
        .hasSameSizeAs(mappedResult);

    // Original points from mapper result should correspond to mapped points
    for (int i = 0; i < mapperResult.size(); i++) {
      assertThat(mapper.apply(mapperResult.get(i)))
          .describedAs("Mapped value at index %d should match", i)
          .isEqualTo(mappedResult.get(i));
    }
  }

  // ==================== SmallestCover ====================

  @Property(tries = 100)
  void smallestCoverOfEmptyListIsEmpty(@ForAll("intervals") final Interval<Integer> a) {
    assertThat(a.smallestCover(List.of())).isEmpty();
  }

  @Property(tries = 100)
  void smallestCoverResultIsSubsetOfInput(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    // All returned points must be from the input
    final var result = query.smallestCover(points);

    assertThat(points).describedAs("Input should contain all result points").containsAll(result);
  }

  @Property(tries = 100)
  void smallestCoverContainsAllPointsWithinIntervalWhenCoverExists(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    // When a cover exists, all points within the interval must be in the result
    final var result = query.smallestCover(points);

    if (!result.isEmpty()) {
      for (final var point : points) {
        if (point.compareTo(query.start()) >= 0 && point.compareTo(query.end()) <= 0) {
          assertThat(result)
              .describedAs("Point %s within interval %s should be in result", point, query)
              .contains(point);
        }
      }
    }
  }

  @Property(tries = 100)
  void smallestCoverIsEmptyWhenPointsCannotCover(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    // A cover exists iff there is at least one point at-or-before start
    // AND at least one point at-or-after end
    final var hasStartCoverage = points.stream().anyMatch(p -> p.compareTo(query.start()) <= 0);
    final var hasEndCoverage = points.stream().anyMatch(p -> p.compareTo(query.end()) >= 0);
    final var canCover = hasStartCoverage && hasEndCoverage;

    final var result = query.smallestCover(points);

    if (!canCover) {
      assertThat(result)
          .describedAs(
              "Should be empty when points cannot cover interval %s (hasStart=%s, hasEnd=%s)",
              query, hasStartCoverage, hasEndCoverage)
          .isEmpty();
    } else {
      assertThat(result)
          .describedAs("Should not be empty when points can cover interval %s", query)
          .isNotEmpty();
    }
  }

  @Property(tries = 100)
  void smallestCoverResultIsSorted(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    final var result = query.smallestCover(points);

    if (result.size() > 1) {
      for (int i = 0; i < result.size() - 1; i++) {
        assertThat(result.get(i))
            .describedAs("Result should be sorted")
            .isLessThan(result.get(i + 1));
      }
    }
  }

  @Property(tries = 100)
  void smallestCoverIncludesAtMostOnePointBeforeStart(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    final var result = query.smallestCover(points);

    // Count points before interval start in result
    final var pointsBefore = result.stream().filter(p -> p.compareTo(query.start()) < 0).toList();

    assertThat(pointsBefore)
        .describedAs("At most one point before interval start")
        .hasSizeLessThanOrEqualTo(1);
  }

  @Property(tries = 100)
  void smallestCoverIncludesAtMostOnePointAfterEnd(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    final var result = query.smallestCover(points);

    // Count points after interval end in result
    final var pointsAfter = result.stream().filter(p -> p.compareTo(query.end()) > 0).toList();

    assertThat(pointsAfter)
        .describedAs("At most one point after interval end")
        .hasSizeLessThanOrEqualTo(1);
  }

  @Property(tries = 100)
  void smallestCoverNeighborOnlyIncludedWhenNoExactMatch(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    final var result = query.smallestCover(points);
    final var withinPoints =
        points.stream()
            .filter(p -> p.compareTo(query.start()) >= 0 && p.compareTo(query.end()) <= 0)
            .toList();

    // If there's an exact match at start, no point before start should be included
    if (!withinPoints.isEmpty() && withinPoints.getFirst().compareTo(query.start()) == 0) {
      assertThat(result.stream().noneMatch(p -> p.compareTo(query.start()) < 0))
          .describedAs("No point before start when exact match exists at start")
          .isTrue();
    }

    // If there's an exact match at end, no point after end should be included
    if (!withinPoints.isEmpty() && withinPoints.getLast().compareTo(query.end()) == 0) {
      assertThat(result.stream().noneMatch(p -> p.compareTo(query.end()) > 0))
          .describedAs("No point after end when exact match exists at end")
          .isTrue();
    }
  }

  @Property(tries = 100)
  void smallestCoverThrowsWhenPointsAreNotSorted(
      @ForAll("intervals") final Interval<Integer> query,
      @ForAll("sortedPoints") final List<Integer> points) {
    // Shuffle the points to make them unsorted
    final var shuffled = new ArrayList<>(points);
    Collections.shuffle(shuffled);

    // Only test when shuffling actually changed the order
    if (!shuffled.equals(points)) {
      assertThatThrownBy(() -> query.smallestCover(shuffled))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly sorted in ascending order with no duplicates");
    }
  }

  // ==================== Intersection ====================

  @Property(tries = 100)
  void intersectionOfSingleIntervalReturnsSameInterval(
      @ForAll("intervals") final Interval<Integer> a) {
    final var result = Interval.intersection(List.of(a));

    assertThat(result).contains(a);
  }

  @Property(tries = 100)
  void intersectionIsCommutative(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    final var result1 = Interval.intersection(List.of(a, b));
    final var result2 = Interval.intersection(List.of(b, a));

    assertThat(result1).isEqualTo(result2);
  }

  @Property(tries = 100)
  void intersectionIsAssociative(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b,
      @ForAll("intervals") final Interval<Integer> c) {
    // (a ∩ b) ∩ c == a ∩ (b ∩ c)
    final var abThenC =
        Interval.intersection(List.of(a, b)).flatMap(ab -> Interval.intersection(List.of(ab, c)));
    final var aThenBc =
        Interval.intersection(List.of(b, c)).flatMap(bc -> Interval.intersection(List.of(a, bc)));

    assertThat(abThenC).isEqualTo(aThenBc);
  }

  @Property(tries = 100)
  void intersectionIsIdempotent(@ForAll("intervals") final Interval<Integer> a) {
    // a ∩ a == a
    final var result = Interval.intersection(List.of(a, a));

    assertThat(result).contains(a);
  }

  @Property(tries = 100)
  void intersectionResultIsContainedByAllInputs(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    final var result = Interval.intersection(List.of(a, b));

    result.ifPresent(
        intersection -> {
          assertThat(a.contains(intersection))
              .describedAs("First interval %s should contain intersection %s", a, intersection)
              .isTrue();
          assertThat(b.contains(intersection))
              .describedAs("Second interval %s should contain intersection %s", b, intersection)
              .isTrue();
        });
  }

  @Property(tries = 100)
  void intersectionExistsIffIntervalsOverlap(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    final var result = Interval.intersection(List.of(a, b));

    assertThat(result.isPresent())
        .describedAs("Intersection of %s and %s should exist iff they overlap", a, b)
        .isEqualTo(a.overlapsWith(b));
  }

  @Property(tries = 100)
  void intersectionWithContainedIntervalReturnsContained(
      @ForAll("intervals") final Interval<Integer> a,
      @ForAll("intervals") final Interval<Integer> b) {
    // If a contains b, then a ∩ b == b
    if (a.contains(b)) {
      final var result = Interval.intersection(List.of(a, b));

      assertThat(result)
          .describedAs("Intersection of %s (container) and %s (contained) should be %s", a, b, b)
          .contains(b);
    }
  }
}
