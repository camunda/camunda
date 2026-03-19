/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.topology;

import static io.camunda.eventbridge.broker.topology.TopologyService.COORDINATOR_KEY;
import static io.camunda.eventbridge.broker.topology.TopologyService.PARTITION_LEADER_KEY_PREFIX;
import static io.camunda.eventbridge.broker.topology.TopologyService.PARTITION_LEADER_KEY_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.utils.net.Address;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TopologyService}.
 *
 * <p>Exercises the SWIM gossip event handling (MEMBER_ADDED, METADATA_CHANGED, MEMBER_REMOVED) and
 * the leadership-publication helper used by the broker on startup.
 */
class TopologyServiceTest {

  private static final String LOCAL_MEMBER_ID = "broker-0";

  private TopologyService service;

  @BeforeEach
  void setUp() {
    service = new TopologyService(LOCAL_MEMBER_ID);
  }

  // -------------------------------------------------------------------------
  // Helpers

  private static Member memberWithPartitionLeader(
      final String memberId, final String host, final int port, final int... partitionIds) {
    final Member member = mock(Member.class);
    when(member.id()).thenReturn(MemberId.from(memberId));
    when(member.address()).thenReturn(Address.from(host, port));
    final Properties props = new Properties();
    for (final int pid : partitionIds) {
      props.put(PARTITION_LEADER_KEY_PREFIX + pid + PARTITION_LEADER_KEY_SUFFIX, memberId);
    }
    when(member.properties()).thenReturn(props);
    return member;
  }

  private static Member memberWithCoordinator(
      final String memberId, final String host, final int port) {
    final Member member = mock(Member.class);
    when(member.id()).thenReturn(MemberId.from(memberId));
    when(member.address()).thenReturn(Address.from(host, port));
    final Properties props = new Properties();
    props.put(COORDINATOR_KEY, memberId);
    when(member.properties()).thenReturn(props);
    return member;
  }

  private static Member emptyMember(final String memberId, final String host, final int port) {
    final Member member = mock(Member.class);
    when(member.id()).thenReturn(MemberId.from(memberId));
    when(member.address()).thenReturn(Address.from(host, port));
    when(member.properties()).thenReturn(new Properties());
    return member;
  }

  private static ClusterMembershipEvent event(final Type type, final Member member) {
    return new ClusterMembershipEvent(type, member);
  }

  // -------------------------------------------------------------------------

  @Nested
  class GetLeaderAddress {

    @Test
    void shouldReturnEmptyWhenNoLeaderKnown() {
      // given / when / then
      assertThat(service.getLeaderAddress(0)).isEmpty();
      assertThat(service.getLeaderAddress(42)).isEmpty();
    }

    @Test
    void shouldReturnLeaderAddressAfterMemberAdded() {
      // given
      final var member = memberWithPartitionLeader("broker-0", "localhost", 26501, 0, 1);

      // when
      service.event(event(Type.MEMBER_ADDED, member));

      // then
      assertThat(service.getLeaderAddress(0)).contains(Address.from("localhost", 26501));
      assertThat(service.getLeaderAddress(1)).contains(Address.from("localhost", 26501));
    }

    @Test
    void shouldUpdateLeaderAddressOnMetadataChanged() {
      // given — initially broker-0 is leader for partition 0
      final var broker0 = memberWithPartitionLeader("broker-0", "host-a", 26501, 0);
      service.event(event(Type.MEMBER_ADDED, broker0));

      // when — broker-1 announces leadership for partition 0 (after a re-election)
      final var broker1 = memberWithPartitionLeader("broker-1", "host-b", 26501, 0);
      service.event(event(Type.METADATA_CHANGED, broker1));

      // then
      assertThat(service.getLeaderAddress(0)).contains(Address.from("host-b", 26501));
    }

    @Test
    void shouldClearLeaderEntryOnMemberRemoved() {
      // given
      final var member = memberWithPartitionLeader("broker-0", "localhost", 26501, 0, 1);
      service.event(event(Type.MEMBER_ADDED, member));

      // when
      service.event(event(Type.MEMBER_REMOVED, member));

      // then
      assertThat(service.getLeaderAddress(0)).isEmpty();
      assertThat(service.getLeaderAddress(1)).isEmpty();
    }

    @Test
    void shouldNotClearEntriesForOtherBrokersOnMemberRemoved() {
      // given — two brokers each leading one partition
      final var broker0 = memberWithPartitionLeader("broker-0", "host-a", 26501, 0);
      final var broker1 = memberWithPartitionLeader("broker-1", "host-b", 26501, 1);
      service.event(event(Type.MEMBER_ADDED, broker0));
      service.event(event(Type.MEMBER_ADDED, broker1));

      // when — broker-0 leaves
      service.event(event(Type.MEMBER_REMOVED, broker0));

      // then — partition 0 cleared, partition 1 still known
      assertThat(service.getLeaderAddress(0)).isEmpty();
      assertThat(service.getLeaderAddress(1)).contains(Address.from("host-b", 26501));
    }

    @Test
    void shouldIgnoreMalformedPartitionLeaderPropertyKey() {
      // given — property with a non-integer between the prefix and suffix
      final Member member = mock(Member.class);
      when(member.id()).thenReturn(MemberId.from("broker-0"));
      when(member.address()).thenReturn(Address.from("localhost", 26501));
      final Properties props = new Properties();
      props.put("eb.partition.NOT_A_NUMBER.leader", "broker-0");
      when(member.properties()).thenReturn(props);

      // when — no exception should be thrown
      service.event(event(Type.MEMBER_ADDED, member));

      // then — routing table stays empty for all partitions
      assertThat(service.getLeaderAddress(0)).isEmpty();
    }

    @Test
    void shouldIgnoreReachabilityChangedEvents() {
      // given — a member was previously known
      final var member = memberWithPartitionLeader("broker-0", "localhost", 26501, 0);
      service.event(event(Type.MEMBER_ADDED, member));

      // when — REACHABILITY_CHANGED fires (member still connected but degraded)
      service.event(event(Type.REACHABILITY_CHANGED, member));

      // then — routing table is unchanged
      assertThat(service.getLeaderAddress(0)).contains(Address.from("localhost", 26501));
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class GetCoordinatorAddress {

    @Test
    void shouldReturnEmptyWhenNoCoordinatorKnown() {
      assertThat(service.getCoordinatorAddress()).isEmpty();
    }

    @Test
    void shouldReturnCoordinatorAddressAfterMemberAdded() {
      // given
      final var coordinator = memberWithCoordinator("broker-0", "localhost", 26501);

      // when
      service.event(event(Type.MEMBER_ADDED, coordinator));

      // then
      assertThat(service.getCoordinatorAddress()).contains(Address.from("localhost", 26501));
    }

    @Test
    void shouldUpdateCoordinatorOnMetadataChanged() {
      // given
      final var coordinator = memberWithCoordinator("broker-0", "host-a", 26501);
      service.event(event(Type.MEMBER_ADDED, coordinator));

      // when — coordinator moves to a new address (e.g., restart)
      final var newCoordinator = memberWithCoordinator("broker-0", "host-b", 26501);
      service.event(event(Type.METADATA_CHANGED, newCoordinator));

      // then
      assertThat(service.getCoordinatorAddress()).contains(Address.from("host-b", 26501));
    }

    @Test
    void shouldClearCoordinatorAddressOnMemberRemoved() {
      // given
      final var coordinator = memberWithCoordinator("broker-0", "localhost", 26501);
      service.event(event(Type.MEMBER_ADDED, coordinator));

      // when
      service.event(event(Type.MEMBER_REMOVED, coordinator));

      // then
      assertThat(service.getCoordinatorAddress()).isEmpty();
    }

    @Test
    void shouldNotClearCoordinatorWhenDifferentMemberRemoved() {
      // given — coordinator and an unrelated member
      final var coordinator = memberWithCoordinator("broker-0", "host-a", 26501);
      final var other = emptyMember("broker-1", "host-b", 26501);
      service.event(event(Type.MEMBER_ADDED, coordinator));
      service.event(event(Type.MEMBER_ADDED, other));

      // when — unrelated member leaves
      service.event(event(Type.MEMBER_REMOVED, other));

      // then — coordinator address is still known
      assertThat(service.getCoordinatorAddress()).contains(Address.from("host-a", 26501));
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class Initialize {

    @Test
    void shouldSeedRoutingTableFromMembershipSnapshot() {
      // given
      final var broker0 = memberWithPartitionLeader("broker-0", "host-a", 26501, 0);
      final var broker1 = memberWithPartitionLeader("broker-1", "host-b", 26501, 1);
      final var coordinator = memberWithCoordinator("broker-0", "host-a", 26501);

      // broker-0 is both leader for partition 0 and the coordinator
      final Member combined = mock(Member.class);
      when(combined.id()).thenReturn(MemberId.from("broker-0"));
      when(combined.address()).thenReturn(Address.from("host-a", 26501));
      final Properties combinedProps = new Properties();
      combinedProps.put("eb.partition.0.leader", "broker-0");
      combinedProps.put(COORDINATOR_KEY, "broker-0");
      when(combined.properties()).thenReturn(combinedProps);

      final var membershipService = mock(ClusterMembershipService.class);
      when(membershipService.getMembers()).thenReturn(Set.of(combined, broker1));

      // when
      service.initialize(membershipService);

      // then
      assertThat(service.getLeaderAddress(0)).contains(Address.from("host-a", 26501));
      assertThat(service.getLeaderAddress(1)).contains(Address.from("host-b", 26501));
      assertThat(service.getCoordinatorAddress()).contains(Address.from("host-a", 26501));
    }

    @Test
    void shouldHandleEmptyMembershipSnapshot() {
      // given
      final var membershipService = mock(ClusterMembershipService.class);
      when(membershipService.getMembers()).thenReturn(Set.of());

      // when — no exception expected
      service.initialize(membershipService);

      // then
      assertThat(service.getLeaderAddress(0)).isEmpty();
      assertThat(service.getCoordinatorAddress()).isEmpty();
    }
  }
}
