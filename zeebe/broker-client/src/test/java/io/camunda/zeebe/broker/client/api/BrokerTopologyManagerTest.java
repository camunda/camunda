/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static io.camunda.zeebe.broker.client.api.BrokerClusterState.NODE_ID_NULL;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class BrokerTopologyManagerTest {
  @RegisterExtension
  final ControlledActorSchedulerExtension actorSchedulerRule =
      new ControlledActorSchedulerExtension();

  // keep referencing the implementation class here to allow interactions with the actor scheduler
  private BrokerTopologyManagerImpl topologyManager;
  private Set<Member> members;

  @BeforeEach
  void setUp() {
    members = new HashSet<>();
    topologyManager =
        new BrokerTopologyManagerImpl(
            () -> members, new BrokerClientTopologyMetrics(new SimpleMeterRegistry()));
    actorSchedulerRule.submitActor(topologyManager);
    actorSchedulerRule.workUntilDone();
  }

  @AfterEach
  void tearDown() {
    topologyManager.closeAsync();
    actorSchedulerRule.workUntilDone();
  }

  @Test
  void shouldUpdateTopologyOnBrokerAdd() {
    // given
    final int brokerId = 1;
    final int partition = 1;
    final BrokerInfo broker = createBroker(brokerId);
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition)).isEmpty();

    // when
    final BrokerInfo brokerUpdated = createBroker(brokerId);
    brokerUpdated.setFollowerForPartition(partition);
    notifyEvent(createMemberUpdateEvent(brokerUpdated));

    // then

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .describedAs("The partition has the expected follower")
        .containsExactly(brokerId);
    assertThat(topologyManager.getTopology().getBrokerVersion(brokerId))
        .isEqualTo(broker.getVersion());
  }

  @Test
  void shouldUpdateTopologyOnBrokerRemove() {
    // given
    final int brokerId = 0;
    final int partition = 1;
    final BrokerInfo broker = createBroker(brokerId);
    notifyEvent(createMemberAddedEvent(broker));

    final BrokerInfo brokerUpdated = createBroker(brokerId);
    brokerUpdated.setFollowerForPartition(partition);
    notifyEvent(createMemberUpdateEvent(brokerUpdated));

    assertThat(topologyManager.getTopology().getBrokers()).isNotEmpty();

    // when
    notifyEvent(createMemberRemoveEvent(brokerUpdated));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).isEmpty();

    // when
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getBrokers()).isNotEmpty();

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .doesNotContain(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(NODE_ID_NULL);
  }

  @Test
  void shouldUpdateLeaderWithNewTerm() {
    // given
    final int partition = 1;
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(oldLeader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .describedAs("Topology has the old leader")
        .isZero();

    // when
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(newLeader));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).contains(newLeaderId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isOne();
  }

  @Test
  void shouldNotUpdateLeaderWhenFromPreviousTerm() {
    // given
    final int partition = 1;
    final int newLeaderId = 1;
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(newLeader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isOne();

    // when
    final int oldLeaderId = 0;
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(oldLeader));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).contains(oldLeaderId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isOne();
  }

  @Test
  void shouldUpdateLeaderWhenPartitionReBootstrapWithLowerTerm() {
    // given
    final int partition = 1;
    final int leaderId = 1;
    final BrokerInfo leader = createBroker(leaderId);
    leader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(leader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isOne();

    // when
    // partition shutdown/purge
    leader.removePartition(partition);
    notifyEvent(createMemberUpdateEvent(leader));

    // new leader starts with a lower term
    final int newLeaderAfterRebootstrapId = 0;
    final BrokerInfo newLeaderAfterRebootstrap = createBroker(newLeaderAfterRebootstrapId);
    newLeaderAfterRebootstrap.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(newLeaderAfterRebootstrap));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).contains(newLeaderAfterRebootstrapId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderAfterRebootstrapId);
  }

  @Test
  void shouldUpdateTopologyOnBrokerRemoveAndDirectlyRejoin() {
    // given
    final int partition = 1;
    final int leaderId = 1;
    final BrokerInfo leader = createBroker(leaderId);
    leader.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(leader));

    assertThat(topologyManager.getTopology()).isNotNull();

    // when
    notifyEvent(createMemberRemoveEvent(leader));

    assertThat(topologyManager.getTopology().getBrokers()).isEmpty();
    notifyEvent(createMemberAddedEvent(leader));

    // then
    assertThat(topologyManager.getTopology().getBrokers())
        .describedAs("the broker has rejoined the cluster")
        .containsExactly(leaderId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isOne();
  }

  @Test
  void shouldUpdateTopologyOnPartitionHealth() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);

    // when
    final BrokerInfo updatedBroker = createBroker(0);
    updatedBroker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(updatedBroker));

    // then
    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);
  }

  @Test
  void shouldUpdateTopologyMetadataWhileNotDuplicatingFollower() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setFollowerForPartition(partition);

    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);

    // then
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  void shouldUpdateTopologyMetadataWhileNotDuplicatingInactiveNodes() {
    // given
    final int brokerId = 0;
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setInactiveForPartition(partition);
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %d", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);

    // then
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  void shouldUpdateTopologyOnLeaderRemoval() {
    // given
    final int partition = 1;
    final int brokerId = 0;
    final BrokerInfo broker = createBroker(brokerId).setLeaderForPartition(partition, partition);

    // when
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isZero();

    broker.setFollowerForPartition(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    // then

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactlyInAnyOrder(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(NODE_ID_NULL);
  }

  @Test
  void shouldUpdateTopologyOnBrokerInactive() {
    // given
    final int partition = 0;
    final int brokerId = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(broker));

    // when

    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .isNullOrEmpty();
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isZero();

    broker.setInactiveForPartition(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    // then

    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .contains(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isNotZero();
  }

  @Test
  void shouldNotifyListenerWhenBrokerAdded() {
    // given
    final RecordingTopologyListener topology = new RecordingTopologyListener();
    addTopologyListener(topology);

    final int brokerId = 1;
    final BrokerInfo broker = createBroker(brokerId);

    // when
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topology.getBrokers()).contains(brokerId);
  }

  @Test
  void shouldNotifyListenerWithInitialState() {
    // given
    final int brokerId = 1;
    final BrokerInfo broker = createBroker(brokerId);
    notifyEvent(createMemberAddedEvent(broker));

    // when
    final RecordingTopologyListener topology = new RecordingTopologyListener();
    addTopologyListener(topology);

    // then
    assertThat(topology.getBrokers()).contains(brokerId);
  }

  @Test
  void shouldNotifyListenerWhenBrokerRemoved() {
    // given
    final RecordingTopologyListener topology = new RecordingTopologyListener();
    addTopologyListener(topology);

    final int brokerId = 1;
    final BrokerInfo broker = createBroker(brokerId);
    notifyEvent(createMemberAddedEvent(broker));

    // when
    notifyEvent(createMemberRemoveEvent(broker));

    // then
    assertThat(topology.getBrokers()).doesNotContain(brokerId);
  }

  @Test
  void shouldNotifyListenerWhenClusterChangeCompleted() {
    // given
    final ClusterCompletedChangeListener listener = new ClusterCompletedChangeListener();
    addTopologyListener(listener);

    // when
    final ClusterConfiguration clusterTopology =
        new ClusterConfiguration(
            1,
            Map.of(),
            Optional.of(new CompletedChange(1, Status.COMPLETED, Instant.now(), Instant.now())),
            Optional.empty(),
            Optional.empty());

    topologyManager.onClusterConfigurationUpdated(clusterTopology);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(listener.wasExecutedAfterClusterChange()).isTrue();
  }

  @Test
  void shouldRemoveListener() {
    // given
    final RecordingTopologyListener topology = new RecordingTopologyListener();
    addTopologyListener(topology);

    final int brokerId = 1;
    final BrokerInfo broker = createBroker(brokerId);
    notifyEvent(createMemberAddedEvent(broker));

    // when
    topologyManager.removeTopologyListener(topology);
    actorSchedulerRule.workUntilDone();

    notifyEvent(createMemberRemoveEvent(broker));

    // then
    assertThat(topology.getBrokers())
        .describedAs("Listener should not get remove event")
        .contains(brokerId);
  }

  @Test
  void shouldUpdateClusterSizeFromClusterTopology() {
    // given
    final BrokerInfo broker = createBroker(1);
    notifyEvent(createMemberAddedEvent(broker));

    // when
    final ClusterConfiguration clusterTopologyWithTwoBrokers =
        ClusterConfiguration.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    topologyManager.onClusterConfigurationUpdated(clusterTopologyWithTwoBrokers);
    actorSchedulerRule.workUntilDone();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getClusterSize()).isEqualTo(2));
  }

  @Test
  void shouldNotOverwriteClusterSizeFromBrokerInfo() {
    // given
    final ClusterConfiguration clusterTopologyWithTwoBrokers =
        ClusterConfiguration.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    topologyManager.onClusterConfigurationUpdated(clusterTopologyWithTwoBrokers);
    actorSchedulerRule.workUntilDone();

    // when
    final BrokerInfo broker = createBroker(1);
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topologyManager.getTopology().getClusterSize()).isEqualTo(2);
  }

  @Test
  void shouldUpdatePartitionsFromClusterTopology() {
    // given
    final var broker = createBroker(1);
    notifyEvent(createMemberAddedEvent(broker));

    // when
    final var clusterTopologyWithTwoBrokers =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, DynamicPartitionConfig.init()),
                        2,
                        PartitionState.active(2, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(2, DynamicPartitionConfig.init()),
                        2,
                        PartitionState.active(1, DynamicPartitionConfig.init()))));
    topologyManager.onClusterConfigurationUpdated(clusterTopologyWithTwoBrokers);
    actorSchedulerRule.workUntilDone();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(2));

    assertThat(topologyManager.getTopology().getPartitions()).isEqualTo(List.of(1, 2));
  }

  @Test
  void shouldNotOverwritePartitionsCountFromBrokerInfo() {
    // given
    final var clusterTopologyWithTwoBrokers =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, DynamicPartitionConfig.init()),
                        2,
                        PartitionState.active(2, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(2, DynamicPartitionConfig.init()),
                        2,
                        PartitionState.active(1, DynamicPartitionConfig.init()))));
    topologyManager.onClusterConfigurationUpdated(clusterTopologyWithTwoBrokers);
    actorSchedulerRule.workUntilDone();

    // when
    final var broker = createBroker(1);
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(2);
  }

  private void addTopologyListener(final BrokerTopologyListener listener) {
    topologyManager.addTopologyListener(listener);
    actorSchedulerRule.workUntilDone();
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
    final Member member = new Member(new MemberConfig().setId(String.valueOf(broker.getNodeId())));
    broker.writeIntoProperties(member.properties());
    return new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
  }

  private void notifyEvent(final ClusterMembershipEvent broker) {
    topologyManager.event(broker);
    actorSchedulerRule.workUntilDone();
  }

  private static final class RecordingTopologyListener implements BrokerTopologyListener {

    private final Set<Integer> brokers = new CopyOnWriteArraySet<>();

    @Override
    public void brokerAdded(final MemberId memberId) {
      brokers.add(Integer.parseInt(memberId.id()));
    }

    @Override
    public void brokerRemoved(final MemberId memberId) {
      brokers.remove(Integer.parseInt(memberId.id()));
    }

    Set<Integer> getBrokers() {
      return brokers;
    }
  }

  private static final class ClusterCompletedChangeListener implements BrokerTopologyListener {
    private boolean wasExecutedAfterClusterChange = false;

    @Override
    public void completedClusterChange() {
      wasExecutedAfterClusterChange = true;
    }

    public boolean wasExecutedAfterClusterChange() {
      return wasExecutedAfterClusterChange;
    }
  }
}
