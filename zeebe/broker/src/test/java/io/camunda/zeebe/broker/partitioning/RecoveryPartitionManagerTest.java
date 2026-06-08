/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecoveryPartitionManagerTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final int PARTITION_ID = 1;
  private static final int PARTITION_ID_2 = 2;

  @TempDir private Path dataDirectory;

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private Member localMember;
  private MemberId localMemberId;
  private ClusterConfigurationService clusterConfigurationService;
  private ClusterServices clusterServices;
  private RecoveryPartitionManager partitionManager;

  @BeforeEach
  void setUp() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    controlActor = new Actor() {};
    actorScheduler.submitActor(controlActor).join();

    localMember = new Member(new MemberConfig());
    localMemberId = localMember.id();

    final var membershipService = mock(ClusterMembershipService.class);
    when(membershipService.getLocalMember()).thenReturn(localMember);

    clusterServices = mock(ClusterServices.class);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);

    // two partitions where the local broker is a member
    final var metadata = localPartitionMetadata(PARTITION_ID);
    final var metadata2 = localPartitionMetadata(PARTITION_ID_2);
    clusterConfigurationService = mock(ClusterConfigurationService.class);
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(new PartitionDistribution(Set.of(metadata, metadata2)));

    final var brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);

    partitionManager =
        new RecoveryPartitionManager(
            GROUP,
            dataDirectory.toString(),
            controlActor,
            clusterConfigurationService,
            clusterServices,
            actorScheduler,
            brokerInfo,
            new SimpleMeterRegistry());
  }

  private PartitionMetadata localPartitionMetadata(final int partitionId) {
    return new PartitionMetadata(
        PartitionId.from(GROUP, partitionId),
        Set.of(localMemberId),
        Map.of(localMemberId, 1),
        1,
        localMemberId);
  }

  @AfterEach
  void tearDown() {
    if (partitionManager != null) {
      partitionManager.stop().join();
    }
    if (controlActor != null) {
      controlActor.closeAsync().join();
    }
    actorScheduler.stop();
  }

  @Test
  void shouldDeactivateLocalPartitionsOnStart() {
    // when
    partitionManager.start().join();

    // then — both local partitions are published as INACTIVE in the broker's topology
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                              .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
            });
  }

  @Test
  void shouldNotStartRaftOrZeebePartitions() {
    // when
    partitionManager.start().join();

    // then — recovery mode never brings up Raft or Zeebe partitions
    assertThat(partitionManager.getRaftPartitions()).isEmpty();
    assertThat(partitionManager.getZeebePartitions()).isEmpty();
    assertThat(partitionManager.getRaftPartition(PARTITION_ID)).isNull();
  }
}
