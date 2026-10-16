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
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ExporterEnableRequestTransformerTest {

  private static final String EXPORTER_ID = "exporterA";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");

  private final DynamicPartitionConfig configWithDisabledExporter =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of(EXPORTER_ID, new ExporterState(1, State.DISABLED, Optional.empty()))));

  private final DynamicPartitionConfig configWithEnabledExporter =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of(EXPORTER_ID, new ExporterState(1, State.ENABLED, Optional.empty()))));

  private final DynamicPartitionConfig configWithoutExporter =
      new DynamicPartitionConfig(new ExportingConfig(ExportingState.EXPORTING, Map.of()));

  @Test
  void shouldGenerateOperationsForAllPhysicalTenantsWhenNoneIsGiven() {
    // given
    final var transformer = new ExporterEnableRequestTransformer(EXPORTER_ID, Optional.empty());
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(configWithDisabledExporter),
                TENANT_B, groupWithMembers(configWithDisabledExporter)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations())
        .containsOnlyKeys(TENANT_A, TENANT_B)
        .allSatisfy(
            (groupId, operations) ->
                assertThat(operations)
                    .containsExactlyInAnyOrder(
                        new PartitionEnableExporterOperation(id0, 1, EXPORTER_ID, Optional.empty()),
                        new PartitionEnableExporterOperation(
                            id1, 1, EXPORTER_ID, Optional.empty())));
  }

  @Test
  void shouldGenerateOperationsForATenantThatHasNeverConfiguredTheExporter() {
    // given — enable does not require the exporter to already exist on the partition
    final var transformer = new ExporterEnableRequestTransformer(EXPORTER_ID, Optional.empty());
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, groupWithMembers(configWithoutExporter)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations()).containsOnlyKeys(TENANT_A);
  }

  @Test
  void shouldGenerateOperationsOnlyForTheGivenPhysicalTenant() {
    // given
    final var transformer =
        new ExporterEnableRequestTransformer(EXPORTER_ID, Optional.empty(), Optional.of(TENANT_A));
    final var clusterConfiguration =
        withPartitionGroups(
            Map.of(
                TENANT_A, groupWithMembers(configWithDisabledExporter),
                TENANT_B, groupWithMembers(configWithDisabledExporter)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final var phase = (PartitionGroupPhase) result.get().getFirst();
    assertThat(phase.groupOperations())
        .containsOnlyKeys(TENANT_A)
        .hasEntrySatisfying(
            TENANT_A,
            operations ->
                assertThat(operations)
                    .containsExactlyInAnyOrder(
                        new PartitionEnableExporterOperation(id0, 1, EXPORTER_ID, Optional.empty()),
                        new PartitionEnableExporterOperation(
                            id1, 1, EXPORTER_ID, Optional.empty())));
  }

  @Test
  void shouldRejectAnUnknownPhysicalTenantId() {
    // given
    final var transformer =
        new ExporterEnableRequestTransformer(
            EXPORTER_ID, Optional.empty(), Optional.of("unknown-tenant"));
    final var clusterConfiguration =
        withPartitionGroups(Map.of(TENANT_A, groupWithMembers(configWithDisabledExporter)));

    // when
    final var result = transformer.phases(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining("unknown-tenant");
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
