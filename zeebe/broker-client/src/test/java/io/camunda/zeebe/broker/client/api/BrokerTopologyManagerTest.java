/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
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
          public void brokerAdded(final BrokerMemberId memberId, final String physicalTenantId) {
            capturedIds.add(memberId);
          }
        });
    actorSchedulerRule.workUntilDone();

    // then — the listener is backfilled with the zone-aware id, not the bare int "0"
    assertThat(capturedIds).containsExactly(zonedMemberId);
  }

  @Test
  void shouldNotifyListenerWithGroupWhenBrokerJoins() {
    // given
    final var capturedGroups = new CopyOnWriteArrayList<String>();
    final var capturedMembers = new CopyOnWriteArrayList<BrokerMemberId>();
    addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void brokerAdded(final BrokerMemberId memberId, final String physicalTenantId) {
            capturedMembers.add(memberId);
            capturedGroups.add(physicalTenantId);
          }
        });

    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo broker = createBrokerWithGroup(brokerId, "tenant1");

    // when
    notifyEvent(createMemberAddedEvent(broker));

    // then
    assertThat(capturedMembers).containsExactly(brokerId);
    assertThat(capturedGroups).containsExactly("tenant1");
  }

  @Test
  void shouldBackfillNewListenerWithGroupInfo() {
    // given — broker 0 already joined with group "tenant1"
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo broker = createBrokerWithGroup(brokerId, "tenant1");
    notifyEvent(createMemberAddedEvent(broker));

    // when — a new listener is registered after the broker is already known
    final var capturedGroups = new CopyOnWriteArrayList<String>();
    final var capturedMembers = new CopyOnWriteArrayList<BrokerMemberId>();
    addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void brokerAdded(final BrokerMemberId memberId, final String physicalTenantId) {
            capturedMembers.add(memberId);
            capturedGroups.add(physicalTenantId);
          }
        });

    // then — backfill fires brokerAdded with the known group
    assertThat(capturedMembers).containsExactly(brokerId);
    assertThat(capturedGroups).containsExactly("tenant1");
  }

  @Test
  void shouldNotifyListenerWithGroupWhenBrokerRemoved() {
    // given
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo broker = createBrokerWithGroup(brokerId, "tenant1");
    notifyEvent(createMemberAddedEvent(broker));

    final var capturedRemovedGroups = new CopyOnWriteArrayList<String>();
    final var capturedRemovedMembers = new CopyOnWriteArrayList<BrokerMemberId>();
    addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void brokerRemoved(final BrokerMemberId memberId, final String physicalTenantId) {
            capturedRemovedMembers.add(memberId);
            capturedRemovedGroups.add(physicalTenantId);
          }
        });

    // when
    notifyEvent(createMemberRemoveEvent(broker));

    // then
    assertThat(capturedRemovedMembers).containsExactly(brokerId);
    assertThat(capturedRemovedGroups).containsExactly("tenant1");
  }

  @Test
  void shouldNotifyListenerForEachGroupOnBrokerRemoved() {
    // given — broker 0 serves both default and tenant1 groups
    final var brokerId = BrokerMemberId.from(0);
    final BrokerInfo defaultInfo = createBroker(brokerId);
    final BrokerInfo tenant1Info = createBrokerWithGroup(brokerId, "tenant1");

    final Member member = new Member(new MemberConfig().setId(defaultInfo.brokerIdStr()));
    defaultInfo.writeIntoProperties(member.properties());
    tenant1Info.writeIntoProperties(member.properties());
    members.add(member);
    notifyEvent(new ClusterMembershipEvent(Type.MEMBER_ADDED, member));

    final var capturedRemovedGroups = new CopyOnWriteArrayList<String>();
    addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void brokerRemoved(final BrokerMemberId memberId, final String physicalTenantId) {
            capturedRemovedGroups.add(physicalTenantId);
          }
        });

    // when
    notifyEvent(new ClusterMembershipEvent(Type.MEMBER_REMOVED, member));

    // then — one notification per group
    assertThat(capturedRemovedGroups)
        .containsExactlyInAnyOrder(DEFAULT_PHYSICAL_TENANT_ID, "tenant1");
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
  void shouldApplyConfigurationReceivedBeforeAnyBrokerJoined() {
    // given -- the cluster configuration is gossiped in before any local membership event for the
    // default group is observed
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
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    topologyManager.onClusterConfigurationUpdated(clusterTopologyWithTwoBrokers);
    actorSchedulerRule.workUntilDone();

    // when -- a broker for the default group joins afterward
    final var broker = createBroker(BrokerMemberId.from(1));
    notifyEvent(createMemberAddedEvent(broker));

    // then -- the previously-received configuration was not dropped
    assertThat(topologyManager.getTopology().getClusterSize()).isEqualTo(2);
    assertThat(topologyManager.getTopology().getPartitions()).isEqualTo(List.of(1, 2));
  }

  @Test
  void shouldResolveEachGroupsOwnConfiguredClusterState() {
    // given -- a broker joins a non-default tenant group
    final var brokerId = BrokerMemberId.from(0);
    final var tenant1Info = createBrokerWithGroup(brokerId, "tenant1");
    notifyEvent(createMemberAddedEvent(tenant1Info));

    // when -- the new-model cluster configuration carries distinct partition groups for
    // "default" and "tenant1", each with a different partition assignment
    final var defaultGroup =
        new PartitionGroupConfiguration(
            PartitionGroupConfiguration.INITIAL_VERSION,
            PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
            Map.of(
                MemberId.from("1"),
                BrokerPartitionState.initialize(
                    Map.of(
                        1,
                        PartitionState.active(1, DynamicPartitionConfig.init()),
                        2,
                        PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var tenant1Group =
        new PartitionGroupConfiguration(
            PartitionGroupConfiguration.INITIAL_VERSION,
            PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
            Map.of(
                MemberId.from("2"),
                BrokerPartitionState.initialize(
                    Map.of(5, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var clusterConfiguration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            new GlobalConfiguration(
                GlobalConfiguration.INITIAL_VERSION,
                Optional.empty(),
                Map.of(
                    MemberId.from("1"), BrokerState.initializeAsActive(),
                    MemberId.from("2"), BrokerState.initializeAsActive()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()),
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", tenant1Group),
            PhasedChangeState.empty());
    topologyManager.onClusterConfigurationUpdated(clusterConfiguration);
    actorSchedulerRule.workUntilDone();

    // then -- the default group resolves its own partitions, not tenant1's
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getClusterSize()).isEqualTo(2));
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(2);
    assertThat(topologyManager.getTopology().getPartitions()).isEqualTo(List.of(1, 2));

    // and -- tenant1 resolves its own partitions, not the default group's
    assertThat(topologyManager.getTopology("tenant1").getClusterSize()).isEqualTo(2);
    assertThat(topologyManager.getTopology("tenant1").getPartitionsCount()).isEqualTo(1);
    assertThat(topologyManager.getTopology("tenant1").getPartitions()).isEqualTo(List.of(5));
  }

  @Test
  void shouldUpdateOnlyChangedGroupOnIncrementalConfigurationUpdate() {
    // given -- default and tenant1 are both known locally and both configured
    notifyEvent(createMemberAddedEvent(createBroker(BrokerMemberId.from(0))));
    notifyEvent(createMemberAddedEvent(createBrokerWithGroup(BrokerMemberId.from(0), "tenant1")));

    final var global = globalConfigWithMember(MemberId.from("1"));
    final var defaultGroup = singleMemberGroup(MemberId.from("1"), Map.of(1, 1, 2, 1));
    final var initialTenant1Group = singleMemberGroup(MemberId.from("1"), Map.of(5, 1));
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", initialTenant1Group),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(2);
    assertThat(topologyManager.getTopology("tenant1").getPartitionsCount()).isEqualTo(1);

    // when -- a second update changes only tenant1's partition assignment; default's
    // configuration object is passed unchanged
    final var updatedTenant1Group = singleMemberGroup(MemberId.from("1"), Map.of(5, 1, 6, 1));
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", updatedTenant1Group),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();

    // then -- tenant1 reflects the new partition, default is unaffected by tenant1's change
    assertThat(topologyManager.getTopology("tenant1").getPartitionsCount()).isEqualTo(2);
    assertThat(topologyManager.getTopology().getPartitionsCount())
        .describedAs("default group must be unaffected by tenant1's configuration change")
        .isEqualTo(2);
    assertThat(topologyManager.getTopology().getPartitions()).isEqualTo(List.of(1, 2));
  }

  @Test
  void shouldExcludeADisabledGroupFromTopology() {
    // given -- default and tenant1 are both known locally and both configured
    notifyEvent(createMemberAddedEvent(createBroker(BrokerMemberId.from(0))));
    notifyEvent(createMemberAddedEvent(createBrokerWithGroup(BrokerMemberId.from(0), "tenant1")));

    final var global = globalConfigWithMember(MemberId.from("1"));
    final var defaultGroup = singleMemberGroup(MemberId.from("1"), Map.of(1, 1));
    final var tenant1Group = singleMemberGroup(MemberId.from("1"), Map.of(5, 1));
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", tenant1Group),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();
    assertThat(topologyManager.getTopology("tenant1").isInitialized()).isTrue();
    assertThat(topologyManager.getTopology("tenant1").getPartitionsCount()).isEqualTo(1);

    // when -- tenant1 is disabled (removed from local static configuration); its
    // PartitionGroupConfiguration is still present, just flagged
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", tenant1Group.disable()),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();

    // then -- tenant1 no longer resolves, as if it were never configured at all; default is
    // unaffected
    assertThat(topologyManager.getTopology("tenant1").isInitialized()).isFalse();
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(1);
  }

  @Test
  void shouldFireIncarnationChangedOncePerGroupWhoseIncarnationNumberChanged() {
    // given -- default and tenant1 are both configured at their initial incarnation number
    notifyEvent(createMemberAddedEvent(createBroker(BrokerMemberId.from(0))));
    notifyEvent(createMemberAddedEvent(createBrokerWithGroup(BrokerMemberId.from(0), "tenant1")));

    final var global = globalConfigWithMember(MemberId.from("1"));
    final var defaultGroup = singleMemberGroup(MemberId.from("1"), Map.of(1, 1));
    final var tenant1Group = singleMemberGroup(MemberId.from("1"), Map.of(5, 1));
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", tenant1Group),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();

    final var incarnationChanges = new AtomicInteger();
    addTopologyListener(
        new BrokerTopologyListener() {
          @Override
          public void clusterIncarnationChanged() {
            incarnationChanges.incrementAndGet();
          }
        });

    // when -- only tenant1's incarnation number advances; default's configuration is unchanged
    final var tenant1GroupWithNewIncarnation = incrementIncarnation(tenant1Group);
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                defaultGroup,
                "tenant1",
                tenant1GroupWithNewIncarnation),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();

    // then -- the listener fires exactly once, for tenant1's incarnation bump, not for default
    assertThat(incarnationChanges).hasValue(1);
  }

  @Test
  void shouldPreserveLiveStateWhenGroupHasNoPartitionGroupConfiguration() {
    // given -- a broker has already joined the "tenant1" group, so live state (brokers, leaders)
    // is established for it
    final var brokerId = BrokerMemberId.from(0);
    final var tenant1Info = createBrokerWithGroup(brokerId, "tenant1");
    tenant1Info.setLeaderForPartition(1, 1L);
    notifyEvent(createMemberAddedEvent(tenant1Info));

    assertThat(topologyManager.getTopology("tenant1").getLeaderForPartition(1)).isEqualTo(brokerId);

    // when -- a cluster configuration update arrives whose partition groups do not contain
    // "tenant1" at all (e.g. the group is not part of the new model config)
    final var configurationWithoutTenant1 =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(),
            PhasedChangeState.empty());
    topologyManager.onClusterConfigurationUpdated(configurationWithoutTenant1);
    actorSchedulerRule.workUntilDone();

    // then -- the live state for tenant1 (leader, brokers) is preserved, not reset
    assertThat(topologyManager.getTopology("tenant1").getLeaderForPartition(1)).isEqualTo(brokerId);
  }

  @Test
  void shouldUseOwnGroupsConfiguredStateWhenBrokerJoinsAfterMultiTenantConfiguration() {
    // given -- the cluster configuration is applied for both default (3 partitions) and tenant1
    // (1 partition), before any broker has locally joined tenant1's group
    final var global = globalConfigWithMember(MemberId.from("1"));
    final var defaultGroup = singleMemberGroup(MemberId.from("1"), Map.of(1, 1, 2, 1, 3, 1));
    final var tenant1Group = singleMemberGroup(MemberId.from("1"), Map.of(5, 1));
    topologyManager.onClusterConfigurationUpdated(
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            global,
            Map.of(DEFAULT_PHYSICAL_TENANT_ID, defaultGroup, "tenant1", tenant1Group),
            PhasedChangeState.empty()));
    actorSchedulerRule.workUntilDone();
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(3);

    // when -- a broker later joins tenant1's group locally, triggering a topology rebuild for
    // tenant1
    final var brokerId = BrokerMemberId.from(0);
    notifyEvent(createMemberAddedEvent(createBrokerWithGroup(brokerId, "tenant1")));

    // then -- tenant1 keeps reporting its own configured partition, not the default group's
    assertThat(topologyManager.getTopology("tenant1").getPartitionsCount())
        .describedAs("tenant1 must report its own partition count, not the default group's")
        .isEqualTo(1);
    assertThat(topologyManager.getTopology("tenant1").getPartitions()).isEqualTo(List.of(5));

    // and -- the default group is unaffected
    assertThat(topologyManager.getTopology().getPartitionsCount()).isEqualTo(3);
  }

  @Test
  void shouldReturnUninitializedTopologyForUnknownGroup() {
    // given / when — no brokers added
    // then
    assertThat(topologyManager.getTopology("unknown-group").isInitialized()).isFalse();
  }

  @Test
  void shouldReportUnknownTenantAsRecoveringOrUnknown() {
    // given -- no configuration was ever received for this tenant

    // when / then -- recovery cannot be ruled out, so callers must hold off rather than act
    assertThat(topologyManager.isRecovering("unknowntenant")).isTrue();
  }

  @Test
  void shouldReportTenantWithoutBrokersAsRecoveringOrUnknown() {
    // given -- a configured group that carries no brokers at all
    final var configuration = configureTenantWithMode(Map.of());

    // when
    topologyManager.onClusterConfigurationUpdated(configuration);
    actorSchedulerRule.workUntilDone();

    // then -- there is no broker whose mode could rule recovery out
    assertThat(topologyManager.isRecovering("tenant1")).isTrue();
  }

  @Test
  void shouldNotReportTenantAsRecoveringOrUnknownWhileEveryBrokerIsProcessing() {
    // given
    final var configuration =
        configureTenantWithMode(
            Map.of(MemberId.from("1"), Mode.PROCESSING, MemberId.from("2"), Mode.PROCESSING));

    // when
    topologyManager.onClusterConfigurationUpdated(configuration);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(topologyManager.isRecovering("tenant1")).isFalse();
  }

  @Test
  void shouldReportTenantAsRecoveringWhileOnlyOneBrokerHasEnteredRecovery() {
    // given -- a mode change that has flipped one of the two brokers so far
    final var configuration =
        configureTenantWithMode(
            Map.of(MemberId.from("1"), Mode.RECOVERING, MemberId.from("2"), Mode.PROCESSING));

    // when
    topologyManager.onClusterConfigurationUpdated(configuration);
    actorSchedulerRule.workUntilDone();

    // then -- the tenant counts as recovering for the whole transition window
    assertThat(topologyManager.isRecovering("tenant1")).isTrue();
  }

  @Test
  void shouldNotReportTenantAsRecoveringBecauseAnotherTenantIs() {
    // given
    final var configuration =
        configureTenantsWithModes(
            Map.of(
                "tenant1",
                Map.of(MemberId.from("1"), Mode.RECOVERING),
                DEFAULT_PHYSICAL_TENANT_ID,
                Map.of(MemberId.from("1"), Mode.PROCESSING)));

    // when
    topologyManager.onClusterConfigurationUpdated(configuration);
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(topologyManager.isRecovering(DEFAULT_PHYSICAL_TENANT_ID)).isFalse();
    assertThat(topologyManager.isRecovering("tenant1")).isTrue();
  }

  private void addTopologyListener(final BrokerTopologyListener listener) {
    topologyManager.addTopologyListener(listener);
    actorSchedulerRule.workUntilDone();
  }

  private BrokerInfo createBroker(final BrokerMemberId brokerId) {
    final BrokerInfo broker =
        new BrokerInfo().setBrokerId(brokerId.nodeIdx(), brokerId.zone()).setClusterSize(3);
    broker.setCommandApiAddress("localhost:1000");
    broker.setVersion("0.23.0-SNAPSHOT");
    return broker;
  }

  private BrokerInfo createBrokerWithGroup(final BrokerMemberId brokerId, final String group) {
    return createBroker(brokerId).setPartitionGroup(group);
  }

  private static GlobalConfiguration globalConfigWithMember(final MemberId memberId) {
    return new GlobalConfiguration(
        GlobalConfiguration.INITIAL_VERSION,
        Optional.empty(),
        Map.of(memberId, BrokerState.initializeAsActive()),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  // builds a partition group configuration hosted by a single member, at the initial incarnation
  private static PartitionGroupConfiguration singleMemberGroup(
      final MemberId memberId, final Map<Integer, Integer> partitionIdToPriority) {
    final Map<Integer, PartitionState> partitions = new HashMap<>();
    partitionIdToPriority.forEach(
        (partitionId, priority) ->
            partitions.put(
                partitionId, PartitionState.active(priority, DynamicPartitionConfig.init())));
    return new PartitionGroupConfiguration(
        PartitionGroupConfiguration.INITIAL_VERSION,
        PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
        Map.of(memberId, BrokerPartitionState.initialize(partitions)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  // builds a cluster configuration holding only a "tenant1" group, whose members are in the given
  // modes
  private static CurrentClusterConfiguration configureTenantWithMode(
      final Map<MemberId, Mode> modeByMember) {
    return configureTenantsWithModes(Map.of("tenant1", modeByMember));
  }

  private static CurrentClusterConfiguration configureTenantsWithModes(
      final Map<String, Map<MemberId, Mode>> modeByMemberByTenant) {
    final Map<String, PartitionGroupConfiguration> groups = new HashMap<>();
    modeByMemberByTenant.forEach(
        (physicalTenantId, modeByMember) -> {
          final Map<MemberId, BrokerPartitionState> members = new HashMap<>();
          modeByMember.forEach(
              (memberId, mode) ->
                  members.put(
                      memberId,
                      BrokerPartitionState.initialize(
                              Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))
                          .setMode(mode)));
          groups.put(
              physicalTenantId,
              new PartitionGroupConfiguration(
                  PartitionGroupConfiguration.INITIAL_VERSION,
                  PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
                  members,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty()));
        });
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfigWithMember(MemberId.from("1")),
        groups,
        PhasedChangeState.empty());
  }

  private static PartitionGroupConfiguration incrementIncarnation(
      final PartitionGroupConfiguration group) {
    return new PartitionGroupConfiguration(
        group.version(),
        group.incarnationNumber() + 1,
        group.members(),
        group.routingState(),
        group.pendingChanges(),
        group.lastChange());
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
    public void brokerAdded(final BrokerMemberId memberId, final String physicalTenantId) {
      brokers.add(memberId);
    }

    @Override
    public void brokerRemoved(final BrokerMemberId memberId, final String physicalTenantId) {
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
