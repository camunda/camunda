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

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class IntervalTest {

  static Stream<Arguments> inclusivenessVariants() {
    return Stream.of(
        Arguments.of(true, true),
        Arguments.of(true, false),
        Arguments.of(false, true),
        Arguments.of(false, false));
  }

  private static String expectedBracket(final boolean inclusive, final boolean isStart) {
    if (isStart) {
      return inclusive ? "[" : "(";
    } else {
      return inclusive ? "]" : ")";
    }
  }

  @Nested
  class Constructor {

    @Test
    void shouldNotCreateIntervalWithStartGreaterThanEnd() {
      assertThatThrownBy(() -> new Interval<>(10, 5))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected start <= end");
    }

    @Test
    void shouldCreateClosedIntervalWithEqualBounds() {
      // when
      final var interval = new Interval<>(5, 5);

      // then - closed interval contains its bounds
      assertThat(interval.contains(5)).isTrue();
    }

    @Test
    void shouldThrowWhenStartIsNull() {
      assertThatThrownBy(() -> new Interval<>(null, 5))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("start must not be null");
    }

    @Test
    void shouldThrowWhenEndIsNull() {
      assertThatThrownBy(() -> new Interval<>(1, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("end must not be null");
    }

    @ParameterizedTest
    @CsvSource({"false, true", "true, false", "false, false"})
    void shouldThrowWhenEqualBoundsNotFullyInclusive(
        final boolean startInclusive, final boolean endInclusive) {
      assertThatThrownBy(() -> new Interval<>(5, startInclusive, 5, endInclusive))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Interval with equal bounds must be inclusive on both sides");
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldCreateIntervalWithCorrectBoundBehavior(
        final boolean startInclusive, final boolean endInclusive) {
      // when
      final var interval = new Interval<>(1, startInclusive, 10, endInclusive);

      // then - verify behavior at bounds
      assertThat(interval.contains(1)).isEqualTo(startInclusive);
      assertThat(interval.contains(10)).isEqualTo(endInclusive);
      assertThat(interval.contains(5)).isTrue(); // middle always contained
    }

    @Test
    void shouldCreateClosedIntervalUsingFactoryMethod() {
      final var interval = Interval.closed(1, 10);

      assertThat(interval.contains(1)).isTrue();
      assertThat(interval.contains(10)).isTrue();
    }

    @Test
    void shouldCreateOpenIntervalUsingFactoryMethod() {
      final var interval = Interval.open(1, 10);

      assertThat(interval.contains(1)).isFalse();
      assertThat(interval.contains(10)).isFalse();
    }

    @Test
    void shouldCreateClosedOpenIntervalUsingFactoryMethod() {
      final var interval = Interval.closedOpen(1, 10);

      assertThat(interval.contains(1)).isTrue();
      assertThat(interval.contains(10)).isFalse();
    }

    @Test
    void shouldCreateOpenClosedIntervalUsingFactoryMethod() {
      final var interval = Interval.openClosed(1, 10);

      assertThat(interval.contains(1)).isFalse();
      assertThat(interval.contains(10)).isTrue();
    }
  }

  @Nested
  class ContainsInterval {

    @Test
    void shouldContainOpenIntervalWhenClosedHasSameBounds() {
      // [1, 10] contains (1, 10) because exclusive bounds are narrower
      assertThat(Interval.closed(1, 10).contains(Interval.open(1, 10))).isTrue();
    }

    @Test
    void shouldNotContainClosedIntervalWhenOpenHasSameBounds() {
      // (1, 10) does not contain [1, 10] because inclusive bounds are wider
      assertThat(Interval.open(1, 10).contains(Interval.closed(1, 10))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
      // exclusive start does not contain inclusive start at same point
      "false, true,  true, true,  false",
      // exclusive end does not contain inclusive end at same point
      "true, false,  true, true,  false",
      // inclusive contains exclusive at same bounds
      "true, true,   false, false, true",
      // same inclusiveness is contained
      "true, false,  true, false, true",
      "false, true,  false, true, true"
    })
    void shouldRespectBoundInclusivenessWhenCheckingContainment(
        final boolean outerStartIncl,
        final boolean outerEndIncl,
        final boolean innerStartIncl,
        final boolean innerEndIncl,
        final boolean expectedContains) {
      // given
      final var outer = new Interval<>(1, outerStartIncl, 10, outerEndIncl);
      final var inner = new Interval<>(1, innerStartIncl, 10, innerEndIncl);

      // when/then
      assertThat(outer.contains(inner)).isEqualTo(expectedContains);
    }
  }

  @Nested
  class IsBeforeAndIsAfter {

    @Test
    void shouldNotBeBeforeWhenBothBoundsInclusiveAtSamePoint() {
      // [1, 5] is not before [5, 10] because they share point 5
      final var first = Interval.closed(1, 5);
      final var second = Interval.closed(5, 10);

      assertThat(first.isBefore(second)).isFalse();
      assertThat(first.isAfter(second)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
      // [1, 5) is before [5, 10] - first end exclusive
      "true, false,  true, true,  true",
      // [1, 5] is before (5, 10] - second start exclusive
      "true, true,   false, true, true",
      // [1, 5) is before (5, 10] - both exclusive at boundary
      "true, false,  false, true, true",
      // [1, 5] is not before [5, 10] - both inclusive at boundary
      "true, true,   true, true,  false"
    })
    void shouldRespectBoundInclusivenessAtBoundary(
        final boolean firstStartIncl,
        final boolean firstEndIncl,
        final boolean secondStartIncl,
        final boolean secondEndIncl,
        final boolean expectedBefore) {
      // given - intervals meeting at point 5
      final var first = new Interval<>(1, firstStartIncl, 5, firstEndIncl);
      final var second = new Interval<>(5, secondStartIncl, 10, secondEndIncl);

      // when/then
      assertThat(first.isBefore(second)).isEqualTo(expectedBefore);
      assertThat(second.isAfter(first)).isEqualTo(expectedBefore);
    }
  }

  @Nested
  class OverlapsWith {

    @Test
    void shouldOverlapWhenBothBoundsInclusiveAtSamePoint() {
      // [1, 5] and [5, 10] share point 5
      assertThat(Interval.closed(1, 5).overlapsWith(Interval.closed(5, 10))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
      // [1, 5) and [5, 10] - first excludes 5
      "true, false,  true, true,  false",
      // [1, 5] and (5, 10] - second excludes 5
      "true, true,   false, true, false",
      // [1, 5) and (5, 10] - both exclude 5
      "true, false,  false, true, false"
    })
    void shouldNotOverlapWhenBoundaryPointIsExcluded(
        final boolean firstStartIncl,
        final boolean firstEndIncl,
        final boolean secondStartIncl,
        final boolean secondEndIncl,
        final boolean expectedOverlap) {
      // given - intervals meeting at point 5
      final var first = new Interval<>(1, firstStartIncl, 5, firstEndIncl);
      final var second = new Interval<>(5, secondStartIncl, 10, secondEndIncl);

      // when/then
      assertThat(first.overlapsWith(second)).isEqualTo(expectedOverlap);
      assertThat(second.overlapsWith(first)).isEqualTo(expectedOverlap);
    }
  }

  @Nested
  class SmallestCover {

    @Test
    void shouldThrowWhenIntervalsAreNotContiguous() {
      // given
      final var interval = Interval.closed(1, 10);
      final var intervals = List.of(Interval.closedOpen(1, 3), Interval.closed(5, 10));

      // when/then
      assertThatThrownBy(() -> interval.smallestCover(intervals))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected intervals to be contiguous");
    }

    @Test
    void shouldAcceptContiguousIntervalsWithMatchingBoundaries() {
      // given - [1, 5) and [5, 10] are contiguous
      final var interval = Interval.closed(1, 10);
      final var intervals = List.of(Interval.closedOpen(1, 5), Interval.closed(5, 10));

      // when
      final var result = interval.smallestCover(intervals);

      // then
      assertThat(result).containsExactly(Interval.closedOpen(1, 5), Interval.closed(5, 10));
    }

    @Test
    void shouldAcceptContiguousClosedIntervals() {
      // given - [1, 5] and [5, 10] share point 5
      final var interval = Interval.closed(1, 10);
      final var intervals = List.of(Interval.closed(1, 5), Interval.closed(5, 10));

      // when
      final var result = interval.smallestCover(intervals);

      // then
      assertThat(result).containsExactly(Interval.closed(1, 5), Interval.closed(5, 10));
    }

    @Test
    void shouldThrowWhenIntervalsHaveGapDueToExclusiveBoundaries() {
      // given - [1, 5) and (5, 10] have a gap at point 5
      final var interval = Interval.closed(1, 10);
      final var intervals = List.of(Interval.closedOpen(1, 5), Interval.openClosed(5, 10));

      // when/then
      assertThatThrownBy(() -> interval.smallestCover(intervals))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Expected intervals to be contiguous");
    }

    @Test
    void shouldSkipNonOverlappingIntervalsAtBeginning() {
      // given - [1, 5), [5, 10), [10, 15] are contiguous
      // query [7, 12] only overlaps with [5, 10) and [10, 15]
      final var query = Interval.closed(7, 12);
      final var intervals =
          List.of(Interval.closedOpen(1, 5), Interval.closedOpen(5, 10), Interval.closed(10, 15));

      // when
      final var result = query.smallestCover(intervals);

      // then - should skip [1, 5) and return [5, 10), [10, 15]
      assertThat(result).containsExactly(Interval.closedOpen(5, 10), Interval.closed(10, 15));
    }

    @Test
    void shouldSkipNonOverlappingIntervalsAtEnd() {
      // given - [1, 5), [5, 10), [10, 15] are contiguous
      // query [2, 7] only overlaps with [1, 5) and [5, 10)
      final var query = Interval.closed(2, 7);
      final var intervals =
          List.of(Interval.closedOpen(1, 5), Interval.closedOpen(5, 10), Interval.closed(10, 15));

      // when
      final var result = query.smallestCover(intervals);

      // then - should return [1, 5), [5, 10) and skip [10, 15]
      assertThat(result).containsExactly(Interval.closedOpen(1, 5), Interval.closedOpen(5, 10));
    }
  }

  @Nested
  class Values {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldReturnStartAndEnd(final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = new Interval<>(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.values()).containsExactly(1, 10);
    }
  }

  @Nested
  class Map {

    @Test
    void shouldMapIntervalToStringType() {
      // given
      final var interval = Interval.closed(1, 5);

      // when
      final var mapped = interval.map(Object::toString);

      // then
      assertThat(mapped).isEqualTo(Interval.closed("1", "5"));
    }
  }

  @Nested
  class Intersection {

    @Test
    void shouldThrowWhenCollectionIsEmpty() {
      assertThatThrownBy(() -> Interval.intersection(List.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot compute intersection of empty collection");
    }

    @Test
    void shouldReturnSinglePointWhenIntervalsMeetAtInclusiveBounds() {
      // [1, 5] ∩ [5, 10] = [5, 5]
      final var result =
          Interval.intersection(List.of(Interval.closed(1, 5), Interval.closed(5, 10)));

      assertThat(result).contains(Interval.closed(5, 5));
    }

    @Test
    void shouldReturnEmptyWhenIntervalsMeetAtExclusiveBounds() {
      // [1, 5) ∩ [5, 10] = ∅
      final var result =
          Interval.intersection(List.of(Interval.closedOpen(1, 5), Interval.closed(5, 10)));

      assertThat(result).isEmpty();
    }

    @Test
    void shouldRespectBoundInclusiveness() {
      // (1, 10] ∩ [5, 15) = [5, 10]
      final var result =
          Interval.intersection(List.of(Interval.openClosed(1, 10), Interval.closedOpen(5, 15)));

      assertThat(result).contains(Interval.closed(5, 10));
    }

    @ParameterizedTest
    @CsvSource({
      // Both exclusive at boundary - empty result
      "true, false,  false, true,  false",
      // First inclusive, second exclusive at 5 - no overlap at 5
      "true, true,   false, true,  false",
      // First exclusive, second inclusive at 5 - no overlap at 5
      "true, false,  true, true,   false",
      // Both inclusive at 5 - has single point result [5, 5]
      "true, true,   true, true,   true"
    })
    void shouldRespectBoundInclusivenessAtBoundary(
        final boolean firstStartIncl,
        final boolean firstEndIncl,
        final boolean secondStartIncl,
        final boolean secondEndIncl,
        final boolean hasResult) {
      // Intervals meeting at point 5
      final var first = new Interval<>(1, firstStartIncl, 5, firstEndIncl);
      final var second = new Interval<>(5, secondStartIncl, 10, secondEndIncl);

      final var result = Interval.intersection(List.of(first, second));

      assertThat(result.isPresent()).isEqualTo(hasResult);
    }
  }

  @Nested
  class ToString {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldFormatWithMathematicalNotation(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = new Interval<>(1, startInclusive, 10, endInclusive);

      // when/then
      final var expected =
          expectedBracket(startInclusive, true) + "1, 10" + expectedBracket(endInclusive, false);
      assertThat(interval.toString()).isEqualTo(expected);
    }
  }
}
