/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.Member;
import io.atomix.raft.RaftServer.Role;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TopologyBroadcaster}.
 *
 * <p>Validates that RAFT role changes are correctly reflected into the local SWIM member's
 * properties, enabling gateway topology discovery via SWIM gossip.
 */
final class TopologyBroadcasterTest {

  private static final String LOCAL_MEMBER_ID = "broker-0";

  private Properties memberProperties;
  private TopologyBroadcaster broadcaster;

  @BeforeEach
  void setUp() {
    memberProperties = new Properties();
    final Member localMember = mock(Member.class);
    when(localMember.properties()).thenReturn(memberProperties);
    broadcaster = new TopologyBroadcaster(localMember, LOCAL_MEMBER_ID);
  }

  // -------------------------------------------------------------------------

  @Nested
  class OnBecameLeader {

    @Test
    void shouldWritePartitionLeaderKeyToSWIMProperties() {
      // when
      broadcaster.onBecameLeader(0, 1L);

      // then
      assertThat(memberProperties.getProperty("eb.partition.0.leader")).isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldUseCorrectKeyForDifferentPartitionIds() {
      // when
      broadcaster.onBecameLeader(3, 2L);
      broadcaster.onBecameLeader(7, 3L);

      // then
      assertThat(memberProperties.getProperty("eb.partition.3.leader")).isEqualTo(LOCAL_MEMBER_ID);
      assertThat(memberProperties.getProperty("eb.partition.7.leader")).isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldOverwriteExistingLeaderEntry() {
      // given — some stale value already present
      memberProperties.setProperty("eb.partition.0.leader", "stale-broker");

      // when
      broadcaster.onBecameLeader(0, 5L);

      // then
      assertThat(memberProperties.getProperty("eb.partition.0.leader")).isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldOnlyWriteAffectedPartition() {
      // given — partition 1 is already led by this broker
      memberProperties.setProperty("eb.partition.1.leader", LOCAL_MEMBER_ID);

      // when — partition 0 becomes leader
      broadcaster.onBecameLeader(0, 1L);

      // then — both entries present, no other keys written
      assertThat(memberProperties.getProperty("eb.partition.0.leader")).isEqualTo(LOCAL_MEMBER_ID);
      assertThat(memberProperties.getProperty("eb.partition.1.leader")).isEqualTo(LOCAL_MEMBER_ID);
      assertThat(memberProperties).hasSize(2);
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class OnLostLeadership {

    @Test
    void shouldRemovePartitionLeaderKeyFromSWIMProperties() {
      // given
      memberProperties.setProperty("eb.partition.0.leader", LOCAL_MEMBER_ID);

      // when
      broadcaster.onLostLeadership(0, Role.FOLLOWER);

      // then
      assertThat(memberProperties.containsKey("eb.partition.0.leader")).isFalse();
    }

    @Test
    void shouldNotFailWhenKeyNotPresent() {
      // when — no leader key present, should not throw
      broadcaster.onLostLeadership(99, Role.CANDIDATE);

      // then — properties map unchanged
      assertThat(memberProperties).isEmpty();
    }

    @Test
    void shouldOnlyRemoveAffectedPartition() {
      // given — two partitions led by this broker
      memberProperties.setProperty("eb.partition.0.leader", LOCAL_MEMBER_ID);
      memberProperties.setProperty("eb.partition.1.leader", LOCAL_MEMBER_ID);

      // when — partition 0 lost
      broadcaster.onLostLeadership(0, Role.FOLLOWER);

      // then — only partition 0 removed; partition 1 still present
      assertThat(memberProperties.containsKey("eb.partition.0.leader")).isFalse();
      assertThat(memberProperties.getProperty("eb.partition.1.leader")).isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldHandleAllNonLeaderRoles() {
      for (final Role role : new Role[] {Role.FOLLOWER, Role.CANDIDATE, Role.INACTIVE}) {
        // given
        memberProperties.setProperty("eb.partition.0.leader", LOCAL_MEMBER_ID);

        // when
        broadcaster.onLostLeadership(0, role);

        // then
        assertThat(memberProperties.containsKey("eb.partition.0.leader"))
            .as("key should be absent after role %s", role)
            .isFalse();
      }
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class ReElection {

    @Test
    void shouldRestoreLeaderKeyAfterReElection() {
      // given — became leader, then lost it
      broadcaster.onBecameLeader(0, 1L);
      broadcaster.onLostLeadership(0, Role.FOLLOWER);
      assertThat(memberProperties.containsKey("eb.partition.0.leader")).isFalse();

      // when — re-elected in a later term
      broadcaster.onBecameLeader(0, 3L);

      // then
      assertThat(memberProperties.getProperty("eb.partition.0.leader")).isEqualTo(LOCAL_MEMBER_ID);
    }
  }

  // -------------------------------------------------------------------------

  @Nested
  class AdvertiseCoordinator {

    @Test
    void shouldWriteCoordinatorKeyToSWIMProperties() {
      // when
      broadcaster.advertiseCoordinator();

      // then
      assertThat(memberProperties.getProperty(TopologyService.COORDINATOR_KEY))
          .isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldBeIndependentOfPartitionLeaderKeys() {
      // when
      broadcaster.onBecameLeader(0, 1L);
      broadcaster.advertiseCoordinator();

      // then — both keys present independently
      assertThat(memberProperties.getProperty("eb.partition.0.leader")).isEqualTo(LOCAL_MEMBER_ID);
      assertThat(memberProperties.getProperty(TopologyService.COORDINATOR_KEY))
          .isEqualTo(LOCAL_MEMBER_ID);
    }

    @Test
    void shouldNotWriteCoordinatorKeyWhenNotCalled() {
      // when — only partition leader published
      broadcaster.onBecameLeader(0, 1L);

      // then — coordinator key absent
      assertThat(memberProperties.containsKey(TopologyService.COORDINATOR_KEY)).isFalse();
    }

    @Test
    void shouldNotRemoveCoordinatorKeyOnLostLeadership() {
      // given
      broadcaster.advertiseCoordinator();
      broadcaster.onBecameLeader(0, 1L);

      // when — partition 0 loses leadership
      broadcaster.onLostLeadership(0, Role.FOLLOWER);

      // then — coordinator key still present
      assertThat(memberProperties.getProperty(TopologyService.COORDINATOR_KEY))
          .isEqualTo(LOCAL_MEMBER_ID);
    }
  }
}
