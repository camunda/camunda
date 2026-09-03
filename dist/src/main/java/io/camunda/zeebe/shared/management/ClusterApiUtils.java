/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.FixedConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
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
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.ConfigurationChange;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.ExporterConfig;
import io.camunda.zeebe.management.cluster.ExporterStateCode;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExportingConfig;
import io.camunda.zeebe.management.cluster.GetConfigurationChangesResponse;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.management.cluster.PhysicalTenantInfo;
import io.camunda.zeebe.management.cluster.PhysicalTenantState;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.RequestHandling;
import io.camunda.zeebe.management.cluster.RequestHandlingActivePartitions;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.management.cluster.TopologyChange;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.management.cluster.TopologyChangeCompletedInner;
import io.camunda.zeebe.util.Either;
import java.net.ConnectException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

final class ClusterApiUtils {
  private static final OffsetDateTime MIN_PARSER_COMPLIANT_DATE =
      OffsetDateTime.parse("0000-01-01T00:00:00Z");
  private static final String MESSAGE_CORRELATION_STRATEGY_HASH_MOD = "HashMod";

  private ClusterApiUtils() {
    throw new IllegalStateException("Utility class");
  }

  private static BrokerId brokerIdValue(final MemberId memberId) {
    return memberId.zone() != null
        ? new BrokerId.String(memberId.id())
        : new BrokerId.Integer(memberId.nodeIdx());
  }

  static ResponseEntity<?> mapError(final Throwable error) {
    if (error instanceof CompletionException) {
      return mapError(error.getCause());
    }

    final var errorResponse = new Error();
    errorResponse.setMessage(error.getMessage());
    final int status =
        switch (error) {
          case final ConnectException ignore -> 502;
          case final NoSuchMemberException ignore -> 502;
          case final TimeoutException ignore -> 504;
          default -> 500;
        };
    return ResponseEntity.status(status).body(errorResponse);
  }

  static ResponseEntity<Error> mapErrorResponse(final ErrorResponse response) {
    final var errorCode =
        switch (response.code()) {
          case INVALID_REQUEST, OPERATION_NOT_ALLOWED -> 400;
          case NOT_FOUND -> 404;
          case CONCURRENT_MODIFICATION, INVALID_STATE -> 409;
          case INTERNAL_ERROR -> 500;
        };
    final var error = new Error();
    error.setMessage(response.message());
    return ResponseEntity.status(errorCode).body(error);
  }

  static ResponseEntity<?> mapOperationResponse(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> response) {
    if (response.isRight()) {
      return ResponseEntity.status(202).body(mapResponseType(response.get()));
    } else {
      return ClusterApiUtils.mapErrorResponse(response.getLeft());
    }
  }

  static ResponseEntity<?> mapOperationResponse(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> response,
      final Throwable throwable) {
    if (throwable != null) {
      return ClusterApiUtils.mapError(throwable);
    }

    return ClusterApiUtils.mapOperationResponse(response);
  }

  static ResponseEntity<?> mapClusterTopologyResponse(
      final Either<ErrorResponse, CurrentClusterConfiguration> response) {
    if (response.isRight()) {
      return ResponseEntity.status(200).body(mapClusterTopology(response.get()));
    } else {
      return ClusterApiUtils.mapErrorResponse(response.getLeft());
    }
  }

  static ResponseEntity<?> mapConfigurationChangeResponse(
      final CurrentClusterConfiguration configuration, final long changeId) {
    return findConfigurationChange(configuration, changeId)
        .<ResponseEntity<?>>map(change -> ResponseEntity.status(200).body(change))
        .orElseGet(
            () -> {
              final var error = new Error();
              error.setMessage("No configuration change found with id " + changeId);
              return ResponseEntity.status(404).body(error);
            });
  }

  static ResponseEntity<?> mapConfigurationChangesResponse(
      final CurrentClusterConfiguration configuration) {
    final var phasedChangeState = configuration.phasedChangeState();
    final List<ConfigurationChange> changes = new ArrayList<>();
    phasedChangeState.pending().values().stream()
        .map(phasedChangePlan -> mapConfigurationChange(configuration, phasedChangePlan))
        .forEach(changes::add);
    phasedChangeState.history().stream()
        .map(ClusterApiUtils::mapConfigurationChange)
        .forEach(changes::add);

    final var sortedChanges =
        changes.stream().sorted(Comparator.comparingLong(ConfigurationChange::getId)).toList();
    return ResponseEntity.status(200)
        .body(new GetConfigurationChangesResponse().changes(sortedChanges));
  }

  /**
   * Resolves {@code changeId} against the two changes {@link PhasedChangeState} currently retains
   * (the pending plan and the last completed change) — there is no persisted change history beyond
   * those two, so any other id is reported as not found.
   */
  private static Optional<ConfigurationChange> findConfigurationChange(
      final CurrentClusterConfiguration configuration, final long changeId) {
    final var phasedChangeState = configuration.phasedChangeState();
    final var pendingMatch =
        phasedChangeState.pending().values().stream()
            .filter(plan -> plan.id() == changeId)
            .findFirst();
    if (pendingMatch.isPresent()) {
      return pendingMatch.map(plan -> mapConfigurationChange(configuration, plan));
    }
    return phasedChangeState.history().stream()
        .filter(change -> change.id() == changeId)
        .findFirst()
        .map(ClusterApiUtils::mapConfigurationChange);
  }

  /**
   * Maps a pending plan's operations to completed/pending, using the actual progress of whichever
   * sub-config (global or physical tenant) the currently active phase targets: phases before {@code
   * currentPhaseIndex} are fully done, phases after it are entirely untouched, but the active
   * phase's own operations may be partially done already — activating a phase copies its operations
   * into the target sub-config's {@link ClusterChangePlan}, which independently tracks which of
   * those operations have completed so far.
   */
  private static ConfigurationChange mapConfigurationChange(
      final CurrentClusterConfiguration configuration, final PhasedChangePlan plan) {
    final var phases = plan.phases();
    final var completedPhases = phases.subList(0, plan.currentPhaseIndex());
    final var remainingPhases = phases.subList(plan.currentPhaseIndex(), phases.size());

    final List<Operation> completed = new ArrayList<>(mapPhaseOperations(completedPhases));
    final List<Operation> pending = new ArrayList<>();
    if (!remainingPhases.isEmpty()) {
      splitActivePhase(configuration, remainingPhases.get(0), completed, pending);
      pending.addAll(mapPhaseOperations(remainingPhases.subList(1, remainingPhases.size())));
    }

    return new ConfigurationChange()
        .id(plan.id())
        .status(ConfigurationChange.StatusEnum.IN_PROGRESS)
        .startedAt(mapInstantToDateTime(plan.startedAt()))
        .completed(completed)
        .pending(pending);
  }

  private static void splitActivePhase(
      final CurrentClusterConfiguration configuration,
      final Phase activePhase,
      final List<Operation> completed,
      final List<Operation> pending) {
    switch (activePhase) {
      case final GlobalPhase globalPhase ->
          splitSubConfigOperations(
              null,
              globalPhase.operations(),
              configuration.globalConfiguration().pendingChanges(),
              completed,
              pending);
      case final PartitionGroupPhase groupPhase ->
          splitGroupOperations(configuration, groupPhase.groupOperations(), completed, pending);
    }
  }

  private static void splitGroupOperations(
      final CurrentClusterConfiguration configuration,
      final Map<String, ? extends List<? extends ClusterConfigurationChangeOperation>>
          groupOperations,
      final List<Operation> completed,
      final List<Operation> pending) {
    groupOperations.forEach(
        (groupId, operations) ->
            splitSubConfigOperations(
                groupId,
                operations,
                Optional.ofNullable(configuration.partitionGroups().get(groupId))
                    .flatMap(PartitionGroupConfiguration::pendingChanges),
                completed,
                pending));
  }

  /**
   * Splits one sub-config's share of the active phase's operations between {@code completed} and
   * {@code pending}. {@code subConfigPlan} is absent when the phase hasn't actually been activated
   * into the sub-config yet (e.g. a plan reconstructed via {@link
   * CurrentClusterConfiguration#fromLegacy}, which builds the pending plan at phase 0 without
   * activating it) — in that case nothing has completed yet, so the whole phase is still pending.
   */
  private static void splitSubConfigOperations(
      final String physicalTenantId,
      final List<? extends ClusterConfigurationChangeOperation> phaseOperations,
      final Optional<? extends ChangePlan> subConfigPlan,
      final List<Operation> completed,
      final List<Operation> pending) {
    if (subConfigPlan.isEmpty()) {
      pending.addAll(mapOperations(physicalTenantId, phaseOperations));
      return;
    }
    final var plan = subConfigPlan.get();
    plan.completedOperations()
        .forEach(
            completedOperation ->
                completed.add(mapOperation(physicalTenantId, completedOperation.operation())));
    pending.addAll(mapOperations(physicalTenantId, plan.pendingOperations()));
  }

  private static ConfigurationChange mapConfigurationChange(final CompletedPhasedChange change) {
    // CompletedPhasedChange retains only id/status/timestamps, not the operations that made up the
    // change, so completed/pending are necessarily empty here.
    return new ConfigurationChange()
        .id(change.id())
        .status(mapPhasedChangeStatus(change.status()))
        .startedAt(mapInstantToDateTime(change.startedAt()))
        .completedAt(mapInstantToDateTime(change.completedAt()))
        .completed(List.of())
        .pending(List.of());
  }

  private static ConfigurationChange.StatusEnum mapPhasedChangeStatus(
      final PhasedChangePlanStatus status) {
    return switch (status) {
      case COMPLETED -> ConfigurationChange.StatusEnum.COMPLETED;
      case FAILED -> ConfigurationChange.StatusEnum.FAILED;
      case CANCELLED -> ConfigurationChange.StatusEnum.CANCELLED;
    };
  }

  private static PlannedOperationsResponse mapResponseType(
      final ClusterConfigurationChangeResponse response) {
    // response.response() is absent when the responding peer hasn't populated the new
    // multi-partition-group data (e.g. a peer still running old code); fall back to the
    // legacy data in that case.
    final var multiConfigResponse = response.response();
    final List<Operation> operations =
        multiConfigResponse == null
            ? mapOperations(response.legacyResponse().plannedChanges())
            : mapPhaseOperations(multiConfigResponse.phases());
    final List<BrokerState> currentTopology =
        multiConfigResponse == null
            ? mapBrokerStates(response.legacyResponse().currentConfiguration())
            : mapBrokerStatesFromPhysicalTenantsConfig(multiConfigResponse.currentConfiguration());
    final List<BrokerState> expectedTopology =
        multiConfigResponse == null
            ? mapBrokerStates(response.legacyResponse().expectedConfiguration())
            : mapBrokerStatesFromPhysicalTenantsConfig(multiConfigResponse.expectedConfiguration());
    return new PlannedOperationsResponse()
        .changeId(response.changeId())
        .currentTopology(currentTopology)
        .expectedTopology(expectedTopology)
        .plannedChanges(operations);
  }

  private static List<Operation> mapOperations(
      final List<ClusterConfigurationChangeOperation> operations) {
    return mapOperations(null, operations);
  }

  private static List<Operation> mapOperations(
      final String physicalTenantId,
      final List<? extends ClusterConfigurationChangeOperation> operations) {
    return operations.stream().map(op -> mapOperation(physicalTenantId, op)).toList();
  }

  private static Stream<Operation> mapGroupOperations(
      final Map<String, ? extends List<? extends ClusterConfigurationChangeOperation>>
          groupOperations) {
    return groupOperations.entrySet().stream()
        .flatMap(entry -> mapOperations(entry.getKey(), entry.getValue()).stream());
  }

  private static List<Operation> mapPhaseOperations(final List<Phase> phases) {
    return phases.stream()
        .flatMap(
            phase ->
                switch (phase) {
                  case final GlobalPhase globalPhase ->
                      mapOperations(null, globalPhase.operations()).stream();
                  case final PartitionGroupPhase groupPhase ->
                      mapGroupOperations(groupPhase.groupOperations());
                })
        .toList();
  }

  static Operation mapOperation(
      final String physicalTenantId, final ClusterConfigurationChangeOperation operation) {
    final var convertedOperation =
        switch (operation) {
          case final MemberJoinOperation join ->
              new Operation().operation(OperationEnum.BROKER_ADD);
          case final MemberLeaveOperation leave ->
              new Operation().operation(OperationEnum.BROKER_REMOVE);
          case final PartitionJoinOperation join ->
              new Operation()
                  .operation(OperationEnum.PARTITION_JOIN)
                  .partitionId(join.partitionId())
                  .priority(join.priority());
          case final PartitionLeaveOperation leave ->
              new Operation()
                  .operation(OperationEnum.PARTITION_LEAVE)
                  .partitionId(leave.partitionId());
          case final PartitionPromoteOperation promote ->
              new Operation()
                  .operation(OperationEnum.PARTITION_PROMOTE)
                  .partitionId(promote.partitionId());
          case final PartitionDemoteOperation demote ->
              new Operation()
                  .operation(OperationEnum.PARTITION_DEMOTE)
                  .partitionId(demote.partitionId());
          case final PartitionReconfigurePriorityOperation reconfigure ->
              new Operation()
                  .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                  .partitionId(reconfigure.partitionId())
                  .priority(reconfigure.priority());
          case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_FORCE_RECONFIGURE)
                  .partitionId(partitionForceReconfigureOperation.partitionId())
                  .brokers(
                      partitionForceReconfigureOperation.members().stream()
                          .map(m -> brokerIdValue(m))
                          .collect(toList()));
          case final MemberRemoveOperation memberRemoveOperation ->
              new Operation()
                  .operation(OperationEnum.BROKER_REMOVE)
                  .brokers(List.of(brokerIdValue(memberRemoveOperation.memberToRemove())));
          case final RemovePhysicalTenantOperation ignored ->
              // A partition-group operation, so physicalTenantId here is already the target group.
              new Operation()
                  .operation(OperationEnum.REMOVE_PHYSICAL_TENANT)
                  .physicalTenant(physicalTenantId);
          case final PartitionDisableExporterOperation disableExporterOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_DISABLE_EXPORTER)
                  .partitionId(disableExporterOperation.partitionId())
                  .exporterId(disableExporterOperation.exporterId());
          case final PartitionEnableExporterOperation enableExporterOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_ENABLE_EXPORTER)
                  .partitionId(enableExporterOperation.partitionId())
                  .exporterId(enableExporterOperation.exporterId());
          case final PartitionDeleteExporterOperation deleteExporterOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_DELETE_EXPORTER)
                  .partitionId(deleteExporterOperation.partitionId())
                  .exporterId(deleteExporterOperation.exporterId());
          case final StartPartitionScaleUp startScaleUp ->
              new Operation().operation(OperationEnum.START_PARTITION_SCALE_UP);
          case final PartitionBootstrapOperation bootstrapOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_BOOTSTRAP)
                  .partitionId(bootstrapOperation.partitionId())
                  .priority(bootstrapOperation.priority());
          case final PartitionPreRestoreOperation preRestoreOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_PRE_RESTORE)
                  .partitionId(preRestoreOperation.partitionId());
          case final PartitionRestoreOperation restoreOperation ->
              new Operation()
                  .operation(OperationEnum.PARTITION_RESTORE)
                  .partitionId(restoreOperation.partitionId());
          case final DeleteHistoryOperation deleteHistoryOperation ->
              new Operation().operation(OperationEnum.DELETE_HISTORY);
          case final AwaitRedistributionCompletion redistributionCompletion ->
              new Operation().operation(OperationEnum.AWAIT_REDISTRIBUTION);
          case final AwaitRelocationCompletion relocationCompletion ->
              new Operation().operation(OperationEnum.AWAIT_RELOCATION);
          case final UpdateRoutingState updateRoutingState ->
              new Operation().operation(OperationEnum.UPDATE_ROUTING_STATE);
          case final UpdateIncarnationNumberOperation updateIncarnationNumberOperation ->
              new Operation().operation(OperationEnum.UPDATE_INCARNATION_NUMBER);
          case final PreScalingOperation preScalingOperation ->
              new Operation()
                  .operation(OperationEnum.PRE_SCALING)
                  .brokers(
                      preScalingOperation.clusterMembers().stream()
                          .map(m -> brokerIdValue(m))
                          .toList());
          case final PostScalingOperation postScalingOperation ->
              new Operation()
                  .operation(OperationEnum.POST_SCALING)
                  .brokers(
                      postScalingOperation.clusterMembers().stream()
                          .map(m -> brokerIdValue(m))
                          .toList());
          case final UpdatePartitionDistributorConfigOperation
                  updatePartitionDistributorConfigOperation ->
              new Operation()
                  .operation(OperationEnum.UPDATE_PARTITION_DISTRIBUTOR_CONFIG)
                  .partitionDistributionConfig(
                      toPartitionDistributionConfig(updatePartitionDistributorConfigOperation));
          case final ModeChangeOperation modeChange ->
              switch (modeChange.mode()) {
                case RECOVERING -> new Operation().operation(OperationEnum.ENTER_RECOVERY);
                case PROCESSING -> new Operation().operation(OperationEnum.EXIT_RECOVERY);
              };
          case final AwaitModeChangeOperation modeChange ->
              new Operation().operation(OperationEnum.AWAIT_MODE_CHANGE);
          case final ExportingStateChangeOperation exportingStateChangeOperation ->
              new Operation()
                  .operation(OperationEnum.EXPORTING_STATE_CHANGE)
                  .exportingState(mapExportingState(exportingStateChangeOperation.state()));
        };

    convertedOperation.brokerId(brokerIdValue(operation.memberId()));
    if (operation instanceof PartitionGroupOperation) {
      convertedOperation.setPhysicalTenant(physicalTenantId);
    }
    return convertedOperation;
  }

  private static List<BrokerState> mapBrokerStates(
      final SortedMap<MemberId, MemberState> topology) {
    return topology.entrySet().stream()
        .map(
            entry ->
                new BrokerState()
                    .id(brokerIdValue(entry.getKey()))
                    .state(mapBrokerState(entry.getValue().state()))
                    .lastUpdatedAt(mapInstantToDateTime(entry.getValue().lastUpdated()))
                    .version(entry.getValue().version())
                    .partitions(mapPartitionStates(entry.getValue().partitions())))
        .toList();
  }

  /**
   * Flattens the new multi-partition-group model into the flat {@code BrokerState} list expected by
   * the REST response: every broker in {@code brokers} is included (broker lifecycle has no tenant
   * dimension, so this is always the global configuration's full membership), and each broker's
   * partitions from every physical tenant group in {@code groupsInView} it participates in are
   * merged onto that one broker entry, tagged with their owning tenant. A broker replicating
   * nothing in {@code groupsInView} gets an empty partition list.
   *
   * <p>{@code groupsInView} is deliberately a parameter rather than derived from the configuration
   * the {@code brokers} came from: it is the subset of partition groups the request is scoped to,
   * so deriving all groups instead would report every tenant's partitions on a request scoped to
   * one. Where no scoping applies, use {@link
   * #mapBrokerStatesFromPhysicalTenantsConfig(CurrentClusterConfiguration)}, which does derive
   * them. It is iterated in a fresh {@link TreeMap}, not the caller's map directly: {@link
   * CurrentClusterConfiguration}'s constructor stores partition groups via {@code Map.copyOf(...)},
   * whose iteration order is randomised per JVM (see {@code
   * jdk.internal.util.ImmutableCollections#SALT32L}), and the JSON response must have a stable,
   * deterministic order.
   */
  private static List<BrokerState> mapBrokerStatesFromPhysicalTenantsConfig(
      final Map<MemberId, io.camunda.zeebe.dynamic.config.state.BrokerState> brokers,
      final Map<String, PartitionGroupConfiguration> groupsInView) {
    final Map<String, PartitionGroupConfiguration> sortedGroups = new TreeMap<>(groupsInView);
    return brokers.entrySet().stream()
        .map(
            entry ->
                mapBrokerStateFromPhysicalTenantsConfig(
                    entry.getKey(), entry.getValue(), sortedGroups))
        .toList();
  }

  /** Maps every broker of {@code configuration}, covering all of its partition groups. */
  private static List<BrokerState> mapBrokerStatesFromPhysicalTenantsConfig(
      final CurrentClusterConfiguration configuration) {
    return mapBrokerStatesFromPhysicalTenantsConfig(
        configuration.globalConfiguration().members(), configuration.partitionGroups());
  }

  /**
   * Maps one broker, merging its partitions across {@code partitionGroups}. {@code version} is
   * reported only while those cover at most one physical tenant: it is then the same fold the
   * legacy {@link CurrentClusterConfiguration#toLegacy} projection applies (see {@code
   * toLegacyMemberState}), so a consumer watching {@code brokers[].version} for change detection
   * still sees partition-state changes and not only broker-lifecycle ones — the two counters are
   * independent, and a partition join bumps the group's without touching the broker's. Across more
   * than one tenant that maximum is no longer the version of anything, so it is left absent. {@code
   * lastUpdatedAt} is folded either way, where it keeps meaning "something about this broker last
   * changed at this time".
   */
  private static BrokerState mapBrokerStateFromPhysicalTenantsConfig(
      final MemberId memberId,
      final io.camunda.zeebe.dynamic.config.state.BrokerState brokerState,
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    final List<PartitionState> partitions = new ArrayList<>();
    final List<PhysicalTenantState> physicalTenants = new ArrayList<>();
    long version = brokerState.version();
    Instant lastUpdated = brokerState.lastUpdated();
    for (final var entry : partitionGroups.entrySet()) {
      final var physicalTenantId = entry.getKey();
      final var group = entry.getValue();
      if (group.isDisabled()) {
        // not running anywhere - excluded from this broker's partitions/physicalTenants and from
        // the version/lastUpdated fold below, the same way it is reported as disabled rather than
        // with routing state in PhysicalTenantInfo (see mapPhysicalTenantInfo).
        continue;
      }
      final var brokerPartitionState = group.members().get(memberId);
      if (brokerPartitionState != null) {
        physicalTenants.add(
            new PhysicalTenantState()
                .id(physicalTenantId)
                .mode(mapPhysicalTenantMode(brokerPartitionState.mode())));
        partitions.addAll(mapPartitionStates(physicalTenantId, brokerPartitionState.partitions()));
        version = Math.max(version, brokerPartitionState.version());
        if (brokerPartitionState.lastUpdated().isAfter(lastUpdated)) {
          lastUpdated = brokerPartitionState.lastUpdated();
        }
      }
    }
    final var mapped =
        new BrokerState()
            .id(brokerIdValue(memberId))
            .state(mapBrokerLifecycleState(brokerState.state()))
            .lastUpdatedAt(mapInstantToDateTime(lastUpdated))
            .partitions(partitions)
            .physicalTenants(physicalTenants);
    if (partitionGroups.size() <= 1) {
      mapped.version(version);
    }
    return mapped;
  }

  private static BrokerStateCode mapBrokerLifecycleState(
      final io.camunda.zeebe.dynamic.config.state.BrokerState.State state) {
    return switch (state) {
      case ACTIVE -> BrokerStateCode.ACTIVE;
      case JOINING -> BrokerStateCode.JOINING;
      case LEAVING -> BrokerStateCode.LEAVING;
      case LEFT -> BrokerStateCode.LEFT;
      case UNINITIALIZED -> BrokerStateCode.UNKNOWN;
    };
  }

  private static PhysicalTenantState.ModeEnum mapPhysicalTenantMode(final Mode mode) {
    return switch (mode) {
      case PROCESSING -> PhysicalTenantState.ModeEnum.PROCESSING;
      case RECOVERING -> PhysicalTenantState.ModeEnum.RECOVERING;
    };
  }

  private static OffsetDateTime mapInstantToDateTime(final Instant timestamp) {
    // Instant.MIN ("-1000000000-01-01T00:00Z") is not compliant with rfc3339 parsers
    // as year field has is not 4 digits, so we replace here with the min possible.
    // see: https://github.com/camunda/camunda/issues/16256
    return timestamp.equals(Instant.MIN)
        ? MIN_PARSER_COMPLIANT_DATE
        : timestamp.atOffset(ZoneOffset.UTC);
  }

  private static BrokerStateCode mapBrokerState(final MemberState.State state) {
    return switch (state) {
      case ACTIVE -> BrokerStateCode.ACTIVE;
      case JOINING -> BrokerStateCode.JOINING;
      case LEAVING -> BrokerStateCode.LEAVING;
      case LEFT -> BrokerStateCode.LEFT;
      case UNINITIALIZED -> BrokerStateCode.UNKNOWN;
      case RECOVERING -> BrokerStateCode.RECOVERING;
    };
  }

  private static List<PartitionState> mapPartitionStates(
      final SortedMap<Integer, io.camunda.zeebe.dynamic.config.state.PartitionState> partitions) {
    return mapPartitionStates(null, partitions);
  }

  private static List<PartitionState> mapPartitionStates(
      final String physicalTenantId,
      final SortedMap<Integer, io.camunda.zeebe.dynamic.config.state.PartitionState> partitions) {
    return partitions.entrySet().stream()
        .map(
            entry ->
                new PartitionState()
                    .id(entry.getKey())
                    .physicalTenant(physicalTenantId)
                    .priority(entry.getValue().priority())
                    .state(mapPartitionState(entry.getValue().state()))
                    .config(mapPartitionConfig(entry.getValue().config())))
        .toList();
  }

  private static PartitionConfig mapPartitionConfig(final DynamicPartitionConfig config) {
    final var exporters =
        config.exporting().exporters().entrySet().stream()
            .map(
                entry ->
                    new ExporterConfig()
                        .id(entry.getKey())
                        .state(mapExporterState(entry.getValue().state())))
            .toList();

    return new PartitionConfig().exporting(new ExportingConfig().exporters(exporters));
  }

  private static ExporterStateCode mapExporterState(final ExporterState.State state) {
    return switch (state) {
      case DISABLED -> ExporterStateCode.DISABLED;
      case ENABLED -> ExporterStateCode.ENABLED;
      case CONFIG_NOT_FOUND -> ExporterStateCode.CONFIG_NOT_FOUND;
    };
  }

  private static io.camunda.zeebe.management.cluster.ExportingState mapExportingState(
      final ExportingState state) {
    return switch (state) {
      case EXPORTING -> io.camunda.zeebe.management.cluster.ExportingState.EXPORTING;
      case PAUSED -> io.camunda.zeebe.management.cluster.ExportingState.PAUSED;
      case SOFT_PAUSED -> io.camunda.zeebe.management.cluster.ExportingState.SOFT_PAUSED;
      case UNKNOWN -> io.camunda.zeebe.management.cluster.ExportingState.UNKNOWN;
    };
  }

  private static PartitionStateCode mapPartitionState(final State state) {
    return switch (state) {
      case JOINING -> PartitionStateCode.JOINING;
      case ACTIVE -> PartitionStateCode.ACTIVE;
      case LEAVING -> PartitionStateCode.LEAVING;
      case RECOVERING -> PartitionStateCode.RECOVERING;
      case LEARNER -> PartitionStateCode.LEARNER;
      // TODO: Define state code for BootStrapping
      case BOOTSTRAPPING, UNKNOWN -> PartitionStateCode.UNKNOWN;
    };
  }

  static GetTopologyResponse mapClusterTopology(final CurrentClusterConfiguration configuration) {
    return mapClusterTopology(configuration, null);
  }

  /**
   * Maps the multi-partition-group configuration to the REST response, scoped to {@code
   * physicalTenant} when given, or to every known physical tenant otherwise. {@code brokers},
   * {@code clusterId} and {@code partitionDistribution} always reflect the global configuration, as
   * they have no tenant dimension.
   *
   * <p>The remaining top-level fields are mutually exclusive, single-tenant-shaped or
   * multi-tenant-shaped, never both, so a request that predates physical tenants keeps exactly the
   * top-level field set it always had, and no physical tenant's routing state is ever reported
   * twice for a tenant the caller did not ask about. Per-broker {@code physicalTenants} and
   * per-partition {@code physicalTenant} are always present regardless of scope — they predate this
   * method — so the response is not byte-identical to before, only the top-level field set is
   * unchanged:
   *
   * <ul>
   *   <li>at most one group in view (an unscoped request against a single-tenant cluster, a request
   *       scoped to one physical tenant, or an uninitialized configuration with zero groups):
   *       {@code version}, {@code lastChange} and {@code pendingChange} are populated from {@link
   *       CurrentClusterConfiguration#toLegacy}'s cluster-wide projection — {@code version} is the
   *       higher of the global and this one group's version, {@code lastChange} always reflects the
   *       cluster-wide change history regardless of which group is in view, and {@code
   *       pendingChange} prefers the global pending change and falls back to this group's own; only
   *       {@code routing} is genuinely this one group's own state.
   *   <li>more than one group in view (an unscoped request against a multi-tenant cluster): none of
   *       {@code version}, {@code lastChange}, {@code pendingChange} or {@code routing} are
   *       populated at the top level — reporting any one group's routing state there would
   *       misrepresent the tenants left out, and the cluster-wide change state has no single
   *       physical tenant to be scoped to. Per-broker {@code version} is left absent as well, see
   *       {@link #mapBrokerStateFromPhysicalTenantsConfig}.
   * </ul>
   *
   * <p>{@code physicalTenants} lists every group in view, and is omitted only for an unscoped
   * request whose single tenant is already fully described by the top-level fields. A caller that
   * names a physical tenant therefore reads that tenant's routing state the same way as a caller
   * that names none, at the cost of repeating it under {@code routing}; the two agree by
   * construction, both being the one group in view's own state.
   */
  static GetTopologyResponse mapClusterTopology(
      final CurrentClusterConfiguration configuration, final @Nullable String physicalTenant) {
    // the caller (ClusterEndpoint) guarantees physicalTenant, when given, names an existing group
    // only once the configuration is initialized. An uninitialized configuration has zero groups
    // by construction (see CurrentClusterConfiguration.uninitialized()/init()), so a requested
    // tenant is then legitimately absent - fall back to an empty view rather than NPE-ing on
    // configuration.partitionGroup's @Nullable result; this lands in the at-most-one-group branch
    // below and yields the uninitialized toLegacy(DEFAULT_GROUP) projection.
    final Map<String, PartitionGroupConfiguration> groupsInView;
    if (physicalTenant == null) {
      groupsInView = configuration.partitionGroups();
    } else {
      final var group = configuration.partitionGroup(physicalTenant);
      groupsInView = group == null ? Map.of() : Map.of(physicalTenant, group);
    }

    final boolean singleTenantShape = groupsInView.size() <= 1;

    final var response = new GetTopologyResponse();
    response.brokers(
        mapBrokerStatesFromPhysicalTenantsConfig(
            configuration.globalConfiguration().members(), groupsInView));

    if (singleTenantShape) {
      final String groupId =
          groupsInView.isEmpty()
              ? CurrentClusterConfiguration.DEFAULT_GROUP
              : groupsInView.keySet().iterator().next();
      final var legacy = configuration.toLegacy(groupId);
      response.version(legacy.version());
      legacy.lastChange().ifPresent(change -> response.lastChange(mapCompletedChange(change)));
      legacy.pendingChanges().ifPresent(change -> response.pendingChange(mapOngoingChange(change)));
      legacy
          .routingState()
          .ifPresent(routingState -> response.routing(mapRoutingState(routingState)));
    }

    if (physicalTenant == null && singleTenantShape) {
      // the generated model defaults physicalTenants to an empty (non-null) list, not absent; it
      // must be explicitly nulled out here so it is genuinely omitted from the response body of a
      // request that predates physical tenants, per the mutually-exclusive contract.
      response.physicalTenants(null);
    } else {
      response.physicalTenants(
          new TreeMap<>(groupsInView)
              .entrySet().stream()
                  .map(entry -> mapPhysicalTenantInfo(entry.getKey(), entry.getValue()))
                  .toList());
    }

    configuration.clusterId().ifPresent(response::clusterId);
    configuration
        .globalConfiguration()
        .partitionDistributorConfig()
        .ifPresent(
            config -> response.partitionDistribution(mapPartitionDistributionConfig(config)));
    return response;
  }

  private static PhysicalTenantInfo mapPhysicalTenantInfo(
      final String groupId, final PartitionGroupConfiguration group) {
    final var info = new PhysicalTenantInfo().id(groupId);
    if (group.isDisabled()) {
      // disabled: not running anywhere, so its routing state is not reported - only that it is
      // disabled. An enabled tenant never sets `disabled`; a populated `routing` already implies
      // it.
      return info.disabled(true);
    }
    group.routingState().ifPresent(routingState -> info.routing(mapRoutingState(routingState)));
    return info;
  }

  private static io.camunda.zeebe.management.cluster.PartitionDistributionConfig
      mapPartitionDistributionConfig(final PartitionDistributorConfig config) {
    final var result = new io.camunda.zeebe.management.cluster.PartitionDistributionConfig();
    switch (config) {
      case final PartitionDistributorConfig.RoundRobinConfig ignored ->
          result.type(
              io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum.ROUND_ROBIN);
      case final PartitionDistributorConfig.ZoneAwareConfig zoneAware ->
          result
              .type(
                  io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum
                      .ZONE_AWARE)
              .zones(
                  zoneAware.zones().stream()
                      .map(
                          z ->
                              new io.camunda.zeebe.management.cluster.ZoneSpec()
                                  .name(z.name())
                                  .numberOfReplicas(z.numberOfReplicas())
                                  .priority(z.priority()))
                      .toList());
      case final PartitionDistributorConfig.FixedConfig ignored ->
          result.type(
              io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum.FIXED);
    }
    return result;
  }

  static PartitionDistributorConfig toPartitionDistributorConfig(
      final io.camunda.zeebe.management.cluster.PartitionDistributionConfig dto) {
    final List<PartitionDistributorConfig.ZoneSpec> zones =
        dto.getZones().stream()
            .map(
                z ->
                    new PartitionDistributorConfig.ZoneSpec(
                        z.getName(), z.getNumberOfReplicas(), z.getPriority()))
            .toList();
    return new ZoneAwareConfig(zones);
  }

  private static io.camunda.zeebe.management.cluster.RoutingState mapRoutingState(
      final RoutingState routingState) {
    return new io.camunda.zeebe.management.cluster.RoutingState()
        .version(routingState.version())
        .requestHandling(mapRequestHanding(routingState.requestHandling()))
        .messageCorrelation(mapMessageCorrelation(routingState.messageCorrelation()));
  }

  private static RequestHandling mapRequestHanding(
      final RoutingState.RequestHandling requestHandling) {
    return switch (requestHandling) {
      case ActivePartitions(
              final var basePartitionCount,
              final var additionalActivePartitions,
              final var inactivePartitions) ->
          new RequestHandlingActivePartitions(
              basePartitionCount,
              new ArrayList<>(additionalActivePartitions),
              new ArrayList<>(inactivePartitions));
      case AllPartitions(final var partitionCount) ->
          new RequestHandlingAllPartitions(partitionCount);
    };
  }

  private static MessageCorrelationHashMod mapMessageCorrelation(
      final RoutingState.MessageCorrelation messageCorrelation) {
    return switch (messageCorrelation) {
      case HashMod(final var partitionCount) ->
          new MessageCorrelationHashMod()
              .strategy(MESSAGE_CORRELATION_STRATEGY_HASH_MOD)
              .partitionCount(partitionCount);
    };
  }

  private static io.camunda.zeebe.management.cluster.CompletedChange mapCompletedChange(
      final CompletedChange completedChange) {
    return new io.camunda.zeebe.management.cluster.CompletedChange()
        .id(completedChange.id())
        .status(mapCompletedChangeStatus(completedChange.status()))
        .startedAt(mapInstantToDateTime(completedChange.startedAt()))
        .completedAt(mapInstantToDateTime(completedChange.completedAt()));
  }

  private static TopologyChange mapOngoingChange(final ChangePlan changePlan) {
    return new TopologyChange()
        .id(changePlan.id())
        .status(mapChangeStatus(changePlan.status()))
        .pending(mapOperations(changePlan.pendingOperations()))
        .completed(mapCompletedOperations(changePlan.completedOperations()));
  }

  private static io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum
      mapCompletedChangeStatus(final Status status) {
    return switch (status) {
      case COMPLETED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.COMPLETED;
      case FAILED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.FAILED;
      case CANCELLED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.CANCELLED;
      case IN_PROGRESS -> throw new IllegalStateException("Completed change cannot be in progress");
    };
  }

  private static StatusEnum mapChangeStatus(final Status status) {
    return switch (status) {
      case IN_PROGRESS -> StatusEnum.IN_PROGRESS;
      case COMPLETED -> StatusEnum.COMPLETED;
      case FAILED -> StatusEnum.FAILED;
      case CANCELLED -> StatusEnum.CANCELLED;
    };
  }

  private static List<TopologyChangeCompletedInner> mapCompletedOperations(
      final List<CompletedOperation> completedOperations) {
    return completedOperations.stream().map(ClusterApiUtils::mapCompletedOperation).toList();
  }

  static TopologyChangeCompletedInner mapCompletedOperation(final CompletedOperation operation) {
    final var mappedOperation =
        switch (operation.operation()) {
          case final MemberJoinOperation join ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_ADD)
                  .brokerId(brokerIdValue(join.memberId()));
          case final MemberLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(brokerIdValue(leave.memberId()));
          case final PartitionJoinOperation join ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_JOIN)
                  .brokerId(brokerIdValue(join.memberId()))
                  .partitionId(join.partitionId())
                  .priority(join.priority());
          case final PartitionLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_LEAVE)
                  .brokerId(brokerIdValue(leave.memberId()))
                  .partitionId(leave.partitionId());
          case final PartitionPromoteOperation promote ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_PROMOTE)
                  .brokerId(brokerIdValue(promote.memberId()))
                  .partitionId(promote.partitionId());
          case final PartitionDemoteOperation demote ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_DEMOTE)
                  .brokerId(brokerIdValue(demote.memberId()))
                  .partitionId(demote.partitionId());
          case final PartitionReconfigurePriorityOperation reconfigure ->
              new TopologyChangeCompletedInner()
                  .operation(
                      TopologyChangeCompletedInner.OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                  .brokerId(brokerIdValue(reconfigure.memberId()))
                  .partitionId(reconfigure.partitionId())
                  .priority(reconfigure.priority());
          case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_FORCE_RECONFIGURE)
                  .brokerId(brokerIdValue(partitionForceReconfigureOperation.memberId()))
                  .partitionId(partitionForceReconfigureOperation.partitionId())
                  .brokers(
                      partitionForceReconfigureOperation.members().stream()
                          .map(ClusterApiUtils::brokerIdValue)
                          .toList());
          case final MemberRemoveOperation memberRemoveOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(brokerIdValue(memberRemoveOperation.memberId()))
                  .brokers(List.of(brokerIdValue(memberRemoveOperation.memberToRemove())));
          case final PartitionDisableExporterOperation disableExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_DISABLE_EXPORTER)
                  .brokerId(brokerIdValue(disableExporterOperation.memberId()))
                  .partitionId(disableExporterOperation.partitionId())
                  .exporterId(disableExporterOperation.exporterId());
          case final PartitionEnableExporterOperation enableExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_ENABLE_EXPORTER)
                  .brokerId(brokerIdValue(enableExporterOperation.memberId()))
                  .partitionId(enableExporterOperation.partitionId())
                  .exporterId(enableExporterOperation.exporterId());
          case final PartitionDeleteExporterOperation deleteExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_DELETE_EXPORTER)
                  .brokerId(brokerIdValue(deleteExporterOperation.memberId()))
                  .partitionId(deleteExporterOperation.partitionId())
                  .exporterId(deleteExporterOperation.exporterId());
          case final StartPartitionScaleUp startScaleUp ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.START_PARTITION_SCALE_UP)
                  .brokerId(brokerIdValue(startScaleUp.memberId()));
          case final PartitionBootstrapOperation bootstrapOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_BOOTSTRAP)
                  .brokerId(brokerIdValue(bootstrapOperation.memberId()))
                  .partitionId(bootstrapOperation.partitionId())
                  .priority(bootstrapOperation.priority());
          case final PartitionPreRestoreOperation preRestoreOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_PRE_RESTORE)
                  .brokerId(brokerIdValue(preRestoreOperation.memberId()))
                  .partitionId(preRestoreOperation.partitionId());
          case final PartitionRestoreOperation restoreOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_RESTORE)
                  .brokerId(brokerIdValue(restoreOperation.memberId()))
                  .partitionId(restoreOperation.partitionId());
          case final DeleteHistoryOperation deleteHistoryOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.DELETE_HISTORY);
          case final AwaitRedistributionCompletion redistributionCompletion ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.AWAIT_REDISTRIBUTION)
                  .brokerId(brokerIdValue(redistributionCompletion.memberId()));
          case final AwaitRelocationCompletion relocationCompletion ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.AWAIT_RELOCATION)
                  .brokerId(brokerIdValue(relocationCompletion.memberId()));
          case final UpdateRoutingState updateRoutingState ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UPDATE_ROUTING_STATE)
                  .brokerId(brokerIdValue(updateRoutingState.memberId()));
          case final UpdateIncarnationNumberOperation updateIncarnationNumberOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UPDATE_INCARNATION_NUMBER)
                  .brokerId(brokerIdValue(updateIncarnationNumberOperation.memberId()));
          case final PreScalingOperation preScalingOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PRE_SCALING)
                  .brokerId(brokerIdValue(preScalingOperation.memberId()))
                  .brokers(
                      preScalingOperation.clusterMembers().stream()
                          .map(ClusterApiUtils::brokerIdValue)
                          .toList());
          case final PostScalingOperation postScalingOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.POST_SCALING)
                  .brokerId(brokerIdValue(postScalingOperation.memberId()))
                  .brokers(
                      postScalingOperation.clusterMembers().stream()
                          .map(ClusterApiUtils::brokerIdValue)
                          .toList());
          case final UpdatePartitionDistributorConfigOperation
                  updatePartitionDistributorConfigOperation ->
              new TopologyChangeCompletedInner()
                  .brokerId(brokerIdValue(updatePartitionDistributorConfigOperation.memberId()))
                  .operation(
                      TopologyChangeCompletedInner.OperationEnum
                          .UPDATE_PARTITION_DISTRIBUTOR_CONFIG)
                  .partitionDistributionConfig(
                      toPartitionDistributionConfig(updatePartitionDistributorConfigOperation));
          case final ModeChangeOperation modeChange ->
              switch (modeChange.mode()) {
                case RECOVERING ->
                    new TopologyChangeCompletedInner()
                        .operation(TopologyChangeCompletedInner.OperationEnum.ENTER_RECOVERY)
                        .brokerId(brokerIdValue(modeChange.memberId()));
                case PROCESSING ->
                    new TopologyChangeCompletedInner()
                        .operation(TopologyChangeCompletedInner.OperationEnum.EXIT_RECOVERY)
                        .brokerId(brokerIdValue(modeChange.memberId()));
              };
          case final AwaitModeChangeOperation modeChange ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.AWAIT_MODE_CHANGE)
                  .brokerId(brokerIdValue(modeChange.memberId()));
          case final RemovePhysicalTenantOperation removal ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.REMOVE_PHYSICAL_TENANT)
                  .brokerId(brokerIdValue(removal.memberId()));
          default ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UNKNOWN);
        };

    mappedOperation.completedAt(mapInstantToDateTime(operation.completedAt()));

    return mappedOperation;
  }

  private static io.camunda.zeebe.management.cluster.PartitionDistributionConfig
      toPartitionDistributionConfig(final UpdatePartitionDistributorConfigOperation operation) {
    final var config = new io.camunda.zeebe.management.cluster.PartitionDistributionConfig();
    switch (operation.config()) {
      case final FixedConfig fixedConfig -> {
        config.type(TypeEnum.FIXED);
      }
      case final RoundRobinConfig roundRobinConfig -> {
        config.type(TypeEnum.ROUND_ROBIN);
      }
      case final ZoneAwareConfig zoneAwareConfig -> {
        config.type(TypeEnum.ZONE_AWARE);
        config.zones(
            zoneAwareConfig.zones().stream()
                .map(
                    z ->
                        new io.camunda.zeebe.management.cluster.ZoneSpec(
                            z.name(), z.numberOfReplicas(), z.priority()))
                .toList());
      }
    }
    return config;
  }

  /**
   * Aggregates the exporter state of every physical tenant, or of {@code physicalTenant} alone when
   * given. Exporter configuration is per physical tenant, so an exporter id may exist in some
   * tenants only and the same id can be in different states in each of them; each tenant is
   * therefore aggregated on its own and reported as its own entry, tagged with {@code
   * physicalTenant}, rather than reduced across tenants into one status that belongs to none of
   * them. Entries are grouped by tenant, tenants in ascending id order.
   *
   * <p>Every entry carries the tag, including a single-tenant cluster's — where it reads {@code
   * default} — so the field means the same thing in every response instead of being present only
   * when a caller could have deduced it anyway. This stays backwards compatible because the field
   * is not required: a client that predates physical tenants ignores it.
   */
  static List<ExporterStatus> aggregateExporterState(
      final CurrentClusterConfiguration configuration, final @Nullable String physicalTenant) {
    final List<String> tenantsInView =
        physicalTenant == null
            ? configuration.partitionGroups().keySet().stream().sorted().toList()
            : List.of(physicalTenant);

    return tenantsInView.stream()
        .flatMap(
            tenant ->
                aggregateExporterState(configuration.toLegacy(tenant)).stream()
                    .map(status -> status.physicalTenant(tenant)))
        .toList();
  }

  static List<ExporterStatus> aggregateExporterState(
      final ClusterConfiguration clusterConfiguration) {
    // Map of ExporterId => List of ExporterState (each item corresponds to a partition)
    final var exporters =
        clusterConfiguration.members().values().stream()
            .flatMap(
                m ->
                    m.partitions().values().stream()
                        .flatMap(p -> p.config().exporting().exporters().entrySet().stream()))
            .collect(
                Collectors.groupingBy(Entry::getKey, mapping(e -> e.getValue().state(), toList())));
    return exporters.entrySet().stream()
        .map(
            e ->
                // Aggregate exporters state from all partition to a single ExporterStatus
                e.getValue().stream()
                    .distinct()
                    .map(s -> transformState(e.getKey(), s))
                    .reduce(
                        (status, other) -> reduceExporterState(status, other, clusterConfiguration))
                    .orElse(
                        // This case would never happen, as we are reducing a non-empty stream
                        new ExporterStatus()
                            .exporterId(e.getKey())
                            .status(ExporterStatus.StatusEnum.UNKNOWN)))
        .toList();
  }

  private static ExporterStatus reduceExporterState(
      final ExporterStatus status,
      final ExporterStatus other,
      final ClusterConfiguration clusterConfiguration) {
    if (status.getStatus().equals(other.getStatus()) && !clusterConfiguration.hasPendingChanges()) {
      return status;
    }

    return clusterConfiguration
        .pendingChanges()
        .flatMap(
            p ->
                p.pendingOperations().stream()
                    .findAny()
                    .map(operation -> getExporterStatus(status, operation)))
        .orElse(
            new ExporterStatus()
                .exporterId(status.getExporterId())
                .status(ExporterStatus.StatusEnum.UNKNOWN));
  }

  private static ExporterStatus getExporterStatus(
      final ExporterStatus status, final ClusterConfigurationChangeOperation operation) {
    final var statusEnum =
        switch (operation) {
          case final PartitionEnableExporterOperation ignored -> ExporterStatus.StatusEnum.ENABLING;
          case final PartitionDisableExporterOperation ignored ->
              ExporterStatus.StatusEnum.DISABLING;
          default -> ExporterStatus.StatusEnum.UNKNOWN;
        };

    return new ExporterStatus().exporterId(status.getExporterId()).status(statusEnum);
  }

  private static ExporterStatus transformState(
      final String exporterId, final ExporterState.State state) {
    return switch (state) {
      case ENABLED ->
          new ExporterStatus().exporterId(exporterId).status(ExporterStatus.StatusEnum.ENABLED);
      case DISABLED ->
          new ExporterStatus().exporterId(exporterId).status(ExporterStatus.StatusEnum.DISABLED);
      case CONFIG_NOT_FOUND ->
          new ExporterStatus()
              .exporterId(exporterId)
              .status(ExporterStatus.StatusEnum.CONFIG_NOT_FOUND);
    };
  }
}
