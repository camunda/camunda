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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
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

  @ParameterizedTest
  @MethodSource("generateAllClusterConfigurationChangeOperationsAsArguments")
  void shouldMapClusterChangeOperation(final ClusterConfigurationChangeOperation operation) {
    final var encoded = ClusterApiUtils.mapOperation(operation);
    assertThat(encoded).isNotNull();
    assertThat(OperationEnum.values())
        .as("Operation " + operation + "is not mapped correctly")
        .contains(encoded.getOperation());
  }

  @Test
  void shouldGenerateAllClusterConfigurationChangeOperationImplementations() {
    // when
    final var allOperations = generateAllClusterConfigurationChangeOperations();

    // Get all concrete implementation classes from generated operations
    final Set<Class<?>> actualImplementationClasses = new HashSet<>();
    for (final var operation : allOperations) {
      actualImplementationClasses.add(operation.getClass());
    }

    // Dynamically discover all implementations using reflection
    final Set<Class<?>> discoveredImplementationClasses = discoverAllImplementations();

    // This is the key test - verify that our generator includes ALL discovered implementations
    assertThat(actualImplementationClasses)
        .as(
            "Generator method must include ALL ClusterConfigurationChangeOperation implementations. "
                + "Missing implementations: %s. "
                + "If you added a new implementation, make sure to add it to generateAllClusterConfigurationChangeOperations()",
            discoveredImplementationClasses.stream()
                .filter(clazz -> !actualImplementationClasses.contains(clazz))
                .map(Class::getSimpleName)
                .toList())
        .containsExactlyInAnyOrderElementsOf(discoveredImplementationClasses);
  }

  /**
   * Discovers all concrete implementations of ClusterConfigurationChangeOperation using reflection.
   * This method automatically finds all nested classes that implement the interface.
   */
  private Set<Class<?>> discoverAllImplementations() {
    final Set<Class<?>> implementations = new HashSet<>();

    // Get all nested classes from the ClusterConfigurationChangeOperation interface
    final Class<?>[] nestedClasses = ClusterConfigurationChangeOperation.class.getDeclaredClasses();
    if (!ClusterConfigurationChangeOperation.class.isSealed()) {
      throw new AssertionError("ClusterConfigurationChangeOperation must be sealed");
    }

    for (final Class<?> nestedClass : nestedClasses) {
      // Check if it's a record that implements ClusterConfigurationChangeOperation
      if (nestedClass.isRecord()
          && ClusterConfigurationChangeOperation.class.isAssignableFrom(nestedClass)) {
        implementations.add(nestedClass);
      }

      // Also check nested classes within nested interfaces (like ScaleUpOperation,
      // PartitionChangeOperation)
      if (nestedClass.isInterface()) {
        final Class<?>[] subNestedClasses = nestedClass.getDeclaredClasses();
        for (final Class<?> subNestedClass : subNestedClasses) {
          if (subNestedClass.isRecord()
              && ClusterConfigurationChangeOperation.class.isAssignableFrom(subNestedClass)) {
            implementations.add(subNestedClass);
          }
        }
      }
    }

    return implementations;
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

  /**
   * Generates all implementations of ClusterConfigurationChangeOperation interface. This method
   * creates instances of all concrete record implementations with sample data.
   *
   * @return a list containing one instance of each ClusterConfigurationChangeOperation
   *     implementation
   */
  public static List<ClusterConfigurationChangeOperation>
      generateAllClusterConfigurationChangeOperations() {
    final MemberId memberId1 = member(1);
    final MemberId memberId2 = member(2);
    final SortedSet<Integer> partitionSet = new TreeSet<>(Set.of(1, 2, 3));
    final Collection<MemberId> memberCollection = List.of(memberId1, memberId2);
    final Optional<RoutingState> emptyRoutingState = Optional.empty();
    final Optional<String> emptyExporterId = Optional.empty();
    final Optional<DynamicPartitionConfig> emptyConfig = Optional.empty();

    return List.of(
        // Basic member operations
        new MemberJoinOperation(memberId1),
        new MemberLeaveOperation(memberId1),
        new MemberRemoveOperation(memberId1, memberId2),
        new DeleteHistoryOperation(memberId1),
        new UpdateRoutingState(memberId1, emptyRoutingState),

        // Scale up operations
        new StartPartitionScaleUp(memberId1, 8),
        new AwaitRedistributionCompletion(memberId1, 8, partitionSet),
        new AwaitRelocationCompletion(memberId1, 8, partitionSet),

        // Partition change operations
        new PartitionJoinOperation(memberId1, 1, 1),
        new PartitionLeaveOperation(memberId1, 1, 3),
        new PartitionReconfigurePriorityOperation(memberId1, 1, 2),
        new PartitionForceReconfigureOperation(memberId1, 1, memberCollection),
        new PartitionDisableExporterOperation(memberId1, 1, "test-exporter"),
        new PartitionDeleteExporterOperation(memberId1, 1, "test-exporter"),
        new PartitionEnableExporterOperation(memberId1, 1, "test-exporter", emptyExporterId),
        new PartitionBootstrapOperation(memberId1, 1, 1, emptyConfig, false),
        new PartitionBootstrapOperation(memberId1, 2, 1, true) // Alternative constructor
        );
  }

  /** Provides all ClusterConfigurationChangeOperation implementations as test arguments. */
  public static Stream<Arguments> generateAllClusterConfigurationChangeOperationsAsArguments() {
    return generateAllClusterConfigurationChangeOperations().stream().map(Arguments::of);
  }

  private record ExporterConfigParam(
      ClusterConfiguration configuration, List<ExporterStatus> expectedResult) {}
}
