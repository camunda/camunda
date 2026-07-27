/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.changes.AwaitModeChangeApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Wires the real {@link PartitionModeHandler} together with a real {@link AwaitModeChangeApplier} -
 * the two collaborators, split across the broker and dynamic-config modules, that jointly decide
 * which partitions get confirmed as {@link PartitionState.State#RECOVERING}. Each is otherwise
 * tested in isolation with the other mocked out, so this proves the composition: a partition
 * reported unhealthy is excluded from the confirmed set and keeps its prior {@link PartitionState},
 * while a healthy sibling is written as {@code RECOVERING}.
 */
final class PartitionModeHandlerAwaitModeChangeApplierTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final MemberId LOCAL_MEMBER = MemberId.from("0");

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final ControlActor controlActor = new ControlActor();

  private ClusterConfigurationService clusterConfigurationService;
  private TopologyManagerImpl topologyManager;
  private PartitionModeHandler handler;

  @BeforeEach
  void setUp() {
    scheduler.submitActor(controlActor);
    scheduler.workUntilDone();

    clusterConfigurationService = mock(ClusterConfigurationService.class);
    topologyManager = mock(TopologyManagerImpl.class);

    final var brokerStartupContext = mock(BrokerStartupContext.class);
    when(brokerStartupContext.getConcurrencyControl()).thenReturn(controlActor.getActorControl());
    when(brokerStartupContext.getClusterConfigurationService())
        .thenReturn(clusterConfigurationService);
    final Map<String, PartitionManager> partitionManagers = new HashMap<>();
    partitionManagers.put(GROUP, mock(RecoveryPartitionManager.class));
    when(brokerStartupContext.getPartitionManagers()).thenReturn(partitionManagers);

    final var clusterServices = mock(ClusterServicesImpl.class);
    final var membershipService = mock(ClusterMembershipService.class);
    final var localMember = mock(Member.class);
    when(localMember.id()).thenReturn(LOCAL_MEMBER);
    when(membershipService.getLocalMember()).thenReturn(localMember);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);
    when(brokerStartupContext.getClusterServices()).thenReturn(clusterServices);

    handler = new PartitionModeHandler(brokerStartupContext, GROUP, topologyManager);
  }

  private void givenLocalPartitions(final Integer... partitionIds) {
    final Set<PartitionMetadata> metadata =
        Arrays.stream(partitionIds)
            .map(
                id ->
                    new PartitionMetadata(
                        new PartitionId(GROUP, id),
                        Set.of(LOCAL_MEMBER),
                        Map.of(LOCAL_MEMBER, 1),
                        1,
                        LOCAL_MEMBER))
            .collect(Collectors.toSet());
    when(clusterConfigurationService.getPartitionDistribution())
        .thenReturn(new PartitionDistribution(metadata));
  }

  private void givenPartitionRoles(final Map<Integer, PartitionRole> roles) {
    when(topologyManager.getLocalPartitionRoles())
        .thenReturn(CompletableActorFuture.completed(Map.copyOf(roles)));
  }

  private void givenPartitionHealth(final Map<Integer, PartitionHealthStatus> health) {
    when(topologyManager.getLocalPartitionHealth())
        .thenReturn(CompletableActorFuture.completed(Map.copyOf(health)));
  }

  private void progress() {
    scheduler.workUntilDone();
  }

  @Test
  void shouldWriteRecoveringOnlyForHealthyPartitionsAcrossBothCollaborators() {
    // given - partition 1 recovers healthily, partition 2 reaches INACTIVE but is DEAD
    givenLocalPartitions(1, 2);
    givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE, 2, PartitionRole.INACTIVE));
    givenPartitionHealth(Map.of(1, PartitionHealthStatus.HEALTHY, 2, PartitionHealthStatus.DEAD));

    final var partitionConfig = DynamicPartitionConfig.init();
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                LOCAL_MEMBER,
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig))));
    final var applier = new AwaitModeChangeApplier(LOCAL_MEMBER, Mode.RECOVERING, handler);

    // when
    final var result = applier.apply();
    progress();

    // then - only the confirmed, healthy partition (1) is written as RECOVERING; the dead
    // partition (2) keeps its prior state instead of being silently marked as recovered
    assertThat(result.isCompletedExceptionally()).isFalse();
    final var updated = result.join().apply(clusterConfiguration);
    assertThat(updated.getMember(LOCAL_MEMBER).getPartition(1).state())
        .isEqualTo(PartitionState.State.RECOVERING);
    assertThat(updated.getMember(LOCAL_MEMBER).getPartition(2).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  @Test
  void shouldFailWithoutWritingAnyStateWhenHealthNotYetReported() {
    // given - role reached INACTIVE, but health hasn't been reported yet (the race the design
    // closes: RecoveryPartitionManager sets role and health in two separate steps)
    givenLocalPartitions(1);
    givenPartitionRoles(Map.of(1, PartitionRole.INACTIVE));
    givenPartitionHealth(Map.of());

    final var applier = new AwaitModeChangeApplier(LOCAL_MEMBER, Mode.RECOVERING, handler);

    // when
    final var result = applier.apply();
    progress();

    // then - the applier propagates the failure so the cluster change is retried, rather than
    // writing any partition state prematurely
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  private static final class ControlActor extends Actor {
    ActorControl getActorControl() {
      return actor;
    }
  }
}
