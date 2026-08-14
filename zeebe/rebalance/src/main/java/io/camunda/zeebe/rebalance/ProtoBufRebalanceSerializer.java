/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.rebalance.protocol.Rebalance;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

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
        overrides.hasMaxTransferAttempts() ? overrides.getMaxTransferAttempts() : null);
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
                  lastCompleted.partitions().stream().map(this::encodePartition).toList())
              .setStartedAt(toTimestamp(lastCompleted.startedAt()))
              .setFinishedAt(toTimestamp(lastCompleted.finishedAt())));
    }
    return builder.build();
  }

  private Rebalance.PartitionRebalance encodePartition(final PartitionRebalance partition) {
    final var builder =
        Rebalance.PartitionRebalance.newBuilder()
            .setPhysicalTenantId(partition.physicalTenantId())
            .setPartitionId(partition.partitionId())
            .setDesiredLeader(partition.desiredLeader().id())
            .setProgress(encodePartitionProgress(partition.progress()));
    if (partition.currentLeader() != null) {
      builder.setCurrentLeader(partition.currentLeader().id());
    }
    if (partition.outcome() != null) {
      builder.setOutcome(encodePartitionOutcome(partition.outcome()));
    }
    return builder.build();
  }

  private PartitionRebalance decodePartition(final Rebalance.PartitionRebalance partition) {
    if (!partition.hasDesiredLeader()) {
      throw new DecodingFailed("A partition rebalance is missing its desired leader: " + partition);
    }
    final var progress = decodePartitionProgress(partition.getProgress());
    return new PartitionRebalance(
        partition.getPhysicalTenantId(),
        partition.getPartitionId(),
        partition.hasCurrentLeader() ? MemberId.from(partition.getCurrentLeader()) : null,
        MemberId.from(partition.getDesiredLeader()),
        progress,
        decodePartitionOutcome(progress, partition));
  }

  private Rebalance.PartitionRebalance.Progress encodePartitionProgress(
      final PartitionRebalanceProgress progress) {
    return switch (progress) {
      case PENDING -> Rebalance.PartitionRebalance.Progress.PENDING;
      case TRANSFERRING -> Rebalance.PartitionRebalance.Progress.TRANSFERRING;
      case COMPLETED -> Rebalance.PartitionRebalance.Progress.COMPLETED;
    };
  }

  private PartitionRebalanceProgress decodePartitionProgress(
      final Rebalance.PartitionRebalance.Progress progress) {
    return switch (progress) {
      case PENDING -> PartitionRebalanceProgress.PENDING;
      case TRANSFERRING -> PartitionRebalanceProgress.TRANSFERRING;
      case COMPLETED -> PartitionRebalanceProgress.COMPLETED;
      case PROGRESS_UNSPECIFIED, UNRECOGNIZED ->
          throw new DecodingFailed(
              "Partition rebalance progress is missing or unrecognized: " + progress);
    };
  }

  /**
   * An outcome is required once {@code progress} is {@link PartitionRebalanceProgress#COMPLETED}
   * and must not otherwise be present, matching {@link PartitionRebalance}'s own invariant.
   */
  private @Nullable PartitionRebalanceOutcome decodePartitionOutcome(
      final PartitionRebalanceProgress progress, final Rebalance.PartitionRebalance partition) {
    if (progress != PartitionRebalanceProgress.COMPLETED) {
      if (partition.hasOutcome()) {
        throw new DecodingFailed(
            "A partition rebalance with progress %s must not have an outcome: %s"
                .formatted(progress, partition));
      }
      return null;
    }
    if (!partition.hasOutcome()) {
      throw new DecodingFailed(
          "A completed partition rebalance is missing its outcome: " + partition);
    }
    return decodePartitionOutcomeValue(partition.getOutcome());
  }

  private Rebalance.PartitionRebalance.Outcome encodePartitionOutcome(
      final PartitionRebalanceOutcome outcome) {
    return switch (outcome) {
      case TRANSFERRED -> Rebalance.PartitionRebalance.Outcome.TRANSFERRED;
      case ALREADY_LEADER -> Rebalance.PartitionRebalance.Outcome.ALREADY_LEADER;
      case NOT_MEMBER -> Rebalance.PartitionRebalance.Outcome.NOT_MEMBER;
      case NOT_REPLICATING -> Rebalance.PartitionRebalance.Outcome.NOT_REPLICATING;
      case UNREACHABLE -> Rebalance.PartitionRebalance.Outcome.UNREACHABLE;
      case NOT_COORDINATOR -> Rebalance.PartitionRebalance.Outcome.NOT_COORDINATOR;
      case STALE_CONFIGURATION -> Rebalance.PartitionRebalance.Outcome.STALE_CONFIGURATION;
      case TRANSFER_IN_PROGRESS -> Rebalance.PartitionRebalance.Outcome.TRANSFER_IN_PROGRESS;
      case LAG_TOO_HIGH -> Rebalance.PartitionRebalance.Outcome.LAG_TOO_HIGH;
      case LEADER_INITIALIZING -> Rebalance.PartitionRebalance.Outcome.LEADER_INITIALIZING;
      case CONFIGURATION_CHANGE_IN_PROGRESS ->
          Rebalance.PartitionRebalance.Outcome.CONFIGURATION_CHANGE_IN_PROGRESS;
      case PAUSE_FAILED -> Rebalance.PartitionRebalance.Outcome.PAUSE_FAILED;
      case REPLICATION_TIMED_OUT -> Rebalance.PartitionRebalance.Outcome.REPLICATION_TIMED_OUT;
      case TIMEOUT_NOW_EXHAUSTED -> Rebalance.PartitionRebalance.Outcome.TIMEOUT_NOW_EXHAUSTED;
      case LEADER_CHANGED -> Rebalance.PartitionRebalance.Outcome.LEADER_CHANGED;
      case NO_LEADER -> Rebalance.PartitionRebalance.Outcome.NO_LEADER;
      case NO_RESPONSE -> Rebalance.PartitionRebalance.Outcome.NO_RESPONSE;
      case CANCELLED -> Rebalance.PartitionRebalance.Outcome.CANCELLED;
    };
  }

  private PartitionRebalanceOutcome decodePartitionOutcomeValue(
      final Rebalance.PartitionRebalance.Outcome outcome) {
    return switch (outcome) {
      case TRANSFERRED -> PartitionRebalanceOutcome.TRANSFERRED;
      case ALREADY_LEADER -> PartitionRebalanceOutcome.ALREADY_LEADER;
      case NOT_MEMBER -> PartitionRebalanceOutcome.NOT_MEMBER;
      case NOT_REPLICATING -> PartitionRebalanceOutcome.NOT_REPLICATING;
      case UNREACHABLE -> PartitionRebalanceOutcome.UNREACHABLE;
      case NOT_COORDINATOR -> PartitionRebalanceOutcome.NOT_COORDINATOR;
      case STALE_CONFIGURATION -> PartitionRebalanceOutcome.STALE_CONFIGURATION;
      case TRANSFER_IN_PROGRESS -> PartitionRebalanceOutcome.TRANSFER_IN_PROGRESS;
      case LAG_TOO_HIGH -> PartitionRebalanceOutcome.LAG_TOO_HIGH;
      case LEADER_INITIALIZING -> PartitionRebalanceOutcome.LEADER_INITIALIZING;
      case CONFIGURATION_CHANGE_IN_PROGRESS ->
          PartitionRebalanceOutcome.CONFIGURATION_CHANGE_IN_PROGRESS;
      case PAUSE_FAILED -> PartitionRebalanceOutcome.PAUSE_FAILED;
      case REPLICATION_TIMED_OUT -> PartitionRebalanceOutcome.REPLICATION_TIMED_OUT;
      case TIMEOUT_NOW_EXHAUSTED -> PartitionRebalanceOutcome.TIMEOUT_NOW_EXHAUSTED;
      case LEADER_CHANGED -> PartitionRebalanceOutcome.LEADER_CHANGED;
      case NO_LEADER -> PartitionRebalanceOutcome.NO_LEADER;
      case NO_RESPONSE -> PartitionRebalanceOutcome.NO_RESPONSE;
      case CANCELLED -> PartitionRebalanceOutcome.CANCELLED;
      case OUTCOME_UNSPECIFIED, UNRECOGNIZED ->
          throw new DecodingFailed(
              "Partition rebalance outcome is missing or unrecognized: " + outcome);
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
        completed.getPartitionsList().stream().map(this::decodePartition).toList(),
        fromTimestamp(completed.getStartedAt()),
        fromTimestamp(completed.getFinishedAt()));
  }

  private RebalanceErrorResponse decodeError(final Rebalance.RebalanceErrorResponse error) {
    return new RebalanceErrorResponse(
        decodeErrorCode(error.getErrorCode()), error.getErrorMessage());
  }

  private Rebalance.RebalanceOutcome encodeOutcome(final RebalanceOutcome outcome) {
    return switch (outcome) {
      case COMPLETED -> Rebalance.RebalanceOutcome.REBALANCE_COMPLETED;
      case CANCELLED -> Rebalance.RebalanceOutcome.REBALANCE_CANCELLED;
      case FAILED -> Rebalance.RebalanceOutcome.REBALANCE_FAILED;
    };
  }

  private RebalanceOutcome decodeOutcome(final Rebalance.RebalanceOutcome outcome) {
    return switch (outcome) {
      case REBALANCE_COMPLETED -> RebalanceOutcome.COMPLETED;
      case REBALANCE_CANCELLED -> RebalanceOutcome.CANCELLED;
      case REBALANCE_FAILED -> RebalanceOutcome.FAILED;
      case REBALANCE_OUTCOME_UNSPECIFIED, UNRECOGNIZED ->
          throw new DecodingFailed("Rebalance outcome is missing or unrecognized: " + outcome);
    };
  }

  private Rebalance.RebalanceErrorCode encodeErrorCode(final RebalanceErrorCode code) {
    return switch (code) {
      case REBALANCE_IN_PROGRESS -> Rebalance.RebalanceErrorCode.REBALANCE_ERROR_IN_PROGRESS;
      case NOT_COORDINATOR -> Rebalance.RebalanceErrorCode.REBALANCE_ERROR_NOT_COORDINATOR;
      case CONFIGURATION_CHANGE_IN_PROGRESS ->
          Rebalance.RebalanceErrorCode.REBALANCE_ERROR_CONFIGURATION_CHANGE_IN_PROGRESS;
      case INTERNAL_ERROR -> Rebalance.RebalanceErrorCode.REBALANCE_ERROR_UNSPECIFIED;
    };
  }

  private RebalanceErrorCode decodeErrorCode(final Rebalance.RebalanceErrorCode code) {
    return switch (code) {
      case REBALANCE_ERROR_IN_PROGRESS -> RebalanceErrorCode.REBALANCE_IN_PROGRESS;
      case REBALANCE_ERROR_NOT_COORDINATOR -> RebalanceErrorCode.NOT_COORDINATOR;
      case REBALANCE_ERROR_CONFIGURATION_CHANGE_IN_PROGRESS ->
          RebalanceErrorCode.CONFIGURATION_CHANGE_IN_PROGRESS;
      case REBALANCE_ERROR_UNSPECIFIED, UNRECOGNIZED -> RebalanceErrorCode.INTERNAL_ERROR;
    };
  }

  private static Timestamp toTimestamp(final Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant fromTimestamp(final Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }
}
