/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.impl.job.ActivationPlanner.Ask;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ActivationPlannerTest {

  @Test
  void shouldSplitRemainingAmountOverAllPartitionsFirst() {
    // given
    final var planner = new ActivationPlanner(10, List.of(1, 2, 3), Set.of());

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactly(new Ask(1, 4), new Ask(2, 3), new Ask(3, 3));
  }

  @Test
  void shouldAskMorePartitionsThanJobsWantedWhenNothingIsKnown() {
    // given
    final var planner = new ActivationPlanner(2, List.of(1, 2, 3), Set.of());

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactly(new Ask(1, 1), new Ask(2, 1), new Ask(3, 1));
  }

  @Test
  void shouldLimitFanOutWhenNoPartitionIsKnownToHaveJobs() {
    // given
    final var partitions = IntStream.rangeClosed(1, 20).boxed().toList();
    final var planner = new ActivationPlanner(2, partitions, Set.of());

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).hasSize(ActivationPlanner.UNKNOWN_FAN_OUT);
    assertThat(asks).allMatch(ask -> ask.maxJobs() == 1);
  }

  @Test
  void shouldNotAskMoreThanJobsWantedWhenPartitionsAreHinted() {
    // given
    final var planner = new ActivationPlanner(1, List.of(1, 2, 3), Set.of(3));

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactly(new Ask(3, 1));
  }

  @Test
  void shouldAskNothingWhileAsksInFlightCoverTheRemainingAmount() {
    // given
    final var planner = new ActivationPlanner(6, List.of(1, 2), Set.of());
    final var asks = planner.nextAsks();

    // when
    planner.onResponse(asks.get(0), 3, 3, false);

    // then
    assertThat(planner.nextAsks()).isEmpty();
    assertThat(planner.hasInFlightAsks()).isTrue();
  }

  @Test
  void shouldAskAgainWithoutWaitingForOtherPartitionsOnceTheyRevealFreeBudget() {
    // given
    final var planner = new ActivationPlanner(9, List.of(1, 2, 3), Set.of());
    final var asks = planner.nextAsks();
    planner.onResponse(asks.get(1), 3, 3, false);
    assertThat(planner.nextAsks()).isEmpty();

    // when partition 1 turns out to be empty while partition 3 is still pending
    planner.onResponse(asks.get(0), 0, 0, false);

    // then the budget it released goes to the partition that is known to have jobs
    assertThat(planner.nextAsks()).containsExactly(new Ask(2, 3));
  }

  @Test
  void shouldNotAskPartitionsThatReturnedLessThanAsked() {
    // given
    final var planner = new ActivationPlanner(9, List.of(1, 2, 3), Set.of());
    final var asks = planner.nextAsks();
    planner.onResponse(asks.get(0), 3, 3, false);
    planner.onResponse(asks.get(1), 1, 1, false);
    planner.onResponse(asks.get(2), 0, 0, false);

    // when
    final var next = planner.nextAsks();

    // then
    assertThat(next).containsExactly(new Ask(1, 5));
  }

  @Test
  void shouldTreatTruncatedResponseAsMayHaveMore() {
    // given
    final var planner = new ActivationPlanner(10, List.of(1, 2), Set.of());
    final var asks = planner.nextAsks();

    // when
    final var truncatedMayHaveMore = planner.onResponse(asks.get(0), 2, 2, true);
    planner.onResponse(asks.get(1), 0, 0, false);

    // then
    assertThat(truncatedMayHaveMore).isTrue();
    assertThat(planner.nextAsks()).containsExactly(new Ask(1, 8));
  }

  @Test
  void shouldMoveOnToUnexploredPartitionsWhenFanOutDoesNotCoverAllPartitions() {
    // given
    final var partitions = IntStream.rangeClosed(1, 10).boxed().toList();
    final var planner = new ActivationPlanner(1, partitions, Set.of());
    planner.nextAsks().forEach(ask -> planner.onResponse(ask, 0, 0, false));

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactly(new Ask(9, 1), new Ask(10, 1));
  }

  @Test
  void shouldAskHintedPartitionForMostJobsAndProbeTheOthers() {
    // given
    final var planner = new ActivationPlanner(9, List.of(1, 2, 3), Set.of(2));

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactlyInAnyOrder(new Ask(2, 7), new Ask(1, 1), new Ask(3, 1));
  }

  @Test
  void shouldOnlyAskHintedPartitionsWhenFewJobsAreWanted() {
    // given
    final var planner = new ActivationPlanner(2, List.of(1, 2, 3), Set.of(2, 3));

    // when
    final var asks = planner.nextAsks();

    // then
    assertThat(asks).containsExactly(new Ask(2, 1), new Ask(3, 1));
  }

  @Test
  void shouldStopWhenAllPartitionsAreDrained() {
    // given
    final var planner = new ActivationPlanner(4, List.of(1, 2), Set.of(2));
    final var asks = planner.nextAsks();

    // when
    planner.onResponse(asks.get(0), 0, 0, false);
    planner.onResponse(asks.get(1), 0, 0, false);

    // then
    assertThat(planner.nextAsks()).isEmpty();
    assertThat(planner.hasInFlightAsks()).isFalse();
    assertThat(planner.remaining()).isEqualTo(4);
  }

  @Test
  void shouldNeverAskAgainAfterFailure() {
    // given
    final var planner = new ActivationPlanner(4, List.of(1, 2), Set.of());
    final var asks = planner.nextAsks();
    planner.onFailure(asks.get(0));
    planner.onResponse(asks.get(1), 2, 2, false);

    // then
    assertThat(planner.nextAsks()).containsExactly(new Ask(2, 2));
  }
}
