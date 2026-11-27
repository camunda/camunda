/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.system.monitoring.history.HealthHistory;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ClusterMembershipDetectorTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

  private final ClusterMembershipService membership = mock(ClusterMembershipService.class);
  private final HealthHistory history =
      new HealthHistory("Broker-0", 100, null, InstantSource.fixed(NOW));

  @Test
  void shouldReadReachabilityFromTheLiveMemberNotTheEventCopy() {
    // given
    final var local = member("0", true);
    final var live = member("1", true);
    when(membership.getLocalMember()).thenReturn(local);
    when(membership.getMembers()).thenReturn(Set.of(local));
    when(membership.getMember(live.id())).thenReturn(live);
    final var detector =
        new ClusterMembershipDetector(membership, history, InstantSource.fixed(NOW));

    // when - like SWIM's event copies, the subject never reports itself reachable
    detector.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, member("1", false)));

    // then
    assertThat(detector.getHealthReport().children().get("Member-1").status())
        .isEqualTo(HealthStatus.HEALTHY);
    assertThat(history.entries()).extracting("type").containsExactly("MEMBER_JOINED");
  }

  @Test
  void shouldMarkAMemberDegradedWhileItIsUnreachable() {
    // given
    final var local = member("0", true);
    final var peer = member("1", true);
    when(membership.getLocalMember()).thenReturn(local);
    when(membership.getMembers()).thenReturn(Set.of(local, peer));
    final var detector =
        new ClusterMembershipDetector(membership, history, InstantSource.fixed(NOW));
    when(membership.getMember(peer.id())).thenReturn(member("1", false));

    // when
    detector.event(new ClusterMembershipEvent(Type.REACHABILITY_CHANGED, peer));

    // then
    final var report = detector.getHealthReport().children().get("Member-1");
    assertThat(report.status()).isEqualTo(HealthStatus.DEGRADED);
    assertThat(report.issue().message()).isEqualTo("member 1 at localhost:26512 is unreachable");
  }

  private static Member member(final String id, final boolean reachable) {
    return new Member(
        Member.builder(MemberId.from(id))
            .withAddress(Address.from("localhost", 26512))
            .build()
            .config()) {
      @Override
      public boolean isReachable() {
        return reachable;
      }
    };
  }
}
