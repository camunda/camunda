/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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

    @ParameterizedTest
    @CsvSource({"false, true", "true, false", "false, false"})
    void shouldThrowExceptionWhenPointIntervalWithExtremesExcluded(
        final boolean startInclusive, final boolean endInclusive) {
      assertThatThrownBy(() -> new Interval<>(1, startInclusive, 1, endInclusive))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Interval with equal bounds must be inclusive on both sides");
    }

    @Test
    void shouldAllowCreationOfClosedPointInterval() {
      assertThatNoException().isThrownBy(() -> Interval.closed(1, 1));
    }
  }

  @Nested
  class ContainsValueWithMapper {

    @Test
    void shouldContainMappedValueInMiddle() {
      final var interval = Interval.closed(new Event("start", 100L), new Event("end", 200L));

      assertThat(interval.contains(150L, Event::timestamp)).isTrue();
    }

    @Test
    void shouldNotContainMappedValueOutside() {
      final var interval = Interval.closed(new Event("start", 100L), new Event("end", 200L));

      assertThat(interval.contains(50L, Event::timestamp)).isFalse();
      assertThat(interval.contains(250L, Event::timestamp)).isFalse();
    }

    @Test
    void shouldRespectStartInclusiveness() {
      final var closedInterval = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      final var openInterval = Interval.open(new Event("start", 100L), new Event("end", 200L));

      assertThat(closedInterval.contains(100L, Event::timestamp)).isTrue();
      assertThat(openInterval.contains(100L, Event::timestamp)).isFalse();
    }

    @Test
    void shouldRespectEndInclusiveness() {
      final var closedInterval = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      final var openInterval = Interval.open(new Event("start", 100L), new Event("end", 200L));

      assertThat(closedInterval.contains(200L, Event::timestamp)).isTrue();
      assertThat(openInterval.contains(200L, Event::timestamp)).isFalse();
    }

    @Test
    void shouldWorkWithDifferentMapperTypes() {
      final var interval = Interval.closed(new Event("alpha", 100L), new Event("zeta", 200L));

      // Map to String for comparison (different from the Comparable<Event> implementation)
      assertThat(interval.contains("beta", Event::name)).isTrue();
      assertThat(interval.contains("aaa", Event::name)).isFalse();
    }

    // Event is Comparable by timestamp, but we can also compare by other fields using mapper
    record Event(String name, long timestamp) implements Comparable<Event> {
      @Override
      public int compareTo(final Event other) {
        return Long.compare(timestamp, other.timestamp);
      }
    }
  }

  @Nested
  class ContainsIntervalWithMapper {

    @Test
    void shouldContainSmallerMappedInterval() {
      final var outer = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      final var inner = Interval.closed(120L, 180L);

      assertThat(outer.contains(inner, Event::timestamp)).isTrue();
    }

    @Test
    void shouldNotContainLargerMappedInterval() {
      final var outer = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      final var inner = Interval.closed(50L, 250L);

      assertThat(outer.contains(inner, Event::timestamp)).isFalse();
    }

    @Test
    void shouldRespectBoundInclusiveness() {
      // Closed interval contains open interval with same bounds
      final var closed = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      assertThat(closed.contains(Interval.open(100L, 200L), Event::timestamp)).isTrue();

      // Open interval does not contain closed interval with same bounds
      final var open = Interval.open(new Event("start", 100L), new Event("end", 200L));
      assertThat(open.contains(Interval.closed(100L, 200L), Event::timestamp)).isFalse();
    }

    @Test
    void shouldContainEqualMappedInterval() {
      final var interval = Interval.closed(new Event("start", 100L), new Event("end", 200L));
      final var other = Interval.closed(100L, 200L);

      assertThat(interval.contains(other, Event::timestamp)).isTrue();
    }

    record Event(String name, long timestamp) implements Comparable<Event> {
      @Override
      public int compareTo(final Event other) {
        return Long.compare(timestamp, other.timestamp);
      }
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
    void shouldReturnEmptyListForEmptyInput() {
      // given
      final var interval = Interval.closed(1, 10);

      // when
      final var result = interval.smallestCover(List.of());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForSinglePointNotCoveringInterval() {
      // given - single point at 5 can't cover [1, 10] (nothing at-or-after 10)
      final var interval = Interval.closed(1, 10);

      assertThat(interval.smallestCover(List.of(5))).isEmpty();
    }

    @Test
    void shouldReturnSinglePointForPointInterval() {
      // given - single point at 5 covers [5, 5]
      final var interval = Interval.closed(5, 5);

      assertThat(interval.smallestCover(List.of(5))).containsExactly(5);
    }

    @Test
    void shouldReturnAllPointsWithinInterval() {
      // given - [3, 7] with points [1, 3, 5, 7, 9]
      final var interval = Interval.closed(3, 7);

      // when
      final var result = interval.smallestCover(List.of(1, 3, 5, 7, 9));

      // then - 3, 5, 7 are within [3, 7]; no neighbor needed since exact matches at boundaries
      assertThat(result).containsExactly(3, 5, 7);
    }

    @Test
    void shouldIncludeLastBeforeWhenNoExactStartMatch() {
      // given
      final var interval = Interval.closed(4, 6);
      final var points = List.of(1, 3, 5, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - 3 is last before 4, 5 is within, 7 is first after 6
      assertThat(result).containsExactly(3, 5, 7);
    }

    @Test
    void shouldIncludeFirstAfterWhenNoExactEndMatch() {
      // given
      final var interval = Interval.closed(3, 6);
      final var points = List.of(1, 3, 5, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - 3 and 5 are within, 7 is first after 6
      assertThat(result).containsExactly(3, 5, 7);
    }

    @Test
    void shouldIncludeBothNeighborsWhenNoExactBoundaryMatches() {
      // given
      final var interval = Interval.closed(4, 8);
      final var points = List.of(1, 3, 5, 7, 9, 11);
      // when
      final var result = interval.smallestCover(points);

      // then - 3 is last before 4, 5 and 7 are within, 9 is first after 8
      assertThat(result).containsExactly(3, 5, 7, 9);
    }

    @Test
    void shouldNotIncludeNeighborWhenExactMatchAtStart() {
      // given
      final var interval = Interval.closed(3, 8);
      final var points = List.of(1, 3, 5, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - 3, 5, 7 within; 9 is first after 8 (no match at end)
      assertThat(result).containsExactly(3, 5, 7, 9);
    }

    @Test
    void shouldNotIncludeNeighborWhenExactMatchAtEnd() {
      // given
      final var interval = Interval.closed(4, 7);
      final var points = List.of(1, 3, 5, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - 3 is last before 4, 5 and 7 are within; no neighbor needed at end
      assertThat(result).containsExactly(3, 5, 7);
    }

    @Test
    void shouldReturnAllPointsWhenIntervalCoversAll() {
      // given
      final var interval = Interval.closed(1, 9);
      final var points = List.of(1, 3, 5, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - all points are within, exact matches at both boundaries
      assertThat(result).containsExactly(1, 3, 5, 7, 9);
    }

    @Test
    void shouldReturnEmptyWhenAllPointsAreBeforeInterval() {
      // given - all points before interval, no coverage at end
      final var interval = Interval.closed(10, 20);
      final var points = List.of(1, 3, 5);

      // when
      final var result = interval.smallestCover(points);

      // then - no point at-or-after end, so no valid cover
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAllPointsAreAfterInterval() {
      // given - all points after interval, no coverage at start
      final var interval = Interval.closed(1, 5);
      final var points = List.of(10, 15, 20);

      // when
      final var result = interval.smallestCover(points);

      // then - no point at-or-before start, so no valid cover
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnBothNeighborsWhenNoPointsWithin() {
      // given - [4, 6] with points [1, 3, 7, 9] - no points within the interval
      final var interval = Interval.closed(4, 6);
      final var points = List.of(1, 3, 7, 9);

      // when
      final var result = interval.smallestCover(points);

      // then - 3 is last before 4, 7 is first after 6
      assertThat(result).containsExactly(3, 7);
    }

    @Test
    void shouldThrowWhenPointsAreNotSorted() {
      final var interval = Interval.closed(1, 10);
      final var unsorted = List.of(1, 5, 3, 7, 9);

      assertThatThrownBy(() -> interval.smallestCover(unsorted))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly sorted in ascending order with no duplicates");
    }

    @Test
    void shouldThrowWhenPointsContainDuplicates() {
      final var interval = Interval.closed(1, 10);
      final var duplicates = List.of(1, 3, 3, 7, 10);

      assertThatThrownBy(() -> interval.smallestCover(duplicates))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly sorted in ascending order with no duplicates");
    }
  }

  @Nested
  class SmallestCoverWithMapper {

    @Test
    void shouldReturnOriginalPointsWhenMapped() {
      // given - events with timestamps, query by Long timestamp
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var event3 = new Event("c", 300L);
      final var events = List.of(event1, event2, event3);

      final var query = Interval.closed(100L, 300L);

      // when
      final var result = query.smallestCover(events, Event::timestamp);

      // then - all events within range, returns original Event objects
      assertThat(result).containsExactly(event1, event2, event3);
      assertThat(result.getFirst().name()).isEqualTo("a");
      assertThat(result.getLast().name()).isEqualTo("c");
    }

    @Test
    void shouldIncludeLastBeforeWhenNoExactStartMatch() {
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var event3 = new Event("c", 300L);
      final var event4 = new Event("d", 400L);
      final var events = List.of(event1, event2, event3, event4);

      // Query starts at 150, no exact match -> include last before (event1 at 100)
      final var query = Interval.closed(150L, 300L);

      final var result = query.smallestCover(events, Event::timestamp);

      assertThat(result).containsExactly(event1, event2, event3);
    }

    @Test
    void shouldIncludeFirstAfterWhenNoExactEndMatch() {
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var event3 = new Event("c", 300L);
      final var event4 = new Event("d", 400L);
      final var events = List.of(event1, event2, event3, event4);

      // Query ends at 250, no exact match -> include first after (event3 at 300)
      final var query = Interval.closed(100L, 250L);

      final var result = query.smallestCover(events, Event::timestamp);

      assertThat(result).containsExactly(event1, event2, event3);
    }

    @Test
    void shouldIncludeBothNeighborsWhenNoExactBoundaryMatches() {
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var event3 = new Event("c", 300L);
      final var event4 = new Event("d", 400L);
      final var events = List.of(event1, event2, event3, event4);

      // Query [150, 350] - no exact match at either boundary
      final var query = Interval.closed(150L, 350L);

      final var result = query.smallestCover(events, Event::timestamp);

      // event1 is last before 150, event2/event3 are within, event4 is first after 350
      assertThat(result).containsExactly(event1, event2, event3, event4);
    }

    @Test
    void shouldReturnBothNeighborsWhenNoPointsWithin() {
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var event3 = new Event("c", 300L);
      final var events = List.of(event1, event2, event3);

      // Query [210, 290] - only event at 200 is before, event at 300 is after
      final var query = Interval.closed(210L, 290L);

      final var result = query.smallestCover(events, Event::timestamp);

      assertThat(result).containsExactly(event2, event3);
    }

    @Test
    void shouldReturnEmptyWhenPointsCannotCoverInterval() {
      final var event1 = new Event("a", 100L);
      final var event2 = new Event("b", 200L);
      final var events = List.of(event1, event2);

      // Query [300, 400] - all events are before the interval, no coverage at end
      final var query = Interval.closed(300L, 400L);

      final var result = query.smallestCover(events, Event::timestamp);

      assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowWhenMappedValuesAreNotSorted() {
      final var event1 = new Event("a", 300L);
      final var event2 = new Event("b", 100L);
      final var events = List.of(event1, event2);

      final var query = Interval.closed(100L, 300L);

      assertThatThrownBy(() -> query.smallestCover(events, Event::timestamp))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("strictly sorted in ascending order with no duplicates");
    }

    record Event(String name, long timestamp) implements Comparable<Event> {
      @Override
      public int compareTo(final Event other) {
        return Long.compare(timestamp, other.timestamp);
      }
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
