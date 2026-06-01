/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BrokerMemberId;
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
import java.util.concurrent.CopyOnWriteArrayList;
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
    final var brokerId = BrokerMemberId.from(1);
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
    final var brokerId = BrokerMemberId.from(0);
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
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isNull();
  }

  @Test
  void shouldUpdateLeaderWithNewTerm() {
    // given
    final int partition = 1;
    final var oldLeaderId = BrokerMemberId.from(0);
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(oldLeader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .describedAs("Topology has the old leader")
        .isEqualTo(oldLeaderId);

    // when
    final var newLeaderId = BrokerMemberId.from(1);
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(newLeader));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).contains(newLeaderId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderId);
  }

  @Test
  void shouldNotUpdateLeaderWhenFromPreviousTerm() {
    // given
    final int partition = 1;
    final var newLeaderId = BrokerMemberId.from(1);
    final BrokerInfo newLeader = createBroker(newLeaderId);
    newLeader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(newLeader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderId);

    // when
    final var oldLeaderId = BrokerMemberId.from(0);
    final BrokerInfo oldLeader = createBroker(oldLeaderId);
    oldLeader.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(oldLeader));

    // then
    assertThat(topologyManager.getTopology().getBrokers()).contains(oldLeaderId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition))
        .isEqualTo(newLeaderId);
  }

  @Test
  void shouldUpdateLeaderWhenPartitionReBootstrapWithLowerTerm() {
    // given
    final int partition = 1;
    final var leaderId = BrokerMemberId.from(1);
    final BrokerInfo leader = createBroker(leaderId);
    leader.setLeaderForPartition(partition, 2);
    notifyEvent(createMemberAddedEvent(leader));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isEqualTo(leaderId);

    // when
    // partition shutdown/purge
    leader.removePartition(partition);
    notifyEvent(createMemberUpdateEvent(leader));

    // new leader starts with a lower term
    final var newLeaderAfterRebootstrapId = BrokerMemberId.from(0);
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
    final var leaderId = BrokerMemberId.from(1);
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
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isEqualTo(leaderId);
  }

  @Test
  void shouldUpdateTopologyOnPartitionHealth() {
    // given
    final var brokerId = BrokerMemberId.from(0);
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);

    // when
    final BrokerInfo updatedBroker = createBroker(brokerId);
    updatedBroker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(updatedBroker));

    // then
    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);
  }

  @Test
  void shouldUpdateTopologyMetadataWhileNotDuplicatingFollower() {
    // given
    final var brokerId = BrokerMemberId.from(0);
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setFollowerForPartition(partition);

    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);

    // then
    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  void shouldUpdateTopologyMetadataWhileNotDuplicatingInactiveNodes() {
    // given
    final var brokerId = BrokerMemberId.from(0);
    final int partition = 0;
    final BrokerInfo broker = createBroker(brokerId);
    broker.setPartitionHealthy(partition);
    broker.setInactiveForPartition(partition);
    notifyEvent(createMemberAddedEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is healthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.HEALTHY);
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);

    // when
    broker.setPartitionUnhealthy(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getPartitionHealth(brokerId, partition))
        .as("partition %d is unhealthy on broker %s", partition, brokerId)
        .isEqualTo(PartitionHealthStatus.UNHEALTHY);

    // then
    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .containsExactly(brokerId);
  }

  @Test
  void shouldUpdateTopologyOnLeaderRemoval() {
    // given
    final int partition = 1;
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo broker = createBroker(brokerId).setLeaderForPartition(partition, partition);

    // when
    notifyEvent(createMemberUpdateEvent(broker));

    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isEqualTo(brokerId);

    broker.setFollowerForPartition(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    // then

    assertThat(topologyManager.getTopology().getFollowersForPartition(partition))
        .containsExactlyInAnyOrder(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isNull();
  }

  @Test
  void shouldUpdateTopologyOnBrokerInactive() {
    // given
    final int partition = 0;
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo broker = createBroker(brokerId);
    broker.setLeaderForPartition(partition, 1);
    notifyEvent(createMemberAddedEvent(broker));

    // when

    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .isNullOrEmpty();
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isEqualTo(brokerId);

    broker.setInactiveForPartition(partition);
    notifyEvent(createMemberUpdateEvent(broker));

    // then

    assertThat(topologyManager.getTopology().getInactiveNodesForPartition(partition))
        .contains(brokerId);
    assertThat(topologyManager.getTopology().getLeaderForPartition(partition)).isNull();
  }

  @Test
  void shouldNotifyListenerWhenBrokerAdded() {
    // given
    final RecordingTopologyListener topology = new RecordingTopologyListener();
    addTopologyListener(topology);

    final var brokerId = BrokerMemberId.from(1);
    final BrokerInfo broker = createBroker(brokerId);

    // when
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topology.getBrokers()).contains(brokerId);
  }

  @Test
  void shouldNotifyListenerWithInitialState() {
    // given
    final var brokerId = BrokerMemberId.from(1);
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

    final var brokerId = BrokerMemberId.from(1);
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
        ClusterConfiguration.builder()
            .version(1)
            .lastChange(
                Optional.of(new CompletedChange(1, Status.COMPLETED, Instant.now(), Instant.now())))
            .build();

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

    final var brokerId = BrokerMemberId.from(1);
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
    final BrokerInfo broker = createBroker(BrokerMemberId.from(1));
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
    final BrokerInfo broker = createBroker(BrokerMemberId.from(1));
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topologyManager.getTopology().getClusterSize()).isEqualTo(2);
  }

  @Test
  void shouldUpdatePartitionsFromClusterTopology() {
    // given
    final var broker = createBroker(BrokerMemberId.from(1));
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
    final var broker = createBroker(BrokerMemberId.from(1));
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(2);
  }

  @Test
  void shouldBackfillNewListenerWithCanonicalZoneAwareMemberId() {
    // given — broker 0 joined with a zone-aware id
    final var zonedMemberId = BrokerMemberId.from("eu-west_0");
    final var broker = createBroker(zonedMemberId);
    final var member =
        new Member(new MemberConfig().setId(zonedMemberId.memberId()).setZoneId("eu-west"));
    broker.writeIntoProperties(member.properties());
    members.add(member);
    notifyEvent(new ClusterMembershipEvent(Type.MEMBER_ADDED, member));

    // when — a new listener is added after the broker is already known
    final var capturedIds = new CopyOnWriteArrayList<BrokerMemberId>();
    topologyManager.addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void brokerAdded(final BrokerMemberId memberId) {
            capturedIds.add(memberId);
          }
        });
    actorSchedulerRule.workUntilDone();

    // then — the listener is backfilled with the zone-aware id, not the bare int "0"
    assertThat(capturedIds).containsExactly(zonedMemberId);
  }

  @Test
  void shouldAggregateTopologyPerPartitionGroup() {
    // given — broker 0 publishes both a default-group and a tenant1-group BrokerInfo
    final var brokerId = BrokerMemberId.from(0);
    final int partitionDefault = 1;
    final int partitionTenant1 = 1;

    final BrokerInfo defaultInfo = createBroker(brokerId);
    defaultInfo.setLeaderForPartition(partitionDefault, 1L);

    final BrokerInfo tenant1Info = createBrokerWithGroup(brokerId, "tenant1");
    tenant1Info.setFollowerForPartition(partitionTenant1);

    // Write both groups into the same member properties
    final Member member = new Member(new MemberConfig().setId(defaultInfo.brokerIdStr()));
    defaultInfo.writeIntoProperties(member.properties());
    tenant1Info.writeIntoProperties(member.properties());
    members.add(member);

    // when
    notifyEvent(new ClusterMembershipEvent(Type.MEMBER_ADDED, member));

    // then — default group topology reflects the default BrokerInfo
    assertThat(topologyManager.getTopology().getLeaderForPartition(partitionDefault))
        .isEqualTo(brokerId);
    assertThat(topologyManager.getTopology().getFollowersForPartition(partitionDefault)).isEmpty();

    // then — tenant1 group topology reflects the tenant1 BrokerInfo
    final BrokerClusterState tenant1Topology = topologyManager.getTopology("tenant1");
    assertThat(tenant1Topology.getFollowersForPartition(partitionTenant1)).contains(brokerId);
    assertThat(tenant1Topology.getLeaderForPartition(partitionTenant1)).isNull();
  }

  @Test
  void shouldRemoveBrokerFromAllGroupsOnMemberRemoved() {
    // given — broker 0 is present in default and tenant1 groups
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo defaultInfo = createBroker(brokerId);
    defaultInfo.setFollowerForPartition(1);
    final BrokerInfo tenant1Info = createBrokerWithGroup(brokerId, "tenant1");
    tenant1Info.setLeaderForPartition(1, 1L);

    final Member member = new Member(new MemberConfig().setId(defaultInfo.brokerIdStr()));
    defaultInfo.writeIntoProperties(member.properties());
    tenant1Info.writeIntoProperties(member.properties());
    members.add(member);
    notifyEvent(new ClusterMembershipEvent(Type.MEMBER_ADDED, member));

    assertThat(topologyManager.getTopology().getBrokers()).contains(brokerId);
    assertThat(topologyManager.getTopology("tenant1").getBrokers()).contains(brokerId);

    // when
    notifyEvent(createMemberRemoveEvent(defaultInfo));

    // then — broker is gone from both groups
    assertThat(topologyManager.getTopology().getBrokers()).doesNotContain(brokerId);
    assertThat(topologyManager.getTopology("tenant1").getBrokers()).doesNotContain(brokerId);
  }

  @Test
  void shouldReturnUninitializedTopologyForUnknownGroup() {
    // given / when — no brokers added
    // then
    assertThat(topologyManager.getTopology("unknown-group").isInitialized()).isFalse();
  }

  private void addTopologyListener(final BrokerTopologyListener listener) {
    topologyManager.addTopologyListener(listener);
    actorSchedulerRule.workUntilDone();
  }

  private BrokerInfo createBroker(final BrokerMemberId brokerId) {
    final BrokerInfo broker =
        new BrokerInfo()
            .setBrokerId(brokerId.nodeIdx(), brokerId.zone())
            .setPartitionsCount(1)
            .setClusterSize(3)
            .setReplicationFactor(3);
    broker.setCommandApiAddress("localhost:1000");
    broker.setVersion("0.23.0-SNAPSHOT");
    return broker;
  }

  private BrokerInfo createBrokerWithGroup(final BrokerMemberId brokerId, final String group) {
    return createBroker(brokerId).setPartitionGroup(group);
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
    final Member member = new Member(new MemberConfig().setId(broker.brokerIdStr()));
    broker.writeIntoProperties(member.properties());
    members.add(member);
    return member;
  }

  private ClusterMembershipEvent createMemberRemoveEvent(final BrokerInfo broker) {
    final Member member = new Member(new MemberConfig().setId(broker.brokerIdStr()));
    broker.writeIntoProperties(member.properties());
    return new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
  }

  private void notifyEvent(final ClusterMembershipEvent broker) {
    topologyManager.event(broker);
    actorSchedulerRule.workUntilDone();
  }

  private static final class RecordingTopologyListener implements BrokerTopologyListener {

    private final Set<BrokerMemberId> brokers = new CopyOnWriteArraySet<>();

    @Override
    public void brokerAdded(final BrokerMemberId memberId) {
      brokers.add(memberId);
    }

    @Override
    public void brokerRemoved(final BrokerMemberId memberId) {
      brokers.remove(memberId);
    }

    Set<BrokerMemberId> getBrokers() {
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
