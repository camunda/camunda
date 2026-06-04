/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TopologyManagerImplTest {

  private static final int PARTITION_ID = 1;
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @AutoClose private ActorScheduler scheduler;
  private BrokerInfo brokerInfo;
  private TopologyManagerImpl topologyManager;

  @BeforeEach
  void setUp() {
    final var localMember = mock(Member.class);
    when(localMember.properties()).thenReturn(new Properties());

    final var membershipService = mock(ClusterMembershipService.class);
    when(membershipService.getLocalMember()).thenReturn(localMember);
    when(membershipService.getMembers()).thenReturn(Set.of());

    brokerInfo = new BrokerInfo(0, null, "localhost:26501");
    brokerInfo.setFollowerForPartition(PARTITION_ID);

    scheduler = ActorScheduler.newActorScheduler().build();
    scheduler.start();

    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    scheduler.submitActor(topologyManager).join();
  }

  @Test
  void shouldIgnoreHealthChangedAfterPartitionRemoved() {
    // given - removePartition queued first, onHealthChanged(DEAD) queued second
    topologyManager.removePartition(PARTITION_ID);
    topologyManager.onHealthChanged(PARTITION_ID, HealthStatus.DEAD);

    // then - partition must not appear in broker info even after the stale health event fires.
    // `during` covers the window after removePartition completes but before onHealthChanged runs.
    Awaitility.await()
        .atMost(TIMEOUT)
        .during(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              assertThat(brokerInfo.getPartitionRoles()).doesNotContainKey(PARTITION_ID);
              assertThat(brokerInfo.getPartitionHealthStatuses()).doesNotContainKey(PARTITION_ID);
            });
  }

  @Test
  void shouldRemovePartitionAfterHealthTransitionToDeadDuringShutdown() {
    // given - health transitions to DEAD before removePartition is called (Order A)
    topologyManager.onHealthChanged(PARTITION_ID, HealthStatus.DEAD);
    // removePartition queued after onHealthChanged
    topologyManager.removePartition(PARTITION_ID);

    // then - partition must be cleared after removePartition runs.
    // Since onHealthChanged was queued first, it ran first; removePartition cleans up after it.
    Awaitility.await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              assertThat(brokerInfo.getPartitionRoles()).doesNotContainKey(PARTITION_ID);
              assertThat(brokerInfo.getPartitionHealthStatuses()).doesNotContainKey(PARTITION_ID);
            });
  }
}
