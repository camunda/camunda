/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ClusterConfigurationModifierTest {

  private final MemberId localMemberId = MemberId.from("0");
  private final ConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig config =
      new DynamicPartitionConfig(
          new ExportersConfig(
              Map.of(
                  "expA",
                  new ExporterState(0, State.ENABLED, Optional.empty()),
                  "expB",
                  new ExporterState(0, State.ENABLED, Optional.empty()))));

  final ClusterConfiguration currentConfiguration =
      ClusterConfiguration.init()
          .addMember(
              localMemberId,
              MemberState.initializeAsActive(
                  Map.of(
                      1, PartitionState.active(1, config), 2, PartitionState.active(2, config))));

  @Nested
  final class RoutingStateInitializerTest {
    @Test
    void shouldNotInitializeRoutingStateIfPartitionScalingIsDisabled() {
      // given
      final var routingStateInitializer = new RoutingStateInitializer(false, 3);

      // when
      final var newConfiguration = routingStateInitializer.modify(currentConfiguration).join();

      // then
      ClusterConfigurationAssert.assertThatClusterTopology(newConfiguration).hasNoRoutingState();
    }

    @Test
    void shouldInitializeRoutingStateIfPartitionScalingIsEnabled() {
      // given
      final var routingStateInitializer = new RoutingStateInitializer(true, 5);

      // when
      final var newConfiguration = routingStateInitializer.modify(currentConfiguration).join();

      // then
      ClusterConfigurationAssert.assertThatClusterTopology(newConfiguration).hasRoutingState();
      ClusterConfigurationAssert.assertThatClusterTopology(newConfiguration)
          .routingState()
          .hasVersion(1)
          .hasActivatedPartitions(5)
          .correlatesMessagesToPartitions(5);
    }
  }

  @Nested
  class ExporterStateInitializerTest {

    @ParameterizedTest
    @MethodSource("provideConfigs")
    void shouldUpdateExporterConfig(final ExporterConfigParameter parameter) {
      // given
      final var exporterStateInitializer =
          new ExporterStateInitializer(parameter.configuredExporters(), localMemberId, executor);

      // when
      final var newConfiguration = exporterStateInitializer.modify(currentConfiguration).join();

      // then
      ClusterConfigurationAssert.assertThatClusterTopology(newConfiguration)
          .member(localMemberId)
          .hasPartitionSatisfying(
              1,
              partition ->
                  PartitionStateAssert.assertThat(partition).hasConfig(parameter.expectedConfig()))
          .hasPartitionSatisfying(
              2,
              partition ->
                  PartitionStateAssert.assertThat(partition).hasConfig(parameter.expectedConfig()));
    }

    @Test
    void shouldUpdateExporterConfigOnFirstUpdateToV86() {
      // given
      final ClusterConfiguration currentConfiguration =
          ClusterConfiguration.init()
              .addMember(
                  localMemberId,
                  MemberState.initializeAsActive(
                      Map.of(
                          1,
                          PartitionState.active(1, DynamicPartitionConfig.uninitialized()),
                          2,
                          PartitionState.active(2, DynamicPartitionConfig.uninitialized()))));

      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportersConfig(
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));

      // when
      final var newConfiguration =
          new ExporterStateInitializer(Set.of("expA", "expB"), localMemberId, executor)
              .modify(currentConfiguration)
              .join();

      // then
      ClusterConfigurationAssert.assertThatClusterTopology(newConfiguration)
          .member(localMemberId)
          .hasPartitionSatisfying(
              1, partition -> PartitionStateAssert.assertThat(partition).hasConfig(expectedConfig))
          .hasPartitionSatisfying(
              2, partition -> PartitionStateAssert.assertThat(partition).hasConfig(expectedConfig));
    }

    @Test
    void shouldNotUpdateMemberStateIfNoExporterChanges() {
      // when
      final var newConfiguration =
          new ExporterStateInitializer(Set.of("expA", "expB"), localMemberId, executor)
              .modify(currentConfiguration)
              .join();

      // then
      assertThat(newConfiguration).isEqualTo(currentConfiguration);
    }

    public static Stream<Arguments> provideConfigs() {
      return Stream.of(exporterAdded(), exporterRemoved(), exporterAddedAndRemoved());
    }

    private static Arguments exporterAddedAndRemoved() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportersConfig(
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.DISABLED, Optional.empty()),
                      "exporter1",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter2",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));

      return Arguments.of(
          Named.of(
              "Exporters Added and Removed",
              new ExporterConfigParameter(
                  Set.of("exporter1", "exporter2", "expA"), expectedConfig)));
    }

    private static Arguments exporterRemoved() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportersConfig(
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.DISABLED, Optional.empty()))));
      return Arguments.of(
          Named.of(
              "Exporters Removed", new ExporterConfigParameter(Set.of("expA"), expectedConfig)));
    }

    private static Arguments exporterAdded() {
      final var expectedConfig =
          new DynamicPartitionConfig(
              new ExportersConfig(
                  Map.of(
                      "expA",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "expB",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter1",
                      new ExporterState(0, State.ENABLED, Optional.empty()),
                      "exporter2",
                      new ExporterState(0, State.ENABLED, Optional.empty()))));
      return Arguments.of(
          Named.of(
              "New Exporters Added",
              new ExporterConfigParameter(
                  Set.of("expA", "expB", "exporter1", "exporter2"), expectedConfig)));
    }

    private record ExporterConfigParameter(
        Set<String> configuredExporters, DynamicPartitionConfig expectedConfig) {}
  }
}
