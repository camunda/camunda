/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.MessageRouter.Route;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ReachabilityTest {

  /** Partition 1 is led from zone a, partition 2 from zone b, partition 3 without a zone. */
  private final LeaderView gossipView =
      partitionId ->
          Optional.ofNullable(
              Map.of(
                      1, new LeaderInfo("a-0", "a"),
                      2, new LeaderInfo("b-0", "b"),
                      3, new LeaderInfo("0", null))
                  .get(partitionId));

  @Test
  void shouldReachAPartitionWhoseLeaderIsInTheGossipView() {
    // given
    final var reachability = new GossipViewReachability(gossipView);

    // when / then
    assertThat(reachability.isReachable(1)).isTrue();
    assertThat(reachability.isReachable(2)).isTrue();
  }

  @Test
  void shouldNotReachAPartitionWhoseLeaderIsUnknown() {
    // given
    final var reachability = new GossipViewReachability(gossipView);

    // when / then
    assertThat(reachability.isReachable(99)).isFalse();
  }

  @Test
  void shouldHideLeadersOfOtherZonesFromAFilteredView() {
    // given
    final var view = new ZoneFilteredLeaderView(gossipView, "a");

    // when / then
    assertThat(view.leaderOf(1)).contains(new LeaderInfo("a-0", "a"));
    assertThat(view.leaderOf(2)).isEmpty();
    assertThat(view.leaderOf(3)).isEmpty();
    assertThat(view.leaderOf(99)).isEmpty();
  }

  @Test
  void shouldRouteByTheFilteredGossipView() {
    // given
    final var router =
        new MessageRouter(new GossipViewReachability(new ZoneFilteredLeaderView(gossipView, "a")));

    // when / then
    assertThat(router.route(1)).isEqualTo(Route.NETWORK);
    assertThat(router.route(2)).isEqualTo(Route.MAILBOX);
    assertThat(router.route(99)).isEqualTo(Route.MAILBOX);
  }

  @Test
  void shouldRouteEverythingOverTheNetworkWhenAllLeadersAreKnown() {
    // given
    final var router = new MessageRouter(new GossipViewReachability(gossipView));

    // when / then
    assertThat(router.route(1)).isEqualTo(Route.NETWORK);
    assertThat(router.route(2)).isEqualTo(Route.NETWORK);
  }
}
