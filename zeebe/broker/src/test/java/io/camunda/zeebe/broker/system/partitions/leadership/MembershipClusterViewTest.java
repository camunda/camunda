/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MembershipClusterViewTest {

  private final ClusterMembershipService membership = mock(ClusterMembershipService.class);
  private final MembershipClusterView view =
      new MembershipClusterView(membership, BrokerMemberId.from("east", 0));

  @Test
  void shouldSeeTheReachableBrokersOfItsOwnRegion() {
    // given
    final var members =
        Set.of(
            broker("east", 1, true),
            broker("east", 2, false),
            broker("west", 0, true),
            gateway("east-gateway"));
    when(membership.getMembers()).thenReturn(members);

    // then - itself, but not the unreachable broker, the other region, or the gateway
    assertThat(view.liveNodesInMyZone()).containsExactlyInAnyOrder("east_0", "east_1");
  }

  @Test
  void shouldAlwaysSeeItself() {
    // given
    when(membership.getMembers()).thenReturn(Set.of());

    // then
    assertThat(view.liveNodesInMyZone()).containsExactly("east_0");
    assertThat(view.isAlive("east_0")).isTrue();
  }

  private static Member broker(final String zone, final int nodeId, final boolean reachable) {
    final var properties = new Properties();
    new BrokerInfo(nodeId, zone, "localhost:26501")
        .writeIntoProperties(properties, BrokerInfo.brokerInfoPropertyName("default"));
    return member(MemberId.from(zone, nodeId), properties, reachable);
  }

  private static Member gateway(final String id) {
    return member(MemberId.from(id), new Properties(), true);
  }

  private static Member member(
      final MemberId id, final Properties properties, final boolean reachable) {
    final var member = mock(Member.class);
    when(member.id()).thenReturn(id);
    when(member.properties()).thenReturn(properties);
    when(member.isReachable()).thenReturn(reachable);
    return member;
  }
}
