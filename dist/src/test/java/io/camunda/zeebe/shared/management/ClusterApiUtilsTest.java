/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ClusterApiUtilsTest {

  @ParameterizedTest
  @MethodSource("provideClusterConfigurationWithExporters")
  void shouldAggregateExporterState(final ExporterConfigParam param) {
    // when
    final var result = ClusterApiUtils.aggregateExporterState(param.configuration());

    // then
    assertThat(result).containsExactlyInAnyOrderElementsOf(param.expectedResult());
  }

  public static Stream<Arguments> provideClusterConfigurationWithExporters() {
    return Stream.of(
        disabledExporters(),
        enabledExporters(),
        enablingExporters(),
        disablingExporters(),
        unknownState());
  }

  private static Arguments unknownState() {
    return Arguments.of(
        Named.of(
            "Unknown State",
            new ExporterConfigParam(
                getConfigWithTwoPartitions(State.ENABLED)
                    .updateMember(
                        member(1),
                        m -> updateExporterState(m, e -> e.disableExporter("exporter-1"))),
                List.of(
                    new ExporterStatus().exporterId("exporter-1").status(StatusEnum.UNKNOWN),
                    new ExporterStatus().exporterId("exporter-2").status(StatusEnum.ENABLED)))));
  }

  private static Arguments disablingExporters() {
    return Arguments.of(
        Named.of(
            "Disabling Exporters",
            new ExporterConfigParam(
                getConfigWithTwoPartitions(State.ENABLED)
                    .startConfigurationChange(
                        List.of(new PartitionDisableExporterOperation(member(1), 1, "exporter-1")))
                    .updateMember(
                        member(2),
                        m -> updateExporterState(m, e -> e.disableExporter("exporter-1"))),
                List.of(
                    new ExporterStatus().exporterId("exporter-1").status(StatusEnum.DISABLING),
                    new ExporterStatus().exporterId("exporter-2").status(StatusEnum.ENABLED)))));
  }

  private static Arguments enablingExporters() {
    return Arguments.of(
        Named.of(
            "Enabling Exporters",
            new ExporterConfigParam(
                getConfigWithTwoPartitions(State.DISABLED)
                    .startConfigurationChange(
                        List.of(
                            new PartitionEnableExporterOperation(
                                member(1), 1, "exporter-1", Optional.empty())))
                    .updateMember(
                        member(2),
                        m -> updateExporterState(m, e -> e.enableExporter("exporter-1", 2))),
                List.of(
                    new ExporterStatus().exporterId("exporter-1").status(StatusEnum.ENABLING),
                    new ExporterStatus().exporterId("exporter-2").status(StatusEnum.DISABLED)))));
  }

  private static Arguments enabledExporters() {
    return Arguments.of(
        Named.of(
            "Enabled Exporters",
            new ExporterConfigParam(
                getConfigWithTwoPartitions(State.ENABLED),
                List.of(
                    new ExporterStatus().exporterId("exporter-1").status(StatusEnum.ENABLED),
                    new ExporterStatus().exporterId("exporter-2").status(StatusEnum.ENABLED)))));
  }

  private static Arguments disabledExporters() {
    return Arguments.of(
        Named.of(
            "Disabled Exporters",
            new ExporterConfigParam(
                getConfigWithTwoPartitions(State.DISABLED),
                List.of(
                    new ExporterStatus().exporterId("exporter-1").status(StatusEnum.DISABLED),
                    new ExporterStatus().exporterId("exporter-2").status(StatusEnum.DISABLED)))));
  }

  private static ClusterConfiguration getConfigWithTwoPartitions(final State exporterState) {
    final DynamicPartitionConfig partitionConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(
                    "exporter-1",
                    new ExporterState(0, exporterState, Optional.empty()),
                    "exporter-2",
                    new ExporterState(0, exporterState, Optional.empty()))));
    return ClusterConfiguration.init()
        .addMember(
            member(1),
            MemberState.initializeAsActive(
                Map.of(
                    1,
                    PartitionState.active(1, partitionConfig),
                    2,
                    PartitionState.active(2, partitionConfig))))
        .addMember(
            member(2),
            MemberState.initializeAsActive(
                Map.of(
                    1,
                    PartitionState.active(2, partitionConfig),
                    2,
                    PartitionState.active(2, partitionConfig))));
  }

  private static MemberState updateExporterState(
      final MemberState m, final UnaryOperator<ExportersConfig> exporterUpdater) {
    return m.updatePartition(1, p -> p.updateConfig(c -> c.updateExporting(exporterUpdater)));
  }

  private static MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }

  private record ExporterConfigParam(
      ClusterConfiguration configuration, List<ExporterStatus> expectedResult) {}
}
