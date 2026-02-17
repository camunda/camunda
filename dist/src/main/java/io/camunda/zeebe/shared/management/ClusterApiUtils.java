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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.ExporterConfig;
import io.camunda.zeebe.management.cluster.ExporterStateCode;
import io.camunda.zeebe.management.cluster.ExporterStatus;
import io.camunda.zeebe.management.cluster.ExportingConfig;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionConfig;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
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
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;

final class ClusterApiUtils {
  private static final OffsetDateTime MIN_PARSER_COMPLIANT_DATE =
      OffsetDateTime.parse("0000-01-01T00:00:00Z");
  private static final String MESSAGE_CORRELATION_STRATEGY_HASH_MOD = "HashMod";

  private ClusterApiUtils() {
    throw new IllegalStateException("Utility class");
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
          case CONCURRENT_MODIFICATION -> 409;
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
      final Either<ErrorResponse, ClusterConfiguration> response) {
    if (response.isRight()) {
      return ResponseEntity.status(200).body(mapClusterTopology(response.get()));
    } else {
      return ClusterApiUtils.mapErrorResponse(response.getLeft());
    }
  }

  private static PlannedOperationsResponse mapResponseType(
      final ClusterConfigurationChangeResponse response) {
    return new PlannedOperationsResponse()
        .changeId(response.changeId())
        .currentTopology(mapBrokerStates(response.currentConfiguration()))
        .expectedTopology(mapBrokerStates(response.expectedConfiguration()))
        .plannedChanges(mapOperations(response.plannedChanges()));
  }

  private static List<Operation> mapOperations(
      final List<ClusterConfigurationChangeOperation> operations) {
    return operations.stream().map(ClusterApiUtils::mapOperation).toList();
  }

  static Operation mapOperation(final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final MemberJoinOperation join ->
          new Operation()
              .operation(OperationEnum.BROKER_ADD)
              .brokerId(Integer.parseInt(join.memberId().id()));
      case final MemberLeaveOperation leave ->
          new Operation()
              .operation(OperationEnum.BROKER_REMOVE)
              .brokerId(Integer.parseInt(leave.memberId().id()));
      case final PartitionJoinOperation join ->
          new Operation()
              .operation(OperationEnum.PARTITION_JOIN)
              .brokerId(Integer.parseInt(join.memberId().id()))
              .partitionId(join.partitionId())
              .priority(join.priority());
      case final PartitionLeaveOperation leave ->
          new Operation()
              .operation(OperationEnum.PARTITION_LEAVE)
              .brokerId(Integer.parseInt(leave.memberId().id()))
              .partitionId(leave.partitionId());
      case final PartitionReconfigurePriorityOperation reconfigure ->
          new Operation()
              .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
              .brokerId(Integer.parseInt(reconfigure.memberId().id()))
              .partitionId(reconfigure.partitionId())
              .priority(reconfigure.priority());
      case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_FORCE_RECONFIGURE)
              .brokerId(Integer.parseInt(partitionForceReconfigureOperation.memberId().id()))
              .partitionId(partitionForceReconfigureOperation.partitionId())
              .brokers(
                  partitionForceReconfigureOperation.members().stream()
                      .map(MemberId::id)
                      .map(Integer::parseInt)
                      .collect(toList()));
      case final MemberRemoveOperation memberRemoveOperation ->
          new Operation()
              .operation(OperationEnum.BROKER_REMOVE)
              .brokerId(Integer.parseInt(memberRemoveOperation.memberId().id()))
              .brokers(List.of(Integer.parseInt(memberRemoveOperation.memberToRemove().id())));
      case final PartitionDisableExporterOperation disableExporterOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_DISABLE_EXPORTER)
              .brokerId(Integer.parseInt(disableExporterOperation.memberId().id()))
              .partitionId(disableExporterOperation.partitionId())
              .exporterId(disableExporterOperation.exporterId());
      case final PartitionEnableExporterOperation enableExporterOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_ENABLE_EXPORTER)
              .brokerId(Integer.parseInt(enableExporterOperation.memberId().id()))
              .partitionId(enableExporterOperation.partitionId())
              .exporterId(enableExporterOperation.exporterId());
      case final PartitionDeleteExporterOperation deleteExporterOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_DELETE_EXPORTER)
              .brokerId(Integer.parseInt(deleteExporterOperation.memberId().id()))
              .partitionId(deleteExporterOperation.partitionId())
              .exporterId(deleteExporterOperation.exporterId());
      case final StartPartitionScaleUp startScaleUp ->
          new Operation()
              .operation(OperationEnum.START_PARTITION_SCALE_UP)
              .brokerId(Integer.parseInt(startScaleUp.memberId().id()));
      case final PartitionBootstrapOperation bootstrapOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_BOOTSTRAP)
              .brokerId(Integer.parseInt(bootstrapOperation.memberId().id()))
              .partitionId(bootstrapOperation.partitionId())
              .priority(bootstrapOperation.priority());
      case final DeleteHistoryOperation deleteHistoryOperation ->
          new Operation().operation(OperationEnum.DELETE_HISTORY);
      case final AwaitRedistributionCompletion redistributionCompletion ->
          new Operation()
              .operation(OperationEnum.AWAIT_REDISTRIBUTION)
              .brokerId(Integer.parseInt(redistributionCompletion.memberId().id()));
      case final AwaitRelocationCompletion relocationCompletion ->
          new Operation()
              .operation(OperationEnum.AWAIT_RELOCATION)
              .brokerId(Integer.parseInt(relocationCompletion.memberId().id()));
      case final UpdateRoutingState updateRoutingState ->
          new Operation()
              .operation(OperationEnum.UPDATE_ROUTING_STATE)
              .brokerId(Integer.parseInt(updateRoutingState.memberId().id()));
      case final UpdateIncarnationNumberOperation updateIncarnationNumberOperation ->
          new Operation().operation(OperationEnum.UPDATE_INCARNATION_NUMBER);
      case final PreScalingOperation preScalingOperation ->
          new Operation()
              .operation(OperationEnum.PRE_SCALING)
              .brokerId(Integer.parseInt(preScalingOperation.memberId().id()))
              .brokers(
                  preScalingOperation.clusterMembers().stream()
                      .map(MemberId::id)
                      .map(Integer::parseInt)
                      .toList());
      default -> new Operation().operation(OperationEnum.UNKNOWN);
    };
  }

  private static List<BrokerState> mapBrokerStates(
      final SortedMap<MemberId, MemberState> topology) {
    return topology.entrySet().stream()
        .map(
            entry ->
                new BrokerState()
                    .id(Integer.parseInt(entry.getKey().id()))
                    .state(mapBrokerState(entry.getValue().state()))
                    .lastUpdatedAt(mapInstantToDateTime(entry.getValue().lastUpdated()))
                    .version(entry.getValue().version())
                    .partitions(mapPartitionStates(entry.getValue().partitions())))
        .toList();
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
    };
  }

  private static List<PartitionState> mapPartitionStates(
      final SortedMap<Integer, io.camunda.zeebe.dynamic.config.state.PartitionState> partitions) {
    return partitions.entrySet().stream()
        .map(
            entry ->
                new PartitionState()
                    .id(entry.getKey())
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

  private static PartitionStateCode mapPartitionState(final State state) {
    return switch (state) {
      case JOINING -> PartitionStateCode.JOINING;
      case ACTIVE -> PartitionStateCode.ACTIVE;
      case LEAVING -> PartitionStateCode.LEAVING;
      // TODO: Define state code for BootStrapping
      case BOOTSTRAPPING, UNKNOWN -> PartitionStateCode.UNKNOWN;
    };
  }

  private static GetTopologyResponse mapClusterTopology(final ClusterConfiguration topology) {
    final var response = new GetTopologyResponse();
    final List<BrokerState> brokers = mapBrokerStates(topology.members());

    response.version(topology.version()).brokers(brokers);
    topology.lastChange().ifPresent(change -> response.lastChange(mapCompletedChange(change)));
    topology.pendingChanges().ifPresent(change -> response.pendingChange(mapOngoingChange(change)));
    topology
        .routingState()
        .ifPresent(routingState -> response.routing(mapRoutingState(routingState)));
    topology.clusterId().ifPresent(response::clusterId);
    return response;
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

  private static TopologyChange mapOngoingChange(final ClusterChangePlan clusterChangePlan) {
    return new TopologyChange()
        .id(clusterChangePlan.id())
        .status(mapChangeStatus(clusterChangePlan.status()))
        .pending(mapOperations(clusterChangePlan.pendingOperations()))
        .completed(mapCompletedOperations(clusterChangePlan.completedOperations()));
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
                  .brokerId(Integer.parseInt(join.memberId().id()));
          case final MemberLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(Integer.parseInt(leave.memberId().id()));
          case final PartitionJoinOperation join ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_JOIN)
                  .brokerId(Integer.parseInt(join.memberId().id()))
                  .partitionId(join.partitionId())
                  .priority(join.priority());
          case final PartitionLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_LEAVE)
                  .brokerId(Integer.parseInt(leave.memberId().id()))
                  .partitionId(leave.partitionId());
          case final PartitionReconfigurePriorityOperation reconfigure ->
              new TopologyChangeCompletedInner()
                  .operation(
                      TopologyChangeCompletedInner.OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                  .brokerId(Integer.parseInt(reconfigure.memberId().id()))
                  .partitionId(reconfigure.partitionId())
                  .priority(reconfigure.priority());
          case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_FORCE_RECONFIGURE)
                  .brokerId(Integer.parseInt(partitionForceReconfigureOperation.memberId().id()))
                  .partitionId(partitionForceReconfigureOperation.partitionId())
                  .brokers(
                      partitionForceReconfigureOperation.members().stream()
                          .map(MemberId::id)
                          .map(Integer::parseInt)
                          .toList());
          case final MemberRemoveOperation memberRemoveOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(Integer.parseInt(memberRemoveOperation.memberId().id()))
                  .brokers(List.of(Integer.parseInt(memberRemoveOperation.memberToRemove().id())));
          case final PartitionDisableExporterOperation disableExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_DISABLE_EXPORTER)
                  .brokerId(Integer.parseInt(disableExporterOperation.memberId().id()))
                  .partitionId(disableExporterOperation.partitionId())
                  .exporterId(disableExporterOperation.exporterId());
          case final PartitionEnableExporterOperation enableExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_ENABLE_EXPORTER)
                  .brokerId(Integer.parseInt(enableExporterOperation.memberId().id()))
                  .partitionId(enableExporterOperation.partitionId())
                  .exporterId(enableExporterOperation.exporterId());
          case final PartitionDeleteExporterOperation deleteExporterOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_DELETE_EXPORTER)
                  .brokerId(Integer.parseInt(deleteExporterOperation.memberId().id()))
                  .partitionId(deleteExporterOperation.partitionId())
                  .exporterId(deleteExporterOperation.exporterId());
          case final StartPartitionScaleUp startScaleUp ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.START_PARTITION_SCALE_UP)
                  .brokerId(Integer.parseInt(startScaleUp.memberId().id()));
          case final PartitionBootstrapOperation bootstrapOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_BOOTSTRAP)
                  .brokerId(Integer.parseInt(bootstrapOperation.memberId().id()))
                  .partitionId(bootstrapOperation.partitionId())
                  .priority(bootstrapOperation.priority());
          case final DeleteHistoryOperation deleteHistoryOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.DELETE_HISTORY);
          case final AwaitRedistributionCompletion redistributionCompletion ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.AWAIT_REDISTRIBUTION)
                  .brokerId(Integer.parseInt(redistributionCompletion.memberId().id()));
          case final AwaitRelocationCompletion relocationCompletion ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.AWAIT_RELOCATION)
                  .brokerId(Integer.parseInt(relocationCompletion.memberId().id()));
          case final UpdateRoutingState updateRoutingState ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UPDATE_ROUTING_STATE)
                  .brokerId(Integer.parseInt(updateRoutingState.memberId().id()));
          case final UpdateIncarnationNumberOperation updateIncarnationNumberOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UPDATE_INCARNATION_NUMBER)
                  .brokerId(Integer.parseInt(updateIncarnationNumberOperation.memberId().id()));
          case final PreScalingOperation preScalingOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PRE_SCALING)
                  .brokerId(Integer.parseInt(preScalingOperation.memberId().id()))
                  .brokers(
                      preScalingOperation.clusterMembers().stream()
                          .map(MemberId::id)
                          .map(Integer::parseInt)
                          .toList());
          default ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UNKNOWN);
        };

    mappedOperation.completedAt(mapInstantToDateTime(operation.completedAt()));

    return mappedOperation;
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
