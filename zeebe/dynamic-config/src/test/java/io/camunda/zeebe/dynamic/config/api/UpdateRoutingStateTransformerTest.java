/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidState;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class UpdateRoutingStateTransformerTest {

  private static final String TENANT_B = "tenant-b";

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private final CurrentClusterConfiguration currentTopology =
      CurrentClusterConfiguration.init()
          .updateGlobalConfiguration(
              globalConfiguration ->
                  globalConfiguration.addMember(id1, BrokerState.initializeAsActive()))
          .initPartitionGroup(DEFAULT_GROUP)
          .updatePartitionGroupConfig(
              DEFAULT_GROUP, group -> group.addMember(id1, hostingAPartition()));

  @Test
  void shouldGenerateUpdateRoutingStateOperationWhenEnabled() {
    // given
    final var routingState =
        Optional.of(
            new RoutingState(
                1L,
                new RoutingState.RequestHandling.AllPartitions(3),
                new RoutingState.MessageCorrelation.HashMod(3)));
    final var transformer = new UpdateRoutingStateTransformer(routingState);

    // when
    final var result = plannedOperations(transformer, currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0)).isInstanceOf(UpdateRoutingState.class);
    final var operation = (UpdateRoutingState) result.get().get(0);
    assertThat(operation.routingState()).isEqualTo(routingState);
  }

  @Test
  void shouldGenerateUpdateRoutingStateOperationWithEmptyRoutingState() {
    // given
    final var transformer = new UpdateRoutingStateTransformer(Optional.empty());

    // when
    final var result = plannedOperations(transformer, currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0)).isInstanceOf(UpdateRoutingState.class);
    final var operation = (UpdateRoutingState) result.get().get(0);
    assertThat(operation.routingState()).isEmpty();
  }

  @Test
  void shouldRejectUnknownPhysicalTenant() {
    // given
    final var transformer =
        new UpdateRoutingStateTransformer(Optional.empty(), Optional.of("unknown"));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — the tenant does not exist, so the caller gets 404 rather than 500
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft()).isInstanceOf(NotFound.class).hasMessageContaining("unknown");
  }

  @Test
  void shouldTargetOnlyTheRequestedPhysicalTenantsPartitionGroup() {
    // given
    final var routingState = Optional.of(RoutingState.initializeWithPartitionCount(1));
    final var transformer = new UpdateRoutingStateTransformer(routingState, Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — only tenant-b's group carries an operation; the default group is left untouched
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            PartitionGroupPhase.sequential(
                Map.of(TENANT_B, List.of(new UpdateRoutingState(id1, routingState)))));
  }

  @Test
  void shouldTargetTheDefaultPhysicalTenantsPartitionGroupWhenUnscoped() {
    // given — an unscoped request keeps writing the default group, unlike the other per-tenant
    // requests where an absent physicalTenantId means "every tenant"
    final var routingState = Optional.of(RoutingState.initializeWithPartitionCount(1));
    final var transformer = new UpdateRoutingStateTransformer(routingState, Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            PartitionGroupPhase.sequential(
                Map.of(DEFAULT_GROUP, List.of(new UpdateRoutingState(id0, routingState)))));
  }

  @Test
  void shouldRejectWhenNoBrokerOfThePhysicalTenantHoldsAPartition() {
    // given — a tenant whose only broker holds none of its partitions, which is the one broker the
    // operation could be dispatched to
    final var configuration = cluster(Map.of(DEFAULT_GROUP, group(Map.of(id0, holdingNothing()))));
    final var transformer =
        new UpdateRoutingStateTransformer(
            Optional.of(RoutingState.initializeWithPartitionCount(1)));

    // when
    final var result = transformer.phases(configuration);

    // then — the request fails rather than the planning throwing, which would escape the request
    // instead of answering it
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(InvalidState.class)
        .satisfies(
            error ->
                assertThat(error).hasMessageContaining("none of its members hold any partitions"));
  }

  private CurrentClusterConfiguration twoTenantCluster() {
    return cluster(
        Map.of(
            DEFAULT_GROUP, group(Map.of(id0, hostingAPartition())),
            TENANT_B, group(Map.of(id1, hostingAPartition()))));
  }

  private CurrentClusterConfiguration cluster(
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    final var brokers =
        partitionGroups.values().stream()
            .flatMap(group -> group.members().keySet().stream())
            .distinct()
            .collect(
                Collectors.toMap(
                    memberId -> memberId,
                    memberId -> new BrokerState(0, Instant.EPOCH, BrokerState.State.ACTIVE)));
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1, Optional.empty(), brokers, Optional.empty(), Optional.empty(), Optional.empty()),
        partitionGroups,
        PhasedChangeState.empty());
  }

  private PartitionGroupConfiguration group(final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private BrokerPartitionState holdingNothing() {
    return BrokerPartitionState.initialize(Map.of());
  }

  private BrokerPartitionState hostingAPartition() {
    return BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, partitionConfig)));
  }
}
