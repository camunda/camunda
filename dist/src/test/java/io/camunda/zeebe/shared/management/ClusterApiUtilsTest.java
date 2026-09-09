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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
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
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
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
import io.camunda.zeebe.management.cluster.TopologyChange;
import io.camunda.zeebe.management.cluster.TopologyChangeCompletedInner;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
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

  @Test
  void shouldTagPhysicalTenantWhenAggregatingUnscopedForSingleTenant() {
    // given
    final var configuration =
        configWithExporters(
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, Map.of("exporter-1", State.ENABLED)));

    // when
    final var result = ClusterApiUtils.aggregateExporterState(configuration, null);

    // then — the tag is present even though a single-tenant caller could have deduced it
    assertThat(result)
        .containsExactly(
            new ExporterStatus()
                .exporterId("exporter-1")
                .status(StatusEnum.ENABLED)
                .physicalTenant(CurrentClusterConfiguration.DEFAULT_GROUP));
  }

  @Test
  void shouldAggregateEachPhysicalTenantOnItsOwnWhenAggregatingUnscoped() {
    // given — the same exporter id is enabled in one tenant and disabled in the other, and each
    // tenant has an exporter the other does not
    final var configuration =
        configWithExporters(
            Map.of(
                "tenant-b",
                Map.of("shared", State.DISABLED, "only-in-b", State.ENABLED),
                "tenant-a",
                Map.of("shared", State.ENABLED, "only-in-a", State.DISABLED)));

    // when
    final var result = ClusterApiUtils.aggregateExporterState(configuration, null);

    // then — grouped by tenant, tenants in ascending id order
    assertThat(result)
        .extracting(
            ExporterStatus::getPhysicalTenant,
            ExporterStatus::getExporterId,
            ExporterStatus::getStatus)
        .containsExactlyInAnyOrder(
            tuple("tenant-a", "shared", StatusEnum.ENABLED),
            tuple("tenant-a", "only-in-a", StatusEnum.DISABLED),
            tuple("tenant-b", "shared", StatusEnum.DISABLED),
            tuple("tenant-b", "only-in-b", StatusEnum.ENABLED));
    assertThat(result).extracting(ExporterStatus::getPhysicalTenant).isSorted();
  }

  @Test
  void shouldAggregateOnlyTheRequestedPhysicalTenant() {
    // given
    final var configuration =
        configWithExporters(
            Map.of(
                "tenant-a",
                Map.of("exporter-1", State.ENABLED),
                "tenant-b",
                Map.of("exporter-2", State.ENABLED)));

    // when
    final var result = ClusterApiUtils.aggregateExporterState(configuration, "tenant-b");

    // then
    assertThat(result)
        .containsExactly(
            new ExporterStatus()
                .exporterId("exporter-2")
                .status(StatusEnum.ENABLED)
                .physicalTenant("tenant-b"));
  }

  @Test
  void shouldAggregateNothingForAPhysicalTenantWithoutExporters() {
    // given
    final var configuration =
        configWithExporters(Map.of("tenant-a", Map.of("exporter-1", State.ENABLED)));

    // when
    final var result = ClusterApiUtils.aggregateExporterState(configuration, "tenant-b");

    // then — an unknown tenant is rejected by the endpoint, so all this needs to do is not fail
    assertThat(result).isEmpty();
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
            PartitionGroupPhase.sequential(
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
  void shouldPopulateLegacyFieldsAndOmitPhysicalTenantsForSingleTenant() {
    // given — a single physical tenant (the default group); the multi-tenant field must not
    // appear, and the response must keep the top-level field set of the pre-physical-tenant shape.
    // The broker's two version counters are set apart deliberately: BrokerState.version counts
    // lifecycle transitions only and BrokerPartitionState.version counts partition and mode
    // changes, so brokers[].version must report the higher of the two the way the legacy
    // MemberState projection did, or a partition join would stop bumping it.
    final var member1 = member(1);
    final var globalConfiguration =
        new GlobalConfiguration(
            5,
            Optional.empty(),
            Map.of(
                member1,
                new io.camunda.zeebe.dynamic.config.state.BrokerState(
                    2,
                    Instant.ofEpochSecond(1),
                    io.camunda.zeebe.dynamic.config.state.BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var defaultGroup =
        new PartitionGroupConfiguration(
            7,
            0,
            Map.of(
                member1,
                new BrokerPartitionState(
                    9,
                    Instant.ofEpochSecond(3),
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                    Mode.PROCESSING)),
            Optional.of(
                new RoutingState(
                    1,
                    new RoutingState.RequestHandling.AllPartitions(1),
                    new RoutingState.MessageCorrelation.HashMod(1))),
            Optional.empty(),
            Optional.empty());
    final var config =
        new CurrentClusterConfiguration(
            0,
            globalConfiguration,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, defaultGroup),
            PhasedChangeState.empty());

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config);

    // then
    assertThat(body.getVersion()).isEqualTo(7);
    assertThat(body.getRouting()).isNotNull();
    assertThat(body.getRouting().getVersion()).isEqualTo(1);
    assertThat(body.getPhysicalTenants()).isNull();
    assertThat(body.getLastChange()).isNull();
    assertThat(body.getPendingChange()).isNull();
    assertThat(body.getBrokers())
        .singleElement()
        .extracting(BrokerState::getVersion, BrokerState::getLastUpdatedAt)
        .containsExactly(9L, Instant.ofEpochSecond(3).atOffset(ZoneOffset.UTC));
  }

  @Test
  void shouldPopulatePhysicalTenantsAndOmitLegacyFieldsForTwoTenants() {
    // given — broker 1 replicates partition 1 in both tenants (partition ids restart per group, so
    // the same id 1 legitimately appears twice, tagged by tenant); broker 2 only in tenant-b.
    final var member1 = member(1);
    final var member2 = member(2);
    final var globalConfiguration =
        new GlobalConfiguration(
            5,
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
            3,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.of(
                new RoutingState(
                    2,
                    new RoutingState.RequestHandling.AllPartitions(1),
                    new RoutingState.MessageCorrelation.HashMod(1))),
            Optional.empty(),
            Optional.empty());
    final var tenantB =
        new PartitionGroupConfiguration(
            4,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))),
                member2,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.of(
                new RoutingState(
                    3,
                    new RoutingState.RequestHandling.AllPartitions(2),
                    new RoutingState.MessageCorrelation.HashMod(2))),
            Optional.empty(),
            Optional.empty());
    final var config =
        new CurrentClusterConfiguration(
            0,
            globalConfiguration,
            Map.of("tenant-a", tenantA, "tenant-b", tenantB),
            PhasedChangeState.empty());

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config);

    // then
    assertThat(body.getVersion()).isNull();
    assertThat(body.getRouting()).isNull();
    assertThat(body.getLastChange()).isNull();
    assertThat(body.getPendingChange()).isNull();
    assertThat(body.getPhysicalTenants())
        .extracting(
            io.camunda.zeebe.management.cluster.PhysicalTenantInfo::getId,
            info -> info.getRouting().getVersion())
        .containsExactly(tuple("tenant-a", 2L), tuple("tenant-b", 3L));
    // per-broker version is omitted alongside the top-level one: the maximum across counters that
    // each tenant advances independently is not the version of anything. lastUpdatedAt survives.
    assertThat(body.getBrokers())
        .allSatisfy(broker -> assertThat(broker.getVersion()).isNull())
        .allSatisfy(broker -> assertThat(broker.getLastUpdatedAt()).isNotNull());

    final var broker1 =
        body.getBrokers().stream()
            .filter(b -> "1".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker1.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactlyInAnyOrder(tuple(1, "tenant-a"), tuple(1, "tenant-b"));

    final var broker2 =
        body.getBrokers().stream()
            .filter(b -> "2".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker2.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactly(tuple(1, "tenant-b"));
  }

  @Test
  void shouldScopeToOnePhysicalTenantAmongTwo() {
    // given — same two-tenant setup as above
    final var member1 = member(1);
    final var member2 = member(2);
    final var globalConfiguration =
        new GlobalConfiguration(
            5,
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
            3,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.of(
                new RoutingState(
                    2,
                    new RoutingState.RequestHandling.AllPartitions(1),
                    new RoutingState.MessageCorrelation.HashMod(1))),
            Optional.empty(),
            Optional.empty());
    final var tenantB =
        new PartitionGroupConfiguration(
            4,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))),
                member2,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.of(
                new RoutingState(
                    3,
                    new RoutingState.RequestHandling.AllPartitions(2),
                    new RoutingState.MessageCorrelation.HashMod(2))),
            Optional.empty(),
            Optional.empty());
    final var config =
        new CurrentClusterConfiguration(
            0,
            globalConfiguration,
            Map.of("tenant-a", tenantA, "tenant-b", tenantB),
            PhasedChangeState.empty());

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config, "tenant-b");

    // then — a scoped request gets the single-tenant top-level shape, and additionally reports the
    // requested tenant under physicalTenants, so a caller reads a tenant's routing state the same
    // way whether or not it scoped. Both routing views are the same group's, so they agree.
    assertThat(body.getVersion()).isEqualTo(5); // max(global=5, tenant-b=4)
    assertThat(body.getRouting()).isNotNull();
    assertThat(body.getRouting().getVersion()).isEqualTo(3);
    assertThat(body.getPhysicalTenants())
        .extracting(
            io.camunda.zeebe.management.cluster.PhysicalTenantInfo::getId,
            info -> info.getRouting().getVersion())
        .containsExactly(tuple("tenant-b", 3L));
    assertThat(body.getBrokers()).allSatisfy(b -> assertThat(b.getVersion()).isNotNull());

    final var broker1 =
        body.getBrokers().stream()
            .filter(b -> "1".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker1.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactly(tuple(1, "tenant-b"));

    final var broker2 =
        body.getBrokers().stream()
            .filter(b -> "2".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker2.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactly(tuple(1, "tenant-b"));
  }

  @Test
  void shouldReportADisabledPhysicalTenantWithoutRoutingOrPartitions() {
    // given — same two-tenant setup as
    // shouldPopulatePhysicalTenantsAndOmitLegacyFieldsForTwoTenants,
    // except tenant-b is disabled: removed from local static configuration, but its partition
    // assignment and data are still retained in the configuration.
    final var member1 = member(1);
    final var member2 = member(2);
    final var globalConfiguration =
        new GlobalConfiguration(
            5,
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
            3,
            0,
            Map.of(
                member1,
                BrokerPartitionState.initialize(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
            Optional.of(
                new RoutingState(
                    2,
                    new RoutingState.RequestHandling.AllPartitions(1),
                    new RoutingState.MessageCorrelation.HashMod(1))),
            Optional.empty(),
            Optional.empty());
    final var tenantB =
        new PartitionGroupConfiguration(
                4,
                0,
                Map.of(
                    member1,
                    BrokerPartitionState.initialize(
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))),
                    member2,
                    BrokerPartitionState.initialize(
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))),
                Optional.of(
                    new RoutingState(
                        3,
                        new RoutingState.RequestHandling.AllPartitions(2),
                        new RoutingState.MessageCorrelation.HashMod(2))),
                Optional.empty(),
                Optional.empty())
            .disable();
    final var config =
        new CurrentClusterConfiguration(
            0,
            globalConfiguration,
            Map.of("tenant-a", tenantA, "tenant-b", tenantB),
            PhasedChangeState.empty());

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config);

    // then — tenant-b is reported as disabled with no routing state, tenant-a is unaffected and
    // never explicitly marked enabled
    assertThat(body.getPhysicalTenants())
        .extracting(
            io.camunda.zeebe.management.cluster.PhysicalTenantInfo::getId,
            io.camunda.zeebe.management.cluster.PhysicalTenantInfo::getDisabled)
        .containsExactly(tuple("tenant-a", null), tuple("tenant-b", true));
    final var tenantAInfo =
        body.getPhysicalTenants().stream()
            .filter(info -> "tenant-a".equals(info.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(tenantAInfo.getRouting()).isNotNull();
    assertThat(tenantAInfo.getRouting().getVersion()).isEqualTo(2);
    final var tenantBInfo =
        body.getPhysicalTenants().stream()
            .filter(info -> "tenant-b".equals(info.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(tenantBInfo.getRouting()).isNull();

    // and — tenant-b's partitions are excluded from every broker's partitions/physicalTenants,
    // exactly as if it had never been configured; tenant-a is untouched
    final var broker1 =
        body.getBrokers().stream()
            .filter(b -> "1".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker1.getPartitions())
        .extracting(p -> p.getId(), p -> p.getPhysicalTenant())
        .containsExactly(tuple(1, "tenant-a"));
    assertThat(broker1.getPhysicalTenants())
        .extracting(io.camunda.zeebe.management.cluster.PhysicalTenantState::getId)
        .containsExactly("tenant-a");

    final var broker2 =
        body.getBrokers().stream()
            .filter(b -> "2".equals(String.valueOf(b.getId())))
            .findFirst()
            .orElseThrow();
    assertThat(broker2.getPartitions()).isEmpty();
    assertThat(broker2.getPhysicalTenants()).isEmpty();
  }

  @Test
  void shouldKeepLegacyUninitializedVersionWhenNoPhysicalTenantsExist() {
    // given — an uninitialized configuration has zero partition groups; it must keep reporting
    // today's sentinel version rather than being (mis-)treated as a multi-tenant response.
    final var config = CurrentClusterConfiguration.uninitialized();

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config);

    // then
    assertThat(body.getVersion()).isEqualTo(-1);
    assertThat(body.getPhysicalTenants()).isNull();
  }

  @Test
  void shouldReportNoPhysicalTenantsWhenScopedAgainstUninitializedConfiguration() {
    // given — an uninitialized configuration has zero partition groups, so a request naming one
    // finds nothing to report. The key stays present, unlike for an unscoped request: the caller
    // asked about physical tenants, and an empty list answers that the named one is not there yet.
    final var config = CurrentClusterConfiguration.uninitialized();

    // when
    final var body = ClusterApiUtils.mapClusterTopology(config, "tenant-a");

    // then
    assertThat(body.getPhysicalTenants()).isEmpty();
    assertThat(body.getVersion()).isEqualTo(-1);
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
    final var response =
        ClusterApiUtils.mapClusterTopologyResponse(
            Either.right(CurrentClusterConfiguration.fromLegacy(config)));

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
    final var response =
        ClusterApiUtils.mapClusterTopologyResponse(
            Either.right(CurrentClusterConfiguration.fromLegacy(config)));

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
    final var response =
        ClusterApiUtils.mapClusterTopologyResponse(
            Either.right(CurrentClusterConfiguration.fromLegacy(config)));

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
    final var response =
        ClusterApiUtils.mapClusterTopologyResponse(
            Either.right(CurrentClusterConfiguration.fromLegacy(config)));

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
                withPendingChange(
                        getConfigWithTwoPartitions(State.ENABLED),
                        new PartitionDisableExporterOperation(member(1), 1, "exporter-1"))
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
                withPendingChange(
                        getConfigWithTwoPartitions(State.DISABLED),
                        new PartitionEnableExporterOperation(
                            member(1), 1, "exporter-1", Optional.empty()))
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

  /**
   * A cluster configuration with one broker holding a single partition per physical tenant, each
   * partition configured with the given exporters in the given states.
   */
  private static CurrentClusterConfiguration configWithExporters(
      final Map<String, Map<String, State>> exportersPerTenant) {
    final var globalConfiguration =
        new GlobalConfiguration(
            1,
            Optional.empty(),
            Map.of(
                member(1),
                new io.camunda.zeebe.dynamic.config.state.BrokerState(
                    0,
                    Instant.ofEpochSecond(1),
                    io.camunda.zeebe.dynamic.config.state.BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final Map<String, PartitionGroupConfiguration> groups = new HashMap<>();
    exportersPerTenant.forEach(
        (tenant, exporters) -> groups.put(tenant, groupWithExporters(exporters)));
    return new CurrentClusterConfiguration(
        1, globalConfiguration, groups, PhasedChangeState.empty());
  }

  private static PartitionGroupConfiguration groupWithExporters(
      final Map<String, State> exporters) {
    final var partitionConfig =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                exporters.entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            Entry::getKey,
                            e -> new ExporterState(0, e.getValue(), Optional.empty())))));
    return new PartitionGroupConfiguration(
        1,
        0,
        Map.of(
            member(1),
            BrokerPartitionState.initialize(Map.of(1, PartitionState.active(1, partitionConfig)))),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
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
        new RemovePhysicalTenantOperation(memberId1),
        new PreScalingOperation(memberId1, memberCollection),
        new PostScalingOperation(memberId1, memberCollection),

        // Scale up operations
        new StartPartitionScaleUp(memberId1, 8),
        new AwaitRedistributionCompletion(memberId1, 8, partitionSet),
        new AwaitRelocationCompletion(memberId1, 8, partitionSet),

        // Partition change operations
        new PartitionJoinOperation(memberId1, 1, 1, true),
        new PartitionPromoteOperation(memberId1, 1),
        new PartitionDemoteOperation(memberId1, 1),
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
  void shouldListAllPendingAndCompletedChanges() {
    // given
    final var startedAt = Instant.ofEpochSecond(1000);
    final var lastCompletedAt = Instant.ofEpochSecond(500);
    final var pendingPlan1 =
        new PhasedChangePlan(
            2, 0, List.of(new GlobalPhase(List.of(new MemberJoinOperation(member(1))))), startedAt);
    final var pendingPlan2 =
        new PhasedChangePlan(
            3,
            0,
            List.of(new GlobalPhase(List.of(new MemberLeaveOperation(member(2))))),
            startedAt);
    final var lastChange1 =
        new CompletedPhasedChange(1, PhasedChangePlanStatus.COMPLETED, startedAt, lastCompletedAt);
    final var lastChange2 =
        new CompletedPhasedChange(4, PhasedChangePlanStatus.CANCELLED, startedAt, lastCompletedAt);
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(
                5, Map.of(2L, pendingPlan1, 3L, pendingPlan2), List.of(lastChange1, lastChange2)));

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
            tuple(3L, ConfigurationChange.StatusEnum.IN_PROGRESS),
            tuple(1L, ConfigurationChange.StatusEnum.COMPLETED),
            tuple(4L, ConfigurationChange.StatusEnum.CANCELLED));
  }

  /**
   * Regression test for <a href="https://github.com/camunda/camunda/issues/61586">#61586</a>: a
   * client that reads {@code pendingChange.id} while a change runs must find that same id in {@code
   * lastChange} once it completes. The three counters are set apart the way they were on the
   * failing cluster: global sub-config plan 61, default group sub-config plan 40, phased plan 20.
   */
  @Test
  void shouldReportPendingAndLastChangeUnderTheSamePhasedPlanId() {
    // given: a three-phase scale-up in its partition-group phase, with one of the two joins done.
    // The global phase already ran as global plan 61; the active group phase runs as group plan 40.
    final var member1 = member(1);
    final var startedAt = Instant.ofEpochSecond(1000);
    final var graphBuilder = OperationGraph.builder();
    final var join1Id = graphBuilder.add(new PartitionJoinOperation(member1, 1, 1, true));
    graphBuilder.add(new PartitionJoinOperation(member1, 2, 1, true), Set.of(join1Id));
    final var groupGraph = graphBuilder.build();
    final List<Phase> phases =
        List.of(
            new GlobalPhase(List.of(new MemberJoinOperation(member1))),
            new PartitionGroupPhase(Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, groupGraph)),
            new GlobalPhase(
                List.of(new PostScalingOperation(member1, new TreeSet<>(Set.of(member1))))));
    final var globalConfiguration =
        new GlobalConfiguration(
            61,
            Optional.empty(),
            new TreeMap<>(
                Map.of(
                    member1,
                    io.camunda.zeebe.dynamic.config.state.BrokerState.initializeAsActive())),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new CompletedChange(61, Status.COMPLETED, startedAt, startedAt.plusSeconds(1))));
    final var defaultGroup =
        new PartitionGroupConfiguration(
                39, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty())
            .startGraphConfigurationChange(groupGraph)
            .completeOperation(join1Id, UnaryOperator.identity());
    assertThat(defaultGroup.pendingChanges().orElseThrow().id()).isEqualTo(40);
    final var running =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            globalConfiguration,
            Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, defaultGroup),
            new PhasedChangeState(
                21, Map.of(20L, new PhasedChangePlan(20, 1, phases, startedAt)), List.of()));

    // when
    final var whileRunning = ClusterApiUtils.mapClusterTopology(running);
    final var afterCompletion =
        ClusterApiUtils.mapClusterTopology(
            running.completePlan(20, PhasedChangePlanStatus.COMPLETED));

    // then: the running change is reported under the phased plan id, with the progress of every
    // phase so far, not under the id of the sub-config plan executing the active phase
    final var pendingChange = whileRunning.getPendingChange();
    assertThat(pendingChange).isNotNull();
    assertThat(pendingChange.getId()).isEqualTo(20L);
    assertThat(pendingChange.getStatus()).isEqualTo(TopologyChange.StatusEnum.IN_PROGRESS);
    assertThat(pendingChange.getStartedAt()).isEqualTo(startedAt.atOffset(ZoneOffset.UTC));
    assertThat(pendingChange.getCompleted())
        .extracting(
            TopologyChangeCompletedInner::getOperation,
            TopologyChangeCompletedInner::getPartitionId)
        .containsExactly(
            tuple(TopologyChangeCompletedInner.OperationEnum.BROKER_ADD, null),
            tuple(TopologyChangeCompletedInner.OperationEnum.PARTITION_JOIN, 1));
    // only the active phase's sub-config plan records when its operations completed
    assertThat(pendingChange.getCompleted().get(0).getCompletedAt()).isNull();
    assertThat(pendingChange.getCompleted().get(1).getCompletedAt()).isNotNull();
    assertThat(pendingChange.getPending())
        .extracting(Operation::getOperation, Operation::getPartitionId)
        .containsExactly(tuple(PARTITION_JOIN, 2), tuple(OperationEnum.POST_SCALING, null));
    assertThat(whileRunning.getLastChange()).isNull();

    // and the completed change is found under the very same id
    assertThat(afterCompletion.getPendingChange()).isNull();
    assertThat(afterCompletion.getLastChange()).isNotNull();
    assertThat(afterCompletion.getLastChange().getId()).isEqualTo(pendingChange.getId());
    assertThat(afterCompletion.getLastChange().getStatus())
        .isEqualTo(io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.COMPLETED);
  }

  @Test
  void shouldReportThePendingChangeOfThePhysicalTenantInView() {
    // given: two physical tenants, each running its own change
    final var member1 = member(1);
    final var startedAt = Instant.ofEpochSecond(1000);
    final var planA =
        new PhasedChangePlan(
            2,
            0,
            List.of(
                PartitionGroupPhase.sequential(
                    "tenant-a", List.of(new PartitionJoinOperation(member1, 1, 1, true)))),
            startedAt);
    final var planB =
        new PhasedChangePlan(
            3,
            0,
            List.of(
                PartitionGroupPhase.sequential(
                    "tenant-b", List.of(new PartitionLeaveOperation(member1, 1, 0)))),
            startedAt);
    final var config =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", emptyGroup(), "tenant-b", emptyGroup()),
            new PhasedChangeState(4, Map.of(2L, planA, 3L, planB), List.of()));

    // when
    final var tenantA = ClusterApiUtils.mapClusterTopology(config, "tenant-a");
    final var tenantB = ClusterApiUtils.mapClusterTopology(config, "tenant-b");

    // then
    assertThat(tenantA.getPendingChange()).isNotNull();
    assertThat(tenantA.getPendingChange().getId()).isEqualTo(2L);
    assertThat(tenantA.getPendingChange().getPending())
        .extracting(Operation::getOperation, Operation::getPhysicalTenant)
        .containsExactly(tuple(PARTITION_JOIN, "tenant-a"));
    assertThat(tenantB.getPendingChange()).isNotNull();
    assertThat(tenantB.getPendingChange().getId()).isEqualTo(3L);
    assertThat(tenantB.getPendingChange().getPending())
        .extracting(Operation::getOperation, Operation::getPhysicalTenant)
        .containsExactly(tuple(Operation.OperationEnum.PARTITION_LEAVE, "tenant-b"));
  }

  @Test
  void shouldReportAClusterWidePendingChangeForEveryPhysicalTenantInView() {
    // given: a change with a global phase touches every tenant
    final var plan =
        new PhasedChangePlan(
            2,
            0,
            List.of(new GlobalPhase(List.of(new MemberJoinOperation(member(1))))),
            Instant.ofEpochSecond(1000));
    final var config =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", emptyGroup(), "tenant-b", emptyGroup()),
            new PhasedChangeState(3, Map.of(2L, plan), List.of()));

    // when
    final var tenantA = ClusterApiUtils.mapClusterTopology(config, "tenant-a");
    final var tenantB = ClusterApiUtils.mapClusterTopology(config, "tenant-b");

    // then
    assertThat(tenantA.getPendingChange()).isNotNull();
    assertThat(tenantA.getPendingChange().getId()).isEqualTo(2L);
    assertThat(tenantB.getPendingChange()).isNotNull();
    assertThat(tenantB.getPendingChange().getId()).isEqualTo(2L);
  }

  private static PartitionGroupConfiguration emptyGroup() {
    return new PartitionGroupConfiguration(
        1, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  @Test
  void shouldMapPendingOperationsOfInProgressChange() {
    // given
    final var memberId1 = member(1);
    final List<Phase> phases =
        List.of(
            PartitionGroupPhase.sequential(
                Map.of("default", List.of(new PartitionJoinOperation(memberId1, 1, 3, true)))),
            new GlobalPhase(List.of(new MemberLeaveOperation(memberId1))));
    final var config =
        configWithPhasedChangeState(
            new PhasedChangeState(
                5, Map.of(2L, new PhasedChangePlan(2, 0, phases, Instant.now())), List.of()));

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
  void shouldSplitActiveGraphPhaseUsingSubConfigProgress() {
    // given: the active phase targets physical tenant "tenant-a" with two operations, and the
    // tenant has run the join and not the leave -- which is what the API must report. A group's
    // progress is a set of completed operations, with no queue index to read.
    final var memberId1 = member(1);
    final var joinOperation = new PartitionJoinOperation(memberId1, 3, 1, true);
    final var leaveOperation = new PartitionLeaveOperation(memberId1, 4, 0);
    final var graphBuilder = OperationGraph.builder();
    final var joinId = graphBuilder.add(joinOperation);
    graphBuilder.add(leaveOperation, Set.of(joinId));
    final var tenantGroup =
        new PartitionGroupConfiguration(
                1, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty())
            .startGraphConfigurationChange(graphBuilder.build())
            .completeOperation(joinId, UnaryOperator.identity());
    final List<Phase> phases =
        List.of(
            new PartitionGroupPhase(
                Map.of("tenant-a", tenantGroup.pendingChanges().orElseThrow().graph())),
            new GlobalPhase(List.of(new MemberLeaveOperation(memberId1))));
    final var config =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", tenantGroup),
            new PhasedChangeState(
                8, Map.of(7L, new PhasedChangePlan(7, 0, phases, Instant.now())), List.of()));

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
        configWithPhasedChangeState(new PhasedChangeState(10, Map.of(), List.of(lastChange)));

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
                3, Map.of(2L, new PhasedChangePlan(2, 0, phases, Instant.now())), List.of()));

    // when
    final var response = ClusterApiUtils.mapConfigurationChangeResponse(config, 999);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(404);
    final var body = (Error) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getMessage()).contains("999");
  }

  /**
   * A legacy configuration carrying a pending change, as a projection of a live sub-configuration
   * would. Built here rather than started on the configuration: nothing executes a change through
   * the legacy single-group type, so it has no method that would start one.
   */
  private static ClusterConfiguration withPendingChange(
      final ClusterConfiguration config, final ClusterConfigurationChangeOperation... operations) {
    return ClusterConfiguration.builder()
        .from(config)
        .version(config.version() + 1)
        .pendingChanges(
            Optional.of(DependencyChangePlan.sequential(config.version() + 1, List.of(operations))))
        .build();
  }

  private record ExporterConfigParam(
      ClusterConfiguration configuration, List<ExporterStatus> expectedResult) {}
}
