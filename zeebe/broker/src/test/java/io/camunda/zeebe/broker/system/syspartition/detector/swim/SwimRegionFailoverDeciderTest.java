/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_A;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.REGION_B;
import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionLayout;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverDecision.CommittedFailover;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SwimRegionFailoverDeciderTest {
  private static final long NOW = 1_000_000;

  private final SwimRegionFailoverDecider decider =
      new SwimRegionFailoverDecider(RegionLayout.of(threeByThreeByOne(5)));

  @Test
  void shouldFailOverFromGoneRegionToHealthyRegion() {
    // given - every broker of region A is gone, the data-only one still suspected
    final var views =
        views(
            dead("region-a_0", 61_000, 3),
            dead("region-a_1", 59_000, 3),
            suspect("region-a_2", 12_000, 2));

    // when
    final var failover = decider.decide(views, "region-b_0", 7, NOW, null, Set.of());

    // then
    assertThat(failover)
        .hasValueSatisfying(
            decision -> {
              assertThat(decision.failedRegion()).isEqualTo(REGION_A);
              assertThat(decision.targetRegion()).isEqualTo(REGION_B);
              assertThat(decision.leaderId()).isEqualTo("region-b_0");
              assertThat(decision.term()).isEqualTo(7);
              assertThat(decision.decidedAtMillis()).isEqualTo(NOW);
              assertThat(decision.detectedAtMillis()).isEqualTo(NOW);
              assertThat(decision.goneBrokers())
                  .containsExactly(
                      new GoneBroker("region-a_0", 61_000, 3),
                      new GoneBroker("region-a_1", 59_000, 3),
                      new GoneBroker("region-a_2", 12_000, 2));
            });
  }

  @Test
  void shouldNotListBrokersOfTheFailedRegionThatAreStillSeen() {
    // given - the data-only broker of region A is still seen, the system partition ones are gone
    final var views = views(dead("region-a_0", 40_000, 3), dead("region-a_1", 40_000, 3));

    // when
    final var failover = decider.decide(views, "region-b_0", 1, NOW, null, Set.of());

    // then
    assertThat(failover)
        .hasValueSatisfying(
            decision ->
                assertThat(decision.goneBrokers())
                    .extracting(GoneBroker::memberId)
                    .containsExactly("region-a_0", "region-a_1"));
  }

  @Test
  void shouldNotFailOverWhileRegionHasABrokerSeen() {
    // given
    final var views = views(dead("region-a_0", 40_000, 3));

    // when - then
    assertThat(decider.decide(views, "region-b_0", 1, NOW, null, Set.of())).isEmpty();
  }

  @Test
  void shouldNotFailOverWhileGoneBrokersAreOnlySuspected() {
    // given - not yet gone for the threshold, or not confirmed by enough observers
    final var views = views(suspect("region-a_0", 10_000, 3), suspect("region-a_1", 10_000, 3));

    // when - then
    assertThat(decider.decide(views, "region-b_0", 1, NOW, null, Set.of())).isEmpty();
  }

  @Test
  void shouldNotFailOverToRegionWhichIsNotActive() {
    // given - region A is gone, but a broker of region B is suspected
    final var views =
        views(
            dead("region-a_0", 40_000, 3),
            dead("region-a_1", 40_000, 3),
            suspect("region-b_1", 5_000, 2));

    // when - then
    assertThat(decider.decide(views, "region-b_0", 1, NOW, null, Set.of())).isEmpty();
  }

  @Test
  void shouldNotFailOverWhenEveryDataRegionIsGone() {
    // given
    final var views =
        views(
            dead("region-a_0", 40_000, 3),
            dead("region-a_1", 40_000, 3),
            dead("region-b_0", 40_000, 3),
            dead("region-b_1", 40_000, 3));

    // when - then
    assertThat(decider.decide(views, "control_0", 1, NOW, null, Set.of())).isEmpty();
  }

  @Test
  void shouldNotFailOverWhenOnlyControlRegionIsGone() {
    // given - the control region hosts no data
    final var views = views(dead("control_0", 40_000, 3));

    // when - then
    assertThat(decider.decide(views, "region-a_0", 1, NOW, null, Set.of())).isEmpty();
  }

  @Test
  void shouldFailOverToTheHealthyRegionWithTheHighestPriority() {
    // given - region B is gone, and region A has the higher priority
    final var views = views(dead("region-b_0", 40_000, 3), dead("region-b_1", 40_000, 3));

    // when
    final var failover = decider.decide(views, "region-a_0", 1, NOW, null, Set.of());

    // then
    assertThat(failover)
        .hasValueSatisfying(
            decision -> {
              assertThat(decision.failedRegion()).isEqualTo(REGION_B);
              assertThat(decision.targetRegion()).isEqualTo(REGION_A);
            });
  }

  @Test
  void shouldNotFailOverAgainUntilTheRegionWasSeenAgain() {
    // given - region A already failed over, and is still gone
    final var views = views(dead("region-a_0", 90_000, 3), dead("region-a_1", 90_000, 3));
    final var previous = new CommittedFailover(REGION_A, REGION_B, NOW - 60_000);

    // when - then
    assertThat(decider.decide(views, "region-b_0", 1, NOW, previous, Set.of())).isEmpty();
    assertThat(decider.decide(views, "region-b_0", 1, NOW, previous, Set.of(REGION_A)))
        .hasValueSatisfying(decision -> assertThat(decision.failedRegion()).isEqualTo(REGION_A));
  }

  @Test
  void shouldFailOverAgainFromAnotherRegionThanThePreviousFailover() {
    // given - region A failed over earlier; now region B is gone, and A is healthy again
    final var views = views(dead("region-b_0", 40_000, 3), dead("region-b_1", 40_000, 3));
    final var previous = new CommittedFailover(REGION_A, REGION_B, NOW - 600_000);

    // when
    final var failover = decider.decide(views, "region-a_0", 1, NOW, previous, Set.of());

    // then
    assertThat(failover)
        .hasValueSatisfying(decision -> assertThat(decision.failedRegion()).isEqualTo(REGION_B));
  }

  @Test
  void shouldNeverFailOverWithoutRegions() {
    // given
    final var decider = new SwimRegionFailoverDecider(RegionLayout.NONE);

    // when - then
    assertThat(decider.decide(Map.of(), "0", 1, NOW, null, Set.of())).isEmpty();
  }

  /** Every broker healthy, except for the given ones. */
  private static Map<String, BrokerView> views(final BrokerView... exceptions) {
    final var views = new HashMap<String, BrokerView>();
    for (final var broker : RegionLayout.of(threeByThreeByOne(5)).allBrokers()) {
      views.put(broker, new BrokerView(broker, MemberLivenessState.HEALTHY, 0, 0, 1));
    }
    for (final var exception : exceptions) {
      views.put(exception.memberId(), exception);
    }
    return views;
  }

  private static BrokerView dead(final String broker, final long goneFor, final int confirmations) {
    return new BrokerView(broker, MemberLivenessState.DEAD, goneFor, confirmations, 0);
  }

  private static BrokerView suspect(
      final String broker, final long goneFor, final int confirmations) {
    return new BrokerView(broker, MemberLivenessState.SUSPECT, goneFor, confirmations, 0);
  }
}
