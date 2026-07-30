/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import com.google.protobuf.InvalidProtocolBufferException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.dynamic.config.rebalance.protocol.Rebalance;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProtoBufRebalanceSerializer implements RebalanceRequestsSerializer {

  @Override
  public byte[] encodeTriggerRebalanceRequest(final TriggerRebalanceRequest request) {
    return Rebalance.TriggerRebalanceRequest.newBuilder()
        .setOverrides(encodeOverrides(request.overrides()))
        .setDryRun(request.dryRun())
        .build()
        .toByteArray();
  }

  @Override
  public TriggerRebalanceRequest decodeTriggerRebalanceRequest(final byte[] encodedRequest) {
    try {
      final var request = Rebalance.TriggerRebalanceRequest.parseFrom(encodedRequest);
      return new TriggerRebalanceRequest(
          decodeOverrides(request.getOverrides()), request.getDryRun());
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  @Override
  public byte[] encodeResponse(final RebalanceStatus response) {
    return Rebalance.Response.newBuilder().setStatus(encodeStatus(response)).build().toByteArray();
  }

  @Override
  public byte[] encodeResponse(final CancelRebalanceResponse response) {
    return Rebalance.Response.newBuilder()
        .setCancelled(
            Rebalance.CancelRebalanceResponse.newBuilder().setWasRunning(response.wasRunning()))
        .build()
        .toByteArray();
  }

  @Override
  public byte[] encodeResponse(final RebalanceErrorResponse response) {
    return Rebalance.Response.newBuilder()
        .setError(
            Rebalance.RebalanceErrorResponse.newBuilder()
                .setErrorCode(encodeErrorCode(response.code()))
                .setErrorMessage(response.message()))
        .build()
        .toByteArray();
  }

  @Override
  public Either<RebalanceErrorResponse, RebalanceStatus> decodeRebalanceStatusResponse(
      final byte[] encodedResponse) {
    final var response = parseResponse(encodedResponse);
    if (response.hasError()) {
      return Either.left(decodeError(response.getError()));
    }
    if (response.hasStatus()) {
      return Either.right(decodeStatus(response.getStatus()));
    }
    throw new DecodingFailed("Response has neither an error nor a rebalance status");
  }

  @Override
  public Either<RebalanceErrorResponse, CancelRebalanceResponse> decodeCancelRebalanceResponse(
      final byte[] encodedResponse) {
    final var response = parseResponse(encodedResponse);
    if (response.hasError()) {
      return Either.left(decodeError(response.getError()));
    }
    if (response.hasCancelled()) {
      return Either.right(new CancelRebalanceResponse(response.getCancelled().getWasRunning()));
    }
    throw new DecodingFailed("Response has neither an error nor a cancellation");
  }

  private Rebalance.Response parseResponse(final byte[] encodedResponse) {
    try {
      return Rebalance.Response.parseFrom(encodedResponse);
    } catch (final InvalidProtocolBufferException e) {
      throw new DecodingFailed(e);
    }
  }

  private Rebalance.RebalanceOverrides encodeOverrides(final RebalanceOverrides overrides) {
    final var builder = Rebalance.RebalanceOverrides.newBuilder();
    if (overrides.replicationLagThreshold() != null) {
      builder.setReplicationLagThresholdBytes(overrides.replicationLagThreshold());
    }
    if (overrides.replicationTimeout() != null) {
      builder.setReplicationTimeoutMillis(overrides.replicationTimeout().toMillis());
    }
    if (overrides.maxTransferAttempts() != null) {
      builder.setMaxTransferAttempts(overrides.maxTransferAttempts());
    }
    if (overrides.leaderWaitTimeout() != null) {
      builder.setLeaderWaitTimeoutMillis(overrides.leaderWaitTimeout().toMillis());
    }
    return builder.build();
  }

  private RebalanceOverrides decodeOverrides(final Rebalance.RebalanceOverrides overrides) {
    return new RebalanceOverrides(
        overrides.hasReplicationLagThresholdBytes()
            ? overrides.getReplicationLagThresholdBytes()
            : null,
        overrides.hasReplicationTimeoutMillis()
            ? Duration.ofMillis(overrides.getReplicationTimeoutMillis())
            : null,
        overrides.hasMaxTransferAttempts() ? overrides.getMaxTransferAttempts() : null,
        overrides.hasLeaderWaitTimeoutMillis()
            ? Duration.ofMillis(overrides.getLeaderWaitTimeoutMillis())
            : null);
  }

  private Rebalance.RebalanceStatusResponse encodeStatus(final RebalanceStatus status) {
    final var builder = Rebalance.RebalanceStatusResponse.newBuilder();
    final var running = status.running();
    if (running != null) {
      builder.setRunning(
          Rebalance.RunningRebalance.newBuilder()
              .setRebalanceId(running.rebalanceId())
              .setOverrides(encodeOverrides(running.overrides()))
              .setDryRun(running.dryRun())
              .setCancelRequested(running.cancelRequested())
              .addAllPartitions(running.partitions().stream().map(this::encodePartition).toList()));
    }
    final var lastCompleted = status.lastCompleted();
    if (lastCompleted != null) {
      builder.setLastCompleted(
          Rebalance.CompletedRebalance.newBuilder()
              .setRebalanceId(lastCompleted.rebalanceId())
              .setOutcome(encodeOutcome(lastCompleted.outcome()))
              .setDryRun(lastCompleted.dryRun())
              .addAllPartitions(
                  lastCompleted.partitions().stream().map(this::encodePartition).toList()));
    }
    return builder.build();
  }

  private Rebalance.PartitionRebalance encodePartition(final PartitionRebalance partition) {
    final var builder =
        Rebalance.PartitionRebalance.newBuilder()
            .setPhysicalTenantId(partition.physicalTenantId())
            .setPartitionId(partition.partitionId())
            .setState(encodePartitionState(partition.state()));
    if (partition.currentLeader() != null) {
      builder.setCurrentLeader(partition.currentLeader().id());
    }
    if (partition.desiredLeader() != null) {
      builder.setDesiredLeader(partition.desiredLeader().id());
    }
    if (partition.reason() != null) {
      builder.setReason(partition.reason());
    }
    return builder.build();
  }

  private PartitionRebalance decodePartition(final Rebalance.PartitionRebalance partition) {
    return new PartitionRebalance(
        partition.getPhysicalTenantId(),
        partition.getPartitionId(),
        partition.hasCurrentLeader() ? MemberId.from(partition.getCurrentLeader()) : null,
        partition.hasDesiredLeader() ? MemberId.from(partition.getDesiredLeader()) : null,
        decodePartitionState(partition.getState()),
        partition.hasReason() ? partition.getReason() : null);
  }

  private Rebalance.PartitionRebalance.State encodePartitionState(
      final PartitionRebalanceState state) {
    return switch (state) {
      case PENDING -> Rebalance.PartitionRebalance.State.PENDING;
      case TRANSFERRING -> Rebalance.PartitionRebalance.State.TRANSFERRING;
      case TRANSFERRED -> Rebalance.PartitionRebalance.State.TRANSFERRED;
      case SKIPPED -> Rebalance.PartitionRebalance.State.SKIPPED;
      case FAILED -> Rebalance.PartitionRebalance.State.FAILED;
      case CANCELLED -> Rebalance.PartitionRebalance.State.CANCELLED;
    };
  }

  private PartitionRebalanceState decodePartitionState(
      final Rebalance.PartitionRebalance.State state) {
    return switch (state) {
      case PENDING -> PartitionRebalanceState.PENDING;
      case TRANSFERRING -> PartitionRebalanceState.TRANSFERRING;
      case TRANSFERRED -> PartitionRebalanceState.TRANSFERRED;
      case SKIPPED -> PartitionRebalanceState.SKIPPED;
      case CANCELLED -> PartitionRebalanceState.CANCELLED;
      case FAILED, UNRECOGNIZED -> PartitionRebalanceState.FAILED;
    };
  }

  private RebalanceStatus decodeStatus(final Rebalance.RebalanceStatusResponse status) {
    return new RebalanceStatus(
        status.hasRunning() ? decodeRunning(status.getRunning()) : null,
        status.hasLastCompleted() ? decodeCompleted(status.getLastCompleted()) : null);
  }

  private RebalanceStatus.Running decodeRunning(final Rebalance.RunningRebalance running) {
    return new RebalanceStatus.Running(
        running.getRebalanceId(),
        decodeOverrides(running.getOverrides()),
        running.getDryRun(),
        running.getCancelRequested(),
        running.getPartitionsList().stream().map(this::decodePartition).toList());
  }

  private RebalanceStatus.Completed decodeCompleted(final Rebalance.CompletedRebalance completed) {
    return new RebalanceStatus.Completed(
        completed.getRebalanceId(),
        decodeOutcome(completed.getOutcome()),
        completed.getDryRun(),
        completed.getPartitionsList().stream().map(this::decodePartition).toList());
  }

  private RebalanceErrorResponse decodeError(final Rebalance.RebalanceErrorResponse error) {
    return new RebalanceErrorResponse(
        decodeErrorCode(error.getErrorCode()), error.getErrorMessage());
  }

  private Rebalance.RebalanceOutcome encodeOutcome(final RebalanceOutcome outcome) {
    return switch (outcome) {
      case COMPLETED -> Rebalance.RebalanceOutcome.COMPLETED;
      case CANCELLED -> Rebalance.RebalanceOutcome.CANCELLED;
      case FAILED -> Rebalance.RebalanceOutcome.FAILED;
    };
  }

  private RebalanceOutcome decodeOutcome(final Rebalance.RebalanceOutcome outcome) {
    return switch (outcome) {
      case COMPLETED -> RebalanceOutcome.COMPLETED;
      case CANCELLED -> RebalanceOutcome.CANCELLED;
      case FAILED -> RebalanceOutcome.FAILED;
      case REBALANCE_OUTCOME_UNSPECIFIED, UNRECOGNIZED ->
          throw new DecodingFailed("Rebalance outcome is missing or unrecognized: " + outcome);
    };
  }

  private Rebalance.RebalanceErrorCode encodeErrorCode(final RebalanceErrorCode code) {
    return switch (code) {
      case REBALANCE_IN_PROGRESS -> Rebalance.RebalanceErrorCode.REBALANCE_IN_PROGRESS;
      case NOT_COORDINATOR -> Rebalance.RebalanceErrorCode.NOT_COORDINATOR;
      case INTERNAL_ERROR -> Rebalance.RebalanceErrorCode.INTERNAL_ERROR;
    };
  }

  private RebalanceErrorCode decodeErrorCode(final Rebalance.RebalanceErrorCode code) {
    return switch (code) {
      case REBALANCE_IN_PROGRESS -> RebalanceErrorCode.REBALANCE_IN_PROGRESS;
      case NOT_COORDINATOR -> RebalanceErrorCode.NOT_COORDINATOR;
      case INTERNAL_ERROR, UNRECOGNIZED -> RebalanceErrorCode.INTERNAL_ERROR;
    };
  }
}
