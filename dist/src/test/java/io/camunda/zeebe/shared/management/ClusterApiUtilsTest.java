/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.zeebe.management.cluster.Operation.OperationEnum.BROKER_REMOVE;
import static io.camunda.zeebe.management.cluster.Operation.OperationEnum.PARTITION_JOIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.FixedConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.ConfigurationChange;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExporterStatus.StatusEnum;
import io.camunda.zeebe.management.cluster.GetConfigurationChangesResponse;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.PhysicalTenantState;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.TopologyChangeCompletedInner;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
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
import org.junit.jupiter.params.provider.ValueSource;

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
    final var encoded = ClusterApiUtils.mapOperation(null, operation);
    assertThat(encoded).isNotNull();
    assertThat(encoded.getOperation()).isNotEqualTo(OperationEnum.UNKNOWN);
    assertThat(OperationEnum.values())
        .as("Operation " + operation + "is not mapped correctly")
        .contains(encoded.getOperation());
  }

  @ParameterizedTest
  @MethodSource("generateAllClusterConfigurationChangeOperationsAsArguments")
  void shouldMapClusterCompletedOperation(final ClusterConfigurationChangeOperation operation) {
    final var encoded =
        ClusterApiUtils.mapCompletedOperation(
            new CompletedOperation(operation, Instant.ofEpochSecond(17172371723L)));
    assertThat(encoded).isNotNull();
    assertThat(encoded.getOperation()).isNotEqualTo(OperationEnum.UNKNOWN);
    assertThat(TopologyChangeCompletedInner.OperationEnum.values())
        .as("Operation " + operation + "is not mapped correctly")
        .contains(encoded.getOperation());
  }

  @ParameterizedTest
  @MethodSource("generateAllClusterConfigurationChangeOperationsAsArguments")
  void shouldTagOnlyPartitionGroupOperationsWithPhysicalTenant(
      final ClusterConfigurationChangeOperation operation) {
    // when
    final var encoded = ClusterApiUtils.mapOperation("tenant-a", operation);

    // then
    if (operation instanceof PartitionGroupOperation) {
      assertThat(encoded.getPhysicalTenant()).isEqualTo("tenant-a");
    } else {
      assertThat(encoded.getPhysicalTenant()).isNull();
    }
  }

  @Test
  void shouldMapPhasedResponseIntoPlannedChangesTaggedByTenant() {
    // given
    final var globalOperation = new MemberJoinOperation(member(1));
    final var tenantAOperation = new DeleteHistoryOperation(member(1));
    final var tenantBOperation = new DeleteHistoryOperation(member(2));
    final List<Phase> phases =
        List.of(
            new GlobalPhase(List.of(globalOperation)),
            new PartitionGroupParallelPhase(
                Map.of(
                    "tenant-a", List.of(tenantAOperation),
                    "tenant-b", List.of(tenantBOperation))));
    final var response =
        new ClusterConfigurationChangeResponse(
            1,
            new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
            new CurrentConfigurationChangeResponse(
                CurrentClusterConfiguration.init(), CurrentClusterConfiguration.init(), phases));

    // when
    final var result = ClusterApiUtils.mapOperationResponse(Either.right(response));

    // then
    assertThat(result.getStatusCode().value()).isEqualTo(202);
    final var body = (PlannedOperationsResponse) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getPlannedChanges())
        .filteredOn(op -> op.getPhysicalTenant() == null)
        .extracting(op -> op.getOperation())
        .containsExactly(OperationEnum.BROKER_ADD);
    assertThat(body.getPlannedChanges())
        .filteredOn(op -> "tenant-a".equals(op.getPhysicalTenant()))
        .extracting(op -> op.getOperation())
        .containsExactly(OperationEnum.DELETE_HISTORY);
    assertThat(body.getPlannedChanges())
        .filteredOn(op -> "tenant-b".equals(op.getPhysicalTenant()))
        .extracting(op -> op.getOperation())
        .containsExactly(OperationEnum.DELETE_HISTORY);
  }

  @Test
  void shouldFallBackToLegacyPlannedChangesWhenMultiConfigurationResponseIsAbsent() {
    // given — a peer that hasn't populated the new multi-partition-group response, e.g. one still
    // running old code (see ClusterConfigurationChangeResponse.response()).
    final var operation = new MemberLeaveOperation(member(1));
    final var response =
        new ClusterConfigurationChangeResponse(
            1, new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of(operation)), null);

    // when
    final var result = ClusterApiUtils.mapOperationResponse(Either.right(response));

    // then
    final var body = (PlannedOperationsResponse) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getPlannedChanges())
        .extracting(op -> op.getOperation(), op -> op.getPhysicalTenant())
        .containsExactly(tuple(BROKER_REMOVE, null));
  }

  @Test
  void shouldFlattenPartitionsFromAllPhysicalTenantsOntoTheOwningBroker() {
    // given — broker 1 participates in both tenants, broker 2 only in tenant-b, and is recovering
    // there.
    final var member1 = member(1);
    final var member2 = member(2);
    final var globalConfiguration =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                member1,
                new io.camunda.zeebe.dynamic.config.state.BrokerState(
                    0,
                    Instant.ofEpochSecond(1),
                    io.camunda.zeebe.dynamic.config.state.BrokerState.State.ACTIVE),
                member2,
                new io.camunda.zeebe.dynamic.config.state.BrokerState(
                    0,
                    Instant.ofEpochSecond(2),
                    io.camunda.zeebe.dynamic.config.state.BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var tenantA =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var tenantB =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(2, PartitionState.active(1, DynamicPartitionConfig.init()))),
                member2,
                BrokerPartitionState.initialize(
                        Map.of(2, PartitionState.active(1, DynamicPartitionConfig.init())))
                    .setMode(Mode.RECOVERING)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var multiConfig =
        new CurrentClusterConfiguration(
            0,
            globalConfiguration,
            Map.of("tenant-a", tenantA, "tenant-b", tenantB),
            PhasedChangeState.empty());
    final var response =
        new ClusterConfigurationChangeResponse(
            1,
            new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
            new CurrentConfigurationChangeResponse(multiConfig, multiConfig, List.of()));

    // when
    final var result = ClusterApiUtils.mapOperationResponse(Either.right(response));

    // then
    final var body = (PlannedOperationsResponse) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCurrentTopology())
        .extracting(BrokerState::getId)
        .extracting(String::valueOf)
        .containsExactlyInAnyOrder("1", "2");

    final var broker1 =
        body.getCurrentTopology().stream()
            .filter(b -> "1".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker1.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactlyInAnyOrder(tuple(1, "tenant-a"), tuple(2, "tenant-b"));
    assertThat(broker1.getPhysicalTenants())
        .extracting(PhysicalTenantState::getId, PhysicalTenantState::getMode)
        .containsExactlyInAnyOrder(
            tuple("tenant-a", PhysicalTenantState.ModeEnum.PROCESSING),
            tuple("tenant-b", PhysicalTenantState.ModeEnum.PROCESSING));

    final var broker2 =
        body.getCurrentTopology().stream()
            .filter(b -> "2".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker2.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactly(tuple(2, "tenant-b"));
    assertThat(broker2.getPhysicalTenants())
        .extracting(PhysicalTenantState::getId, PhysicalTenantState::getMode)
        .containsExactly(tuple("tenant-b", PhysicalTenantState.ModeEnum.RECOVERING));
  }

  @Test
  void shouldUseBrokerIdAsIntegerForNonZoneAwareBroker() {
    // given
    final var memberId = MemberId.from("1");
    final var operation = new MemberJoinOperation(memberId);

    // when
    final var encoded = ClusterApiUtils.mapOperation(null, operation);

    // then
    assertThat(encoded.getBrokerId()).isEqualTo(1);
  }

  @Test
  void shouldUseBrokerIdAsStringForZoneAwareBroker() {
    // given
    final var memberId = MemberId.from("zone-a", 0);
    final var operation = new MemberJoinOperation(memberId);

    // when
    final var encoded = ClusterApiUtils.mapOperation(null, operation);

    // then
    assertThat(encoded.getBrokerId()).isEqualTo("zone-a_0");
  }

  @Test
  void shouldUseBrokerIdAsStringInCompletedOperationForZoneAwareBroker() {
    // given
    final var memberId = MemberId.from("zone-b", 2);
    final var operation = new MemberLeaveOperation(memberId);

    // when
    final var encoded =
        ClusterApiUtils.mapCompletedOperation(
            new CompletedOperation(operation, Instant.ofEpochSecond(17172371723L)));

    // then
    assertThat(encoded.getBrokerId()).isEqualTo("zone-b_2");
  }

  @Test
  void shouldUseBrokerIdAsStringInTopologyResponseForZoneAwareBrokers() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("zone-a", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("zone-b", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));

    // when
    final var response = ClusterApiUtils.mapClusterTopologyResponse(Either.right(config));

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (GetTopologyResponse) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getBrokers())
        .extracting(b -> b.getId().value())
        .containsExactlyInAnyOrder("zone-a_0", "zone-b_0");
  }

  @Test
  void shouldUseBrokerIdAsIntegerInTopologyResponseForNonZoneAwareBrokers() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));

    // when
    final var response = ClusterApiUtils.mapClusterTopologyResponse(Either.right(config));

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (GetTopologyResponse) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getBrokers())
        .extracting(BrokerState::getId)
        .extracting(String::valueOf)
        .containsExactlyInAnyOrder("0", "1");
  }

  @Test
  void shouldMapRecoveringPartitionState() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, DynamicPartitionConfig.init()).toRecovering())));

    // when
    final var response = ClusterApiUtils.mapClusterTopologyResponse(Either.right(config));

    // then
    final var body = (GetTopologyResponse) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getBrokers())
        .flatExtracting(BrokerState::getPartitions)
        .extracting(io.camunda.zeebe.management.cluster.PartitionState::getState)
        .containsExactly(io.camunda.zeebe.management.cluster.PartitionStateCode.RECOVERING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ZONE_AWARE", "FIXED", "ROUND_ROBIN"})
  void shouldIncludePartitionDistributorConfig(final String type) {
    // given
    final var expectedConfig =
        new io.camunda.zeebe.management.cluster.PartitionDistributionConfig();
    expectedConfig.type(TypeEnum.valueOf(type));
    final PartitionDistributorConfig partitionDistributorConfig;
    switch (type) {
      case "ZONE_AWARE" -> {
        partitionDistributorConfig =
            new PartitionDistributorConfig.ZoneAwareConfig(
                List.of(new ZoneSpec("zone-a", 3, 1000)));
        expectedConfig.setZones(
            List.of(new io.camunda.zeebe.management.cluster.ZoneSpec("zone-a", 3, 1000)));
      }
      case "ROUND_ROBIN" -> {
        partitionDistributorConfig = new RoundRobinConfig();
      }
      case "FIXED" -> {
        partitionDistributorConfig = new FixedConfig();
      }
      default -> throw new IllegalArgumentException("Invalid type: " + type);
    }
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))))
            .setPartitionDistributorConfig(partitionDistributorConfig);

    // when
    final var response = ClusterApiUtils.mapClusterTopologyResponse(Either.right(config));

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    final var body = (GetTopologyResponse) response.getBody();
    assertThat(body.getPartitionDistribution()).isEqualTo(expectedConfig);
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
   * Discovers all concrete record implementations of ClusterConfigurationChangeOperation by
   * recursively walking the sealed {@code permits} hierarchy.
   *
   * <p>This works regardless of whether subtypes are nested inside the top-level interface or
   * defined in separate top-level files, because Java's {@code getPermittedSubclasses()} follows
   * the compiler-enforced {@code permits} clause rather than physical class nesting.
   */
  private Set<Class<?>> discoverAllImplementations() {
    if (!ClusterConfigurationChangeOperation.class.isSealed()) {
      throw new AssertionError("ClusterConfigurationChangeOperation must be sealed");
    }
    final Set<Class<?>> implementations = new HashSet<>();
    collectConcretePermittedSubtypes(ClusterConfigurationChangeOperation.class, implementations);
    return implementations;
  }

  private static void collectConcretePermittedSubtypes(
      final Class<?> sealedType, final Set<Class<?>> result) {
    for (final Class<?> permitted : sealedType.getPermittedSubclasses()) {
      if (permitted.isRecord()) {
        result.add(permitted);
      } else if (permitted.isSealed()) {
        collectConcretePermittedSubtypes(permitted, result);
      }
    }
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
            new ExportingConfig(
                ExportingState.EXPORTING,
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
      final MemberState m, final UnaryOperator<ExportingConfig> exporterUpdater) {
    return m.updatePartition(1, p -> p.updateConfig(c -> c.updateExporting(exporterUpdater)));
  }

  private static MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }

  private static CurrentClusterConfiguration configWithPhasedChangeState(
      final PhasedChangeState phasedChangeState) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        Map.of(),
        phasedChangeState);
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
    final Set<MemberId> memberCollection = Set.of(memberId1, memberId2);
    final Optional<RoutingState> emptyRoutingState = Optional.empty();
    final Optional<String> emptyExporterId = Optional.empty();
    final Optional<DynamicPartitionConfig> emptyConfig = Optional.empty();

    return List.of(
        // Basic member operations
        new MemberJoinOperation(memberId1),
        new MemberLeaveOperation(memberId1),
        new MemberRemoveOperation(memberId1, memberId2),

        // General cluster operations
        new DeleteHistoryOperation(memberId1),
        new UpdateRoutingState(memberId1, emptyRoutingState),
        new UpdateIncarnationNumberOperation(memberId1),
        new PreScalingOperation(memberId1, memberCollection),
        new PostScalingOperation(memberId1, memberCollection),

        // Scale up operations
        new StartPartitionScaleUp(memberId1, 8),
        new AwaitRedistributionCompletion(memberId1, 8, partitionSet),
        new AwaitRelocationCompletion(memberId1, 8, partitionSet),

        // Partition change operations
        new PartitionJoinOperation(memberId1, 1, 1),
        new PartitionLeaveOperation(memberId1, 1, 3),
        new PartitionReconfigurePriorityOperation(memberId1, 1, 2),
        new PartitionForceReconfigureOperation(memberId1, 1, memberCollection),
        new PartitionBootstrapOperation(memberId1, 1, 1, emptyConfig, false),
        new PartitionBootstrapOperation(memberId1, 2, 1, true), // Alternative constructor

        // Exporters
        new PartitionDisableExporterOperation(memberId1, 1, "test-exporter"),
        new PartitionDeleteExporterOperation(memberId1, 1, "test-exporter"),
        new PartitionEnableExporterOperation(memberId1, 1, "test-exporter", emptyExporterId),
        new ExportingStateChangeOperation(memberId1, ExportingState.PAUSED),
        new PartitionPreRestoreOperation(memberId1, 1),
        new PartitionRestoreOperation(memberId1, 1, new TreeSet<>(Set.of(1L, 2L))),

        // PartitionDistributorConfig
        new UpdatePartitionDistributorConfigOperation(memberId1, new RoundRobinConfig()),

        // Mode change operations
        new ModeChangeOperation(memberId1, Mode.RECOVERING),
        new ModeChangeOperation(memberId1, Mode.PROCESSING),
        new AwaitModeChangeOperation(memberId1, Mode.RECOVERING),
        new AwaitModeChangeOperation(memberId1, Mode.PROCESSING));
  }

  /** Provides all ClusterConfigurationChangeOperation implementations as test arguments. */
  public static Stream<Arguments> generateAllClusterConfigurationChangeOperationsAsArguments() {
    return generateAllClusterConfigurationChangeOperations().stream().map(Arguments::of);
  }

  @Test
  void shouldReturnEmptyChangeListWhenNoChanges() {
    // given
    final var config = CurrentClusterConfiguration.init();

    // when
    final var response = ClusterApiUtils.mapConfigurationChangesResponse(config);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (GetConfigurationChangesResponse) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getChanges()).isEmpty();
  }

  @Test
  void shouldListPendingAndLastCompletedChange() {
    // given
    final var startedAt = Instant.ofEpochSecond(1000);
    final var lastCompletedAt = Instant.ofEpochSecond(500);
    final var pendingPlan =
        new PhasedChangePlan(
            2, 0, List.of(new GlobalPhase(List.of(new MemberJoinOperation(member(1))))), startedAt);
    final var lastChange =
        new CompletedPhasedChange(1, PhasedChangePlanStatus.COMPLETED, startedAt, lastCompletedAt);
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(Optional.of(pendingPlan), Optional.of(lastChange)));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangesResponse(config);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (GetConfigurationChangesResponse) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getChanges())
        .extracting(ConfigurationChange::getId, ConfigurationChange::getStatus)
        .containsExactlyInAnyOrder(
            tuple(2L, ConfigurationChange.StatusEnum.IN_PROGRESS),
            tuple(1L, ConfigurationChange.StatusEnum.COMPLETED));
  }

  @Test
  void shouldMapPendingOperationsOfInProgressChange() {
    // given
    final var memberId1 = member(1);
    final List<Phase> phases =
        List.of(
            new PartitionGroupParallelPhase(
                Map.of("default", List.of(new PartitionJoinOperation(memberId1, 1, 3)))),
            new GlobalPhase(List.of(new MemberLeaveOperation(memberId1))));
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(
                Optional.of(new PhasedChangePlan(2, 0, phases, Instant.now())), Optional.empty()));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangeResponse(config, 2);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (ConfigurationChange) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getId()).isEqualTo(2L);
    assertThat(body.getStatus()).isEqualTo(ConfigurationChange.StatusEnum.IN_PROGRESS);
    assertThat(body.getCompleted()).isEmpty();
    assertThat(body.getPending())
        .extracting(Operation::getOperation)
        .containsExactly(PARTITION_JOIN, BROKER_REMOVE);
  }

  @Test
  void shouldSplitActivePhaseOperationsUsingSubConfigProgress() {
    // given: the active phase targets physical tenant "tenant-a" with two operations, but the
    // tenant's own ClusterChangePlan shows the join already completed and only the leave pending
    final var memberId1 = member(1);
    final var joinOperation = new PartitionJoinOperation(memberId1, 3, 1);
    final var leaveOperation = new PartitionLeaveOperation(memberId1, 3, 0);
    final var tenantPlan =
        new ClusterChangePlan(
            1,
            1,
            Status.IN_PROGRESS,
            Instant.now(),
            List.of(new CompletedOperation(joinOperation, Instant.now())),
            List.of(leaveOperation));
    final var tenantGroup =
        new PartitionGroupConfiguration(
            1, 0, Map.of(), Optional.empty(), Optional.of(tenantPlan), Optional.empty());
    final List<Phase> phases =
        List.of(
            new PartitionGroupParallelPhase(
                Map.of("tenant-a", List.of(joinOperation, leaveOperation))),
            new GlobalPhase(List.of(new MemberLeaveOperation(memberId1))));
    final var config =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", tenantGroup),
            new PhasedChangeState(
                Optional.of(new PhasedChangePlan(7, 0, phases, Instant.now())), Optional.empty()));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangeResponse(config, 7);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (ConfigurationChange) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getCompleted())
        .extracting(Operation::getOperation, Operation::getPhysicalTenant)
        .containsExactly(tuple(PARTITION_JOIN, "tenant-a"));
    assertThat(body.getPending())
        .extracting(Operation::getOperation, Operation::getPhysicalTenant)
        .containsExactly(
            tuple(Operation.OperationEnum.PARTITION_LEAVE, "tenant-a"), tuple(BROKER_REMOVE, null));
  }

  @Test
  void shouldReturnCompletedChangeWithoutOperations() {
    // given
    final var startedAt = Instant.ofEpochSecond(1000);
    final var completedAt = Instant.ofEpochSecond(2000);
    final var lastChange =
        new CompletedPhasedChange(5, PhasedChangePlanStatus.FAILED, startedAt, completedAt);
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(Optional.empty(), Optional.of(lastChange)));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangeResponse(config, 5);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var body = (ConfigurationChange) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getId()).isEqualTo(5L);
    assertThat(body.getStatus()).isEqualTo(ConfigurationChange.StatusEnum.FAILED);
    assertThat(body.getCompleted()).isEmpty();
    assertThat(body.getPending()).isEmpty();
  }

  @Test
  void shouldReturn404WhenChangeIdIsUnknown() {
    // given
    final List<Phase> phases =
        List.of(new GlobalPhase(List.of(new MemberJoinOperation(member(1)))));
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(
                Optional.of(new PhasedChangePlan(2, 0, phases, Instant.now())), Optional.empty()));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangeResponse(config, 999);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    final var body = (Error) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getMessage()).contains("999");
  }

  private record ExporterConfigParam(
      ClusterConfiguration configuration, List<ExporterStatus> expectedResult) {}
}
