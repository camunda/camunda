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

  private static Interval<Integer> createInterval(
      final int start, final boolean startInclusive, final int end, final boolean endInclusive) {
    return new Interval<>(start, startInclusive, end, endInclusive);
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

      // then
      assertThat(interval.start()).isEqualTo(5);
      assertThat(interval.end()).isEqualTo(5);
      assertThat(interval.startInclusive()).isTrue();
      assertThat(interval.endInclusive()).isTrue();
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
    void shouldCreateIntervalWithCorrectInclusiveness(
        final boolean startInclusive, final boolean endInclusive) {
      // when
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // then
      assertThat(interval.start()).isEqualTo(1);
      assertThat(interval.end()).isEqualTo(10);
      assertThat(interval.startInclusive()).isEqualTo(startInclusive);
      assertThat(interval.endInclusive()).isEqualTo(endInclusive);
    }

    @Test
    void shouldCreateClosedIntervalUsingFactoryMethod() {
      final var interval = Interval.closed(1, 10);
      assertThat(interval).isEqualTo(new Interval<>(1, true, 10, true));
    }

    @Test
    void shouldCreateOpenIntervalUsingFactoryMethod() {
      final var interval = Interval.open(1, 10);
      assertThat(interval).isEqualTo(new Interval<>(1, false, 10, false));
    }

    @Test
    void shouldCreateClosedOpenIntervalUsingFactoryMethod() {
      final var interval = Interval.closedOpen(1, 10);
      assertThat(interval).isEqualTo(new Interval<>(1, true, 10, false));
    }

    @Test
    void shouldCreateOpenClosedIntervalUsingFactoryMethod() {
      final var interval = Interval.openClosed(1, 10);
      assertThat(interval).isEqualTo(new Interval<>(1, false, 10, true));
    }
  }

  @Nested
  class EqualsAndHashCode {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldBeEqualAndHaveSameHashCodeWhenIntervalsAreEqual(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval1 = createInterval(1, startInclusive, 10, endInclusive);
      final var interval2 = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval1).isEqualTo(interval2);
      assertThat(interval1.hashCode()).isEqualTo(interval2.hashCode());
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotBeEqualWhenIntervalsHaveDifferentStarts(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval1 = createInterval(1, startInclusive, 10, endInclusive);
      final var interval2 = createInterval(2, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval1).isNotEqualTo(interval2);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotBeEqualWhenIntervalsHaveDifferentEnds(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval1 = createInterval(1, startInclusive, 10, endInclusive);
      final var interval2 = createInterval(1, startInclusive, 11, endInclusive);

      // when/then
      assertThat(interval1).isNotEqualTo(interval2);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotBeEqualWhenIntervalsHaveDifferentInclusiveness(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval1 = createInterval(1, startInclusive, 10, endInclusive);
      final var interval2 = createInterval(1, !startInclusive, 10, !endInclusive);

      // when/then
      assertThat(interval1).isNotEqualTo(interval2);
      assertThat(interval1.hashCode()).isNotEqualTo(interval2.hashCode());
    }
  }

  @Nested
  class ContainsValue {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldContainValueInMiddleOfInterval(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(5)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldContainOrExcludeStartBasedOnInclusiveness(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(1)).isEqualTo(startInclusive);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldContainOrExcludeEndBasedOnInclusiveness(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(10)).isEqualTo(endInclusive);
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotContainValueBeforeStart(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(0)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotContainValueAfterEnd(final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(11)).isFalse();
    }
  }

  @Nested
  class ContainsInterval {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldContainSmallerIntervalInside(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);
      final var other = createInterval(3, startInclusive, 7, endInclusive);

      // when/then
      assertThat(interval.contains(other)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldContainEqualInterval(final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);
      final var other = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(other)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotContainLargerInterval(final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(3, startInclusive, 7, endInclusive);
      final var other = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.contains(other)).isFalse();
    }

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
      final var outer = createInterval(1, outerStartIncl, 10, outerEndIncl);
      final var inner = createInterval(1, innerStartIncl, 10, innerEndIncl);

      // when/then
      assertThat(outer.contains(inner)).isEqualTo(expectedContains);
    }
  }

  @Nested
  class OverlapsWith {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldOverlapWhenIntervalsIntersect(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);
      final var other = createInterval(5, startInclusive, 15, endInclusive);

      // when/then
      assertThat(interval.overlapsWith(other)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldOverlapWhenOneContainsOther(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);
      final var other = createInterval(3, startInclusive, 7, endInclusive);

      // when/then
      assertThat(interval.overlapsWith(other)).isTrue();
      assertThat(other.overlapsWith(interval)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldNotOverlapWhenIntervalsAreDisjoint(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 5, endInclusive);
      final var other = createInterval(7, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.overlapsWith(other)).isFalse();
    }

    @Test
    void shouldOverlapWhenBothBoundsInclusiveAtSamePoint() {
      // [1, 10] and [10, 15] share point 10
      assertThat(Interval.closed(1, 10).overlapsWith(Interval.closed(10, 15))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
      // [1, 10) and [10, 15] - first excludes 10
      "true, false,  true, true,  false",
      // [1, 10] and (10, 15] - second excludes 10
      "true, true,   false, true, false",
      // [1, 10) and (10, 15] - both exclude 10
      "true, false,  false, true, false"
    })
    void shouldNotOverlapWhenBoundaryPointIsExcluded(
        final boolean firstStartIncl,
        final boolean firstEndIncl,
        final boolean secondStartIncl,
        final boolean secondEndIncl,
        final boolean expectedOverlap) {
      // given - intervals meeting at point 10
      final var first = createInterval(1, firstStartIncl, 10, firstEndIncl);
      final var second = createInterval(10, secondStartIncl, 15, secondEndIncl);

      // when/then
      assertThat(first.overlapsWith(second)).isEqualTo(expectedOverlap);
    }
  }

  @Nested
  class SmallestCover {

    @Test
    void shouldReturnEmptyListWhenCalledWithEmptyList() {
      // given
      final var interval = Interval.closed(1, 10);

      // when
      final var result = interval.smallestCover(List.of());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlyOverlappingIntervals() {
      // given - interval [5, 15] should overlap with [5, 10) and [10, 20], but not [1, 5)
      final var interval = Interval.closed(5, 15);
      final var intervals =
          List.of(Interval.closedOpen(1, 5), Interval.closedOpen(5, 10), Interval.closed(10, 20));

      // when
      final var result = interval.smallestCover(intervals);

      // then
      assertThat(result).containsExactly(Interval.closedOpen(5, 10), Interval.closed(10, 20));
    }

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
  }

  @Nested
  class Values {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldReturnStartAndEnd(final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      assertThat(interval.values()).containsExactly(1, 10);
    }
  }

  @Nested
  class Map {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldMapIntervalValuesAndPreserveInclusiveness(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when
      final var mapped = interval.map(i -> i * 2L);

      // then
      assertThat(mapped.start()).isEqualTo(2L);
      assertThat(mapped.end()).isEqualTo(20L);
      assertThat(mapped.startInclusive()).isEqualTo(startInclusive);
      assertThat(mapped.endInclusive()).isEqualTo(endInclusive);
    }

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
  class ToString {

    @ParameterizedTest
    @MethodSource("io.camunda.zeebe.backup.api.IntervalTest#inclusivenessVariants")
    void shouldFormatWithMathematicalNotation(
        final boolean startInclusive, final boolean endInclusive) {
      // given
      final var interval = createInterval(1, startInclusive, 10, endInclusive);

      // when/then
      final var expected =
          expectedBracket(startInclusive, true) + "1, 10" + expectedBracket(endInclusive, false);
      assertThat(interval.toString()).isEqualTo(expected);
    }
  }
}
