/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import com.google.protobuf.InvalidProtocolBufferException;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.rebalance.protocol.Rebalance;
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
              .setCancelRequested(running.cancelRequested()));
    }
    final var lastCompleted = status.lastCompleted();
    if (lastCompleted != null) {
      builder.setLastCompleted(
          Rebalance.CompletedRebalance.newBuilder()
              .setRebalanceId(lastCompleted.rebalanceId())
              .setOutcome(encodeOutcome(lastCompleted.outcome())));
    }
    return builder.build();
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
        running.getCancelRequested());
  }

  private RebalanceStatus.Completed decodeCompleted(final Rebalance.CompletedRebalance completed) {
    return new RebalanceStatus.Completed(
        completed.getRebalanceId(), decodeOutcome(completed.getOutcome()));
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
      case INTERNAL_ERROR -> Rebalance.RebalanceErrorCode.REBALANCE_ERROR_UNSPECIFIED;
    };
  }

  private RebalanceErrorCode decodeErrorCode(final Rebalance.RebalanceErrorCode code) {
    return switch (code) {
      case REBALANCE_ERROR_IN_PROGRESS -> RebalanceErrorCode.REBALANCE_IN_PROGRESS;
      case REBALANCE_ERROR_NOT_COORDINATOR -> RebalanceErrorCode.NOT_COORDINATOR;
      case REBALANCE_ERROR_UNSPECIFIED, UNRECOGNIZED -> RebalanceErrorCode.INTERNAL_ERROR;
    };
  }
}
