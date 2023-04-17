/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.topology;

import static io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterState.NODE_ID_NULL;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TopologyUpdateTest {

  private final ControlledActorClock actorClock = new ControlledActorClock();
  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(actorClock);

  private BrokerTopologyManagerImpl topologyManager;
  private Set<Member> members;

  @Before
  public void setUp() {
    members = new HashSet<>();
    topologyManager = new BrokerTopologyManagerImpl(() -> members);
    actorSchedulerRule.submitActor(topologyManager);
  }

  @After
  public void tearDown() {
    topologyManager.close();
  }

  @Test
  public void shouldUpdateTopologyOnBrokerAdd() {
    // given
    final int brokerId = 1;
    final int partition = 1;
    final BrokerInfo broker = createBroker(brokerId);
    topologyManager.event(createMemberAddedEvent(broker));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("the partition has no followers")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
                    .isNull());

    // when
    final BrokerInfo brokerUpdated = createBroker(brokerId);
    brokerUpdated.setFollowerForPartition(partition);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));

    // then
    Awaitility.await("the partition has the expected follower")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
                    .containsExactly(brokerId));
    assertThat(topologyManager.getTopology().getBrokerVersion(brokerId))
        .isEqualTo(broker.getVersion());
  }

  @Test
  public void shouldUpdateTopologyOnBrokerRemove() {
    // given
    final int brokerId = 0;
    final int partition = 1;
    final BrokerInfo broker = createBroker(brokerId);
    topologyManager.event(createMemberAddedEvent(broker));
    final BrokerInfo brokerUpdated = createBroker(brokerId);
    brokerUpdated.setFollowerForPartition(partition);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("the topology has brokers")
        .untilAsserted(() -> assertThat(topologyManager.getTopology().getBrokers()).isNotEmpty());

    // when
    topologyManager.event(createMemberRemoveEvent(brokerUpdated));

    // then
    Awaitility.await("the topology has no brokers anymore")
        .untilAsserted(() -> assertThat(topologyManager.getTopology().getBrokers()).isEmpty());

    // when
    topologyManager.event(createMemberAddedEvent(broker));
    Awaitility.await("the topology has brokers")
        .untilAsserted(() -> assertThat(topologyManager.getTopology().getBrokers()).isNotEmpty());

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .doesNotContain(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(NODE_ID_NULL);
  }

  @Test
  public void shouldUpdateLeaderWithNewTerm() {
    // given
    final int partition = 1;
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    topologyManager.event(createMemberAddedEvent(oldLeader));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("the topology has the old leader")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                    .isEqualTo(oldLeaderId));

    // when
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    topologyManager.event(createMemberAddedEvent(newLeader));

    // then
    Awaitility.await("the new broker is in the topology")
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getBrokers()).contains(newLeaderId));
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderId);
  }

  @Test
  public void shouldNotUpdateLeaderWhenFromPreviousTerm() {
    // given
    final int partition = 1;
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    topologyManager.event(createMemberAddedEvent(newLeader));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("the topology has the new leader")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                    .isEqualTo(newLeaderId));

    // when
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    topologyManager.event(createMemberAddedEvent(oldLeader));

    // then
    Awaitility.await("the old broker is in the topology")
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getBrokers()).contains(oldLeaderId));
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderId);
  }

  @Test
  public void shouldUpdateTopologyOnBrokerRemoveAndDirectlyRejoin() {
    // given
    final int partition = 1;
    final int leaderId = 1;
    final BrokerInfo leader = createBroker(leaderId);
    leader.setLeaderForPartition(partition, 1);
    topologyManager.event(createMemberAddedEvent(leader));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("the topology exists")
        .untilAsserted(() -> assertThat(topologyManager.getTopology()).isNotNull());

    // when
    topologyManager.event(createMemberRemoveEvent(leader));
    Awaitility.await("the topology has no brokers")
        .untilAsserted(() -> assertThat(topologyManager.getTopology().getBrokers()).isEmpty());
    topologyManager.event(createMemberAddedEvent(leader));

    // then
    Awaitility.await("the broker has rejoined the tropology")
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getBrokers()).containsExactly(leaderId));
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isEqualTo(leaderId);
  }

  @Test
  public void shouldUpdateTopologyOnPartitionHealth() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    topologyManager.event(createMemberAddedEvent(broker));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("partition is detected as healthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is healthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.HEALTHY));

    // when
    final BrokerInfo updatedBroker = createBroker(0);
    updatedBroker.setPartitionUnhealthy(partition);
    topologyManager.event(createMemberUpdateEvent(updatedBroker));
    Awaitility.await("partition is detected as unhealthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is unhealthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.UNHEALTHY));

    // then
    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);
  }

  @Test
  public void shouldUpdateTopologyMetadataWhileNotDuplicatingFollower() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setFollowerForPartition(partition);

    topologyManager.event(createMemberAddedEvent(broker));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("partition is detected as healthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is healthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.HEALTHY));
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    topologyManager.event(createMemberUpdateEvent(broker));
    Awaitility.await("partition is detected as unhealthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is unhealthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.UNHEALTHY));

    // then
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  public void shouldUpdateTopologyMetadataWhileNotDuplicatingInactiveNodes() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setInactiveForPartition(partition);
    topologyManager.event(createMemberAddedEvent(broker));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("partition is detected as healthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is healthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.HEALTHY));
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    topologyManager.event(createMemberUpdateEvent(broker));
    Awaitility.await("partition is detected as unhealthy")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
                    .as("partition %d is unhealthy on broker %d", partition, brokerId)
                    .isEqualTo(PartitionHealthStatus.UNHEALTHY));

    // then
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  public void shouldUpdateTopologyOnLeaderRemoval() {
    // given
    final int partition = 1;
    final int brokerId = 0;
    final BrokerInfo broker = createBroker(brokerId).setLeaderForPartition(partition, partition);

    // when
    topologyManager.event(createMemberUpdateEvent(broker));
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("broker 0 is leader of partition 1")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                    .isZero());

    broker.setFollowerForPartition(partition);
    topologyManager.event(createMemberUpdateEvent(broker));

    // then
    Awaitility.await("broker 0 is follower of partition 1")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
                  .containsExactlyInAnyOrder(brokerId);
              assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                  .isEqualTo(NODE_ID_NULL);
            });
  }

  @Test
  public void shouldUpdateTopologyOnBrokerInactive() {
    // given
    final int partition = 0;
    final int brokerId = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setLeaderForPartition(partition, 1);
    topologyManager.event(createMemberAddedEvent(broker));

    // when
    Awaitility.given()
        // topology is eventually available
        .ignoreException(NullPointerException.class)
        .await("broker 0 is leader of partition 0")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
                  .isNullOrEmpty();
              assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                  .isEqualTo(brokerId);
            });
    broker.setInactiveForPartition(partition);
    topologyManager.event(createMemberUpdateEvent(broker));

    // then
    Awaitility.await("broker 0 is inactive for partition 0")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
                  .contains(brokerId);
              assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
                  .isNotEqualTo(brokerId);
            });
  }

  private BrokerInfo createBroker(final int brokerId) {
    final BrokerInfo broker =
        new BrokerInfo()
            .setNodeId(brokerId)
            .setPartitionsCount(1)
            .setClusterSize(3)
            .setReplicationFactor(3);
    broker.setCommandApiAddress("localhost:1000");
    broker.setVersion("0.23.0-SNAPSHOT");
    return broker;
  }

  private ClusterMembershipEvent createMemberAddedEvent(final BrokerInfo broker) {
    final Member member = createMemberFromBrokerInfo(broker);
    return new ClusterMembershipEvent(Type.MEMBER_ADDED, member);
  }

  private ClusterMembershipEvent createMemberUpdateEvent(final BrokerInfo broker) {
    final Member member = createMemberFromBrokerInfo(broker);
    return new ClusterMembershipEvent(Type.METADATA_CHANGED, member);
  }

  private Member createMemberFromBrokerInfo(final BrokerInfo broker) {
    final Member member =
        new Member(new MemberConfig().setId(Integer.toString(broker.getNodeId())));
    broker.writeIntoProperties(member.properties());
    members.add(member);
    return member;
  }

  private ClusterMembershipEvent createMemberRemoveEvent(final BrokerInfo broker) {
    final Member member = new Member(new MemberConfig());
    broker.writeIntoProperties(member.properties());
    return new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
  }
}
