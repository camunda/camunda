/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.topology;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TopologyUpdateTest {

  private final ControlledActorClock actorClock = new ControlledActorClock();
  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(actorClock);

  private BrokerTopologyManagerImpl topologyManager;
  private Set<Member> members;

  @Before
  public void setUp() {
    members = new HashSet<>();
    topologyManager = new BrokerTopologyManagerImpl(() -> members, (a, b) -> {});
    actorSchedulerRule.submitActor(topologyManager);
  }

  @After
  public void tearDown() {
    topologyManager.close();
  }

  @Test
  public void shouldUpdateTopologyOnBrokerAdd() {
    final BrokerInfo broker = createBroker(0);
    topologyManager.event(createMemberAddedEvent(broker));
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1)).isNull();

    final BrokerInfo brokerUpdated = createBroker(0);
    brokerUpdated.setFollowerForPartition(1);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology().getFollowersForPartition(1) != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isTrue();
  }

  @Test
  public void shouldAddBrokerOnTopologyEvenOnNotReceivedEvent() {
    // given
    final BrokerInfo broker = createBroker(0);
    broker.setFollowerForPartition(1);
    createMemberAddedEvent(broker);

    // when
    actorClock.addTime(Duration.ofSeconds(10));

    // then
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isTrue();
  }

  @Test
  public void shouldUpdateBrokerOnTopologyEvenOnNotReceivedEvent() {
    // given
    final BrokerInfo broker = createBroker(0);
    topologyManager.event(createMemberAddedEvent(broker));
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1)).isNull();

    // when
    final BrokerInfo brokerUpdate = createBroker(0);
    brokerUpdate.setFollowerForPartition(1);
    createMemberUpdateEvent(brokerUpdate);
    actorClock.addTime(Duration.ofSeconds(10));

    // then
    waitUntil(() -> topologyManager.getTopology().getFollowersForPartition(1) != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isTrue();
  }

  @Test
  public void shouldUpdateTopologyOnBrokerRemove() {
    final BrokerInfo broker = createBroker(0);
    topologyManager.event(createMemberAddedEvent(broker));
    final BrokerInfo brokerUpdated = createBroker(0);
    brokerUpdated.setFollowerForPartition(1);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology() != null);

    topologyManager.event(createMemberRemoveEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology().getBrokers().isEmpty());

    topologyManager.event(createMemberAddedEvent(broker));
    waitUntil(() -> !topologyManager.getTopology().getBrokers().isEmpty());

    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isFalse();
  }

  @Test
  public void shouldUpdateLeaderWithNewTerm() {
    // given
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(1, 1);
    topologyManager.event(createMemberAddedEvent(oldLeader));
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getLeaderForPartition(1)).isEqualTo(oldLeaderId);

    // when
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(1, 2);
    topologyManager.event(createMemberAddedEvent(newLeader));

    // then
    waitUntil(() -> topologyManager.getTopology().getBrokers().contains(newLeaderId));
    assertThat(topologyManager.getTopology().getLeaderForPartition(1)).isEqualTo(newLeaderId);
  }

  @Test
  public void shouldNotUpdateLeaderWhenPreviousTerm() {
    // given
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(1, 2);
    topologyManager.event(createMemberAddedEvent(newLeader));
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getLeaderForPartition(1)).isEqualTo(newLeaderId);

    // when
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(1, 1);
    topologyManager.event(createMemberAddedEvent(oldLeader));

    // then
    waitUntil(() -> topologyManager.getTopology().getBrokers().contains(oldLeaderId));
    assertThat(topologyManager.getTopology().getLeaderForPartition(1)).isEqualTo(newLeaderId);
  }

  private BrokerInfo createBroker(int brokerId) {
    final BrokerInfo broker =
        new BrokerInfo()
            .setNodeId(brokerId)
            .setPartitionsCount(1)
            .setClusterSize(3)
            .setReplicationFactor(3);
    broker.setCommandApiAddress("localhost:1000");
    return broker;
  }

  private ClusterMembershipEvent createMemberAddedEvent(BrokerInfo broker) {
    final Member member = createMemberFromBrokerInfo(broker);
    return new ClusterMembershipEvent(Type.MEMBER_ADDED, member);
  }

  private ClusterMembershipEvent createMemberUpdateEvent(BrokerInfo broker) {
    final Member member = createMemberFromBrokerInfo(broker);
    return new ClusterMembershipEvent(Type.METADATA_CHANGED, member);
  }

  private Member createMemberFromBrokerInfo(BrokerInfo broker) {
    final Member member =
        new Member(new MemberConfig().setId(Integer.toString(broker.getNodeId())));
    broker.writeIntoProperties(member.properties());
    members.add(member);
    return member;
  }

  private ClusterMembershipEvent createMemberRemoveEvent(BrokerInfo broker) {
    final Member member = new Member(new MemberConfig());
    broker.writeIntoProperties(member.properties());
    return new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
  }
}
