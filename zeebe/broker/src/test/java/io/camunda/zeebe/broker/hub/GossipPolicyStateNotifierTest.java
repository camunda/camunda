/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus;
import io.camunda.zeebe.engine.processing.policy.PolicyStatus.UpdateStatus;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GossipPolicyStateNotifierTest {

  private final Member localMember = new Member(new MemberConfig().setId("broker-0"));
  private final ClusterMembershipService membershipService = mock(ClusterMembershipService.class);
  private GossipPolicyStateNotifier notifier;

  @BeforeEach
  void setUp() {
    when(membershipService.getLocalMember()).thenReturn(localMember);
    notifier = new GossipPolicyStateNotifier(membershipService, "pt-1");
  }

  @Test
  void shouldGossipThePolicyStateOfTheTenant() {
    // given
    final var status =
        new PolicyStatus(new CommittedPolicy("policy-1", 3, 42), UpdateStatus.PENDING, "update-43");

    // when
    notifier.publish(status);

    // then
    assertThat(PolicyStateGossip.read(localMember, "pt-1")).contains(status);
    assertThat(PolicyStateGossip.read(localMember, "pt-2")).isEmpty();
  }

  @Test
  void shouldGossipAStateWithoutCommittedPolicy() {
    // given
    final var status = new PolicyStatus(null, UpdateStatus.NONE, null);

    // when
    notifier.publish(status);

    // then
    assertThat(PolicyStateGossip.read(localMember, "pt-1")).contains(status);
  }

  @Test
  void shouldWithdrawTheGossipedState() {
    // given
    notifier.publish(new PolicyStatus(null, UpdateStatus.NONE, null));

    // when
    notifier.clear();

    // then
    assertThat(localMember.properties()).doesNotContainKey(PolicyStateGossip.key("pt-1"));
    assertThat(PolicyStateGossip.read(localMember, "pt-1")).isEmpty();
  }

  @Test
  void shouldIgnoreAStateItCannotRead() {
    // given
    localMember.properties().setProperty(PolicyStateGossip.key("pt-1"), "not json");

    // when
    final var read = PolicyStateGossip.read(localMember, "pt-1");

    // then
    assertThat(read).isEmpty();
  }
}
