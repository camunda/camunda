/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ExportingStateChangeRequestTransformerTest {

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  private final DynamicPartitionConfig exportingConfig =
      new DynamicPartitionConfig(new ExportingConfig(ExportingState.EXPORTING, Map.of()));
  private final DynamicPartitionConfig pausedConfig =
      new DynamicPartitionConfig(new ExportingConfig(ExportingState.PAUSED, Map.of()));

  @Test
  void shouldGenerateOperationsForAllPhysicalTenantsWhenNoneIsGiven() {
    // given
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(exportingConfig),
                TENANT_B, groupWithMembers(exportingConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — one phase carrying both groups, so their partitions change concurrently
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations())
        .containsOnlyKeys(TENANT_A, TENANT_B)
        .allSatisfy(
            (groupId, operations) ->
                assertThat(operations)
                    .containsExactlyInAnyOrder(
                        new ExportingStateChangeOperation(id0, ExportingState.PAUSED),
                        new ExportingStateChangeOperation(id1, ExportingState.PAUSED)));
  }

  @Test
  void shouldSkipPhysicalTenantsAlreadyInTheTargetState() {
    // given — tenant-b already paused, tenant-a is not
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(exportingConfig),
                TENANT_B, groupWithMembers(pausedConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — the group that has nothing to do is left out of the phase entirely
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations()).containsOnlyKeys(TENANT_A);
  }

  @Test
  void shouldSkipAMemberAlreadyFullyInTheTargetState() {
    // given — within tenant-a, id0 is still exporting and id1 is already paused
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var group =
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
            .addMember(
                id0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, exportingConfig))))
            .addMember(
                id1,
                BrokerPartitionState.initialize(Map.of(2, PartitionState.active(1, pausedConfig))));
    final var clusterConfiguration = withPartitionGroups(Map.of(TENANT_A, group));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — only the member not yet in the target state gets an operation
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations().get(TENANT_A))
        .containsExactly(new ExportingStateChangeOperation(id0, ExportingState.PAUSED));
  }

  @Test
  void shouldChangeAMemberWithAnyPartitionNotInTheTargetState() {
    // given — id0 has one paused and one exporting partition, request pauses
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var group =
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
            .addMember(
                id0,
                BrokerPartitionState.initialize(
                    Map.of(
                        1, PartitionState.active(1, pausedConfig),
                        2, PartitionState.active(1, exportingConfig))));
    final var clusterConfiguration = withPartitionGroups(Map.of(TENANT_A, group));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — one operation for the member, not one per partition
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations().get(TENANT_A))
        .containsExactly(new ExportingStateChangeOperation(id0, ExportingState.PAUSED));
  }

  @Test
  void shouldGenerateOperationsOnlyForTheGivenPhysicalTenant() {
    // given
    final var transformer =
        new ExportingStateChangeRequestTransformer(ExportingState.PAUSED, Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(exportingConfig),
                TENANT_B, groupWithMembers(exportingConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — tenant-b keeps exporting
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations())
        .containsOnlyKeys(TENANT_A)
        .hasEntrySatisfying(
            TENANT_A,
            operations ->
                assertThat(operations)
                    .containsExactlyInAnyOrder(
                        new ExportingStateChangeOperation(id0, ExportingState.PAUSED),
                        new ExportingStateChangeOperation(id1, ExportingState.PAUSED)));
  }

  @Test
  void shouldSucceedWithNoPhasesWhenThePartitionGroupIsAlreadyInTheTargetState() {
    // given
    final var transformer =
        new ExportingStateChangeRequestTransformer(ExportingState.PAUSED, Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, groupWithMembers(pausedConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — idempotent no-op yields an empty plan
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldSucceedWithNoPhasesWhenAllPhysicalTenantsAreAlreadyInTheTargetState() {
    // given
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(pausedConfig),
                TENANT_B, groupWithMembers(pausedConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — idempotent no-op yields an empty plan
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenantId() {
    // given
    final var transformer =
        new ExportingStateChangeRequestTransformer(
            ExportingState.PAUSED, Optional.of("unknown-tenant"));
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, groupWithMembers(exportingConfig)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — the tenant does not exist, so the caller gets 404 rather than 500
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining("unknown-tenant");
  }

  @Test
  void shouldNotChangeABrokerThatHostsNoPartitionOfTheGroup() {
    // given — id0 hosts a partition of the group, id1 owns none
    final var transformer = new ExportingStateChangeRequestTransformer(ExportingState.PAUSED);
    final var group =
        PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
            .addMember(
                id0,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, exportingConfig))))
            .addMember(id1, BrokerPartitionState.initialize(Map.of()));
    final var clusterConfiguration = withPartitionGroups(Map.of(TENANT_A, group));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then — id1 owns no partition, so there is nothing on it to change
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations().get(TENANT_A))
        .containsExactly(new ExportingStateChangeOperation(id0, ExportingState.PAUSED));
  }

  private PartitionGroupConfiguration groupWithMembers(final DynamicPartitionConfig config) {
    return PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION)
        .addMember(
            id0, BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, config))))
        .addMember(
            id1, BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, config))));
  }

  private CurrentClusterConfiguration withPartitionGroups(
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        partitionGroups,
        PhasedChangeState.empty());
  }
}
