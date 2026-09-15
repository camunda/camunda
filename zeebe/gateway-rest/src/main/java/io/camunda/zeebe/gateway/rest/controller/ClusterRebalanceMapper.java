/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ClusterBalanceResponse;
import io.camunda.gateway.protocol.model.ClusterCompletedRebalance;
import io.camunda.gateway.protocol.model.ClusterRebalanceOperationPartition;
import io.camunda.gateway.protocol.model.ClusterRebalancePartition;
import io.camunda.gateway.protocol.model.ClusterRebalanceRequest;
import io.camunda.gateway.protocol.model.ClusterRunningRebalance;
import io.camunda.gateway.protocol.model.RebalanceCancellationResponse;
import io.camunda.service.ClusterRebalanceServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.rebalance.ClusterLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionRebalance;
import io.camunda.zeebe.rebalance.PartitionRebalanceOutcome;
import io.camunda.zeebe.rebalance.PartitionRebalanceProgress;
import io.camunda.zeebe.rebalance.RebalanceOutcome;
import io.camunda.zeebe.rebalance.RebalanceStatus;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class ClusterRebalanceMapper {

  private ClusterRebalanceMapper() {}

  static ClusterRebalanceServices.ClusterRebalanceRequest toServiceRequest(
      final boolean dryRun, final @Nullable ClusterRebalanceRequest body) {
    if (body == null) {
      return ClusterRebalanceServices.ClusterRebalanceRequest.withDefaultSettings(dryRun);
    }
    return new ClusterRebalanceServices.ClusterRebalanceRequest(
        dryRun,
        body.getReplicationLagThreshold(),
        duration("replicationTimeout", body.getReplicationTimeout()),
        body.getMaxTransferAttempts(),
        duration("leaderWaitTimeout", body.getLeaderWaitTimeout()));
  }

  private static @Nullable Duration duration(final String field, final @Nullable String value) {
    if (value == null) {
      return null;
    }
    final Duration parsed;
    try {
      parsed = Duration.parse(value);
    } catch (final DateTimeParseException e) {
      throw new ServiceException(
          "%s must be an ISO-8601 duration such as PT30S but was '%s'".formatted(field, value),
          Status.INVALID_ARGUMENT);
    }
    if (parsed.isNegative() || parsed.isZero()) {
      throw new ServiceException(
          "%s must be a positive ISO-8601 duration but was '%s'".formatted(field, value),
          Status.INVALID_ARGUMENT);
    }
    return parsed;
  }

  static ClusterBalanceResponse toClusterBalanceResponse(final RebalanceStatus status) {
    final var leadershipStatus = status.leadershipStatus();
    final var response =
        ClusterBalanceResponse.Builder.create()
            .state(toStateEnum(leadershipStatus.state()))
            .partitions(
                leadershipStatus.partitions().stream()
                    .map(ClusterRebalanceMapper::toPartition)
                    .toList());
    final var lastCompleted = status.lastCompleted();
    final var running = status.running();
    return response
        .runningRebalance(running == null ? null : toRunningRebalance(running))
        .lastCompletedRebalance(lastCompleted == null ? null : toCompletedRebalance(lastCompleted))
        .build();
  }

  private static ClusterRunningRebalance toRunningRebalance(final RebalanceStatus.Running running) {
    return ClusterRunningRebalance.Builder.create()
        .rebalanceId(running.rebalanceId())
        .partitions(
            running.partitions().stream()
                .map(ClusterRebalanceMapper::toRebalancePartition)
                .toList())
        .startedAt(ResponseMapper.formatDate(running.startedAt().atOffset(ZoneOffset.UTC)))
        .dryRun(running.dryRun())
        .cancelRequested(running.cancelRequested())
        .build();
  }

  private static ClusterRebalanceOperationPartition toRebalancePartition(
      final PartitionRebalance partition) {
    final var outcome = partition.outcome();
    return ClusterRebalanceOperationPartition.Builder.create()
        .partitionId(partition.partitionId())
        .physicalTenantId(partition.physicalTenantId())
        .currentLeader(partition.currentLeaderId())
        .desiredLeader(partition.desiredLeaderId())
        .progress(toProgressEnum(partition.progress()))
        .result(outcome == null ? null : toPartitionResultEnum(outcome))
        .build();
  }

  private static ClusterRebalanceOperationPartition.ProgressEnum toProgressEnum(
      final PartitionRebalanceProgress progress) {
    return switch (progress) {
      case PENDING -> ClusterRebalanceOperationPartition.ProgressEnum.PENDING;
      case TRANSFERRING -> ClusterRebalanceOperationPartition.ProgressEnum.TRANSFERRING;
      case COMPLETED -> ClusterRebalanceOperationPartition.ProgressEnum.COMPLETED;
    };
  }

  private static ClusterRebalanceOperationPartition.ResultEnum toPartitionResultEnum(
      final PartitionRebalanceOutcome outcome) {
    return switch (outcome) {
      case TRANSFERRED -> ClusterRebalanceOperationPartition.ResultEnum.TRANSFERRED;
      case ALREADY_LEADER -> ClusterRebalanceOperationPartition.ResultEnum.ALREADY_LEADER;
      case NOT_MEMBER -> ClusterRebalanceOperationPartition.ResultEnum.NOT_MEMBER;
      case NOT_REPLICATING -> ClusterRebalanceOperationPartition.ResultEnum.NOT_REPLICATING;
      case UNREACHABLE -> ClusterRebalanceOperationPartition.ResultEnum.UNREACHABLE;
      case NOT_COORDINATOR -> ClusterRebalanceOperationPartition.ResultEnum.NOT_COORDINATOR;
      case STALE_CONFIGURATION -> ClusterRebalanceOperationPartition.ResultEnum.STALE_CONFIGURATION;
      case TRANSFER_IN_PROGRESS ->
          ClusterRebalanceOperationPartition.ResultEnum.TRANSFER_IN_PROGRESS;
      case LAG_TOO_HIGH -> ClusterRebalanceOperationPartition.ResultEnum.LAG_TOO_HIGH;
      case LEADER_INITIALIZING -> ClusterRebalanceOperationPartition.ResultEnum.LEADER_INITIALIZING;
      case CONFIGURATION_CHANGE_IN_PROGRESS ->
          ClusterRebalanceOperationPartition.ResultEnum.CONFIGURATION_CHANGE_IN_PROGRESS;
      case PAUSE_FAILED -> ClusterRebalanceOperationPartition.ResultEnum.PAUSE_FAILED;
      case REPLICATION_TIMED_OUT ->
          ClusterRebalanceOperationPartition.ResultEnum.REPLICATION_TIMED_OUT;
      case TIMEOUT_NOW_EXHAUSTED ->
          ClusterRebalanceOperationPartition.ResultEnum.TIMEOUT_NOW_EXHAUSTED;
      case LEADER_CHANGED -> ClusterRebalanceOperationPartition.ResultEnum.LEADER_CHANGED;
      case NO_LEADER -> ClusterRebalanceOperationPartition.ResultEnum.NO_LEADER;
      case NO_RESPONSE -> ClusterRebalanceOperationPartition.ResultEnum.NO_RESPONSE;
      case CANCELLED -> ClusterRebalanceOperationPartition.ResultEnum.CANCELLED;
      case PHYSICAL_TENANT_DISABLED ->
          ClusterRebalanceOperationPartition.ResultEnum.PHYSICAL_TENANT_DISABLED;
      case PHYSICAL_TENANT_RECOVERING ->
          ClusterRebalanceOperationPartition.ResultEnum.PHYSICAL_TENANT_RECOVERING;
    };
  }

  static RebalanceCancellationResponse toRebalanceCancellationResponse(
      final CancelRebalanceResponse cancellation) {
    return RebalanceCancellationResponse.Builder.create()
        .wasRunning(cancellation.wasRunning())
        .build();
  }

  private static ClusterBalanceResponse.StateEnum toStateEnum(
      final ClusterLeadershipStatus.State state) {
    return switch (state) {
      case BALANCED -> ClusterBalanceResponse.StateEnum.BALANCED;
      case BALANCING -> ClusterBalanceResponse.StateEnum.BALANCING;
      case UNBALANCED -> ClusterBalanceResponse.StateEnum.UNBALANCED;
    };
  }

  private static ClusterRebalancePartition toPartition(final PartitionLeadershipStatus partition) {
    return ClusterRebalancePartition.Builder.create()
        .partitionId(partition.partitionId())
        .physicalTenantId(partition.physicalTenantId())
        .currentLeader(partition.currentLeaderId())
        .desiredLeader(partition.desiredLeaderId())
        .state(toPartitionStateEnum(partition.state()))
        .build();
  }

  private static ClusterRebalancePartition.StateEnum toPartitionStateEnum(
      final PartitionLeadershipStatus.State state) {
    return switch (state) {
      case TRANSFERRING -> ClusterRebalancePartition.StateEnum.TRANSFERRING;
      case UNBALANCED -> ClusterRebalancePartition.StateEnum.UNBALANCED;
      case BALANCED -> ClusterRebalancePartition.StateEnum.BALANCED;
    };
  }

  private static ClusterCompletedRebalance toCompletedRebalance(
      final RebalanceStatus.Completed completed) {
    return ClusterCompletedRebalance.Builder.create()
        .rebalanceId(completed.rebalanceId())
        .partitions(
            completed.partitions().stream()
                .map(ClusterRebalanceMapper::toRebalancePartition)
                .toList())
        .startedAt(ResponseMapper.formatDate(completed.startedAt().atOffset(ZoneOffset.UTC)))
        .finishedAt(ResponseMapper.formatDate(completed.finishedAt().atOffset(ZoneOffset.UTC)))
        .result(toResultEnum(completed.outcome()))
        .build();
  }

  private static ClusterCompletedRebalance.ResultEnum toResultEnum(final RebalanceOutcome result) {
    return switch (result) {
      case COMPLETED -> ClusterCompletedRebalance.ResultEnum.COMPLETED;
      case CANCELLED -> ClusterCompletedRebalance.ResultEnum.CANCELLED;
      case FAILED -> ClusterCompletedRebalance.ResultEnum.FAILED;
    };
  }
}
