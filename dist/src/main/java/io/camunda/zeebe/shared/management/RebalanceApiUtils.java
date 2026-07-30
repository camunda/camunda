/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.dynamic.config.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionRebalance;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceOverrides;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceStatus;
import io.camunda.zeebe.dynamic.config.rebalance.TriggerRebalanceRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.CompletedRebalance;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.PartitionRebalanceStatus;
import io.camunda.zeebe.management.cluster.RebalanceCancellationResponse;
import io.camunda.zeebe.management.cluster.RebalanceRequest;
import io.camunda.zeebe.management.cluster.RebalanceSettings;
import io.camunda.zeebe.management.cluster.RebalanceStatusResponse;
import io.camunda.zeebe.util.Either;
import java.net.ConnectException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;

/**
 * Translates between the rebalance endpoint's HTTP shapes and what the coordinator speaks.
 *
 * <p>Kept apart from {@link ClusterApiUtils} because the two answer for different things: that one
 * maps cluster configuration changes, which are long-running operations the caller follows by
 * change id, whereas a rebalance is followed by polling its own status.
 */
@NullMarked
final class RebalanceApiUtils {

  private RebalanceApiUtils() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Reads an operator's request body, defaulting every setting it leaves out.
   *
   * @throws IllegalArgumentException if a duration is not ISO-8601, or a setting is out of range
   */
  static TriggerRebalanceRequest toTriggerRequest(final @Nullable RebalanceRequest body) {
    if (body == null) {
      return TriggerRebalanceRequest.withConfiguredSettings();
    }
    final var overrides =
        new RebalanceOverrides(
            body.getReplicationLagThreshold(),
            duration("replicationTimeout", body.getReplicationTimeout()),
            body.getMaxTransferAttempts(),
            duration("leaderWaitTimeout", body.getLeaderWaitTimeout()));
    return new TriggerRebalanceRequest(overrides, Boolean.TRUE.equals(body.getDryRun()));
  }

  private static @Nullable Duration duration(final String field, final @Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Duration.parse(value);
    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(
          "%s must be an ISO-8601 duration such as PT30S but was '%s'".formatted(field, value), e);
    }
  }

  /**
   * Answers a request that reports the rebalance status, with {@code successStatus} when it did.
   */
  static ResponseEntity<?> mapStatusResponse(
      final @Nullable Either<RebalanceErrorResponse, RebalanceStatus> response,
      final @Nullable Throwable error,
      final int successStatus) {
    return mapResponse(
        response, error, status -> ResponseEntity.status(successStatus).body(mapStatus(status)));
  }

  static ResponseEntity<?> mapCancellationResponse(
      final @Nullable Either<RebalanceErrorResponse, CancelRebalanceResponse> response,
      final @Nullable Throwable error) {
    return mapResponse(
        response,
        error,
        cancelled ->
            ResponseEntity.status(200)
                .body(new RebalanceCancellationResponse().wasRunning(cancelled.wasRunning())));
  }

  private static <T> ResponseEntity<?> mapResponse(
      final @Nullable Either<RebalanceErrorResponse, T> response,
      final @Nullable Throwable error,
      final Function<T, ResponseEntity<?>> onSuccess) {
    if (error != null) {
      return mapError(error);
    }
    if (response == null) {
      return errorResponse(502, "The coordinator gave no answer to the request");
    }
    if (response.isLeft()) {
      return mapErrorResponse(response.getLeft());
    }
    return onSuccess.apply(response.get());
  }

  /**
   * Maps a refusal the coordinator reported for itself. Unlike {@link #mapError}, the exchange
   * worked and this is the coordinator's own answer, so a failure it names is reported as a failure
   * of the cluster rather than of the forwarding.
   */
  private static ResponseEntity<Error> mapErrorResponse(final RebalanceErrorResponse response) {
    final var status =
        switch (response.code()) {
          case REBALANCE_IN_PROGRESS -> 409;
          // The forwarding member's view of the configuration is behind, so whoever now coordinates
          // has not been told to this member yet. Retrying is what resolves it.
          case NOT_COORDINATOR -> 503;
          case INTERNAL_ERROR -> 500;
        };
    return errorResponse(status, response.message());
  }

  /** Maps a failure of the forwarding itself, so the coordinator's own answer never arrived. */
  private static ResponseEntity<Error> mapError(final Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return mapError(error.getCause());
    }
    final var status =
        switch (error) {
          case final ConnectException ignored -> 503;
          case final NoSuchMemberException ignored -> 503;
          case final TimeoutException ignored -> 504;
          default -> 502;
        };
    return errorResponse(status, error.getMessage());
  }

  private static ResponseEntity<Error> errorResponse(
      final int status, final @Nullable String message) {
    return ResponseEntity.status(status).body(new Error().message(message));
  }

  private static RebalanceStatusResponse mapStatus(final RebalanceStatus status) {
    final var response = new RebalanceStatusResponse().status(aggregateStatus(status));
    final var running = status.running();
    if (running != null) {
      response
          .rebalanceId(running.rebalanceId())
          .dryRun(running.dryRun())
          .settings(mapSettings(running.overrides()))
          .partitions(mapPartitions(running.partitions()));
    }
    final var lastCompleted = status.lastCompleted();
    if (lastCompleted != null) {
      response.lastCompletedRebalance(
          new CompletedRebalance()
              .rebalanceId(lastCompleted.rebalanceId())
              .outcome(CompletedRebalance.OutcomeEnum.fromValue(lastCompleted.outcome().name()))
              .dryRun(lastCompleted.dryRun())
              .partitions(mapPartitions(lastCompleted.partitions())));
    }
    return response;
  }

  private static RebalanceStatusResponse.StatusEnum aggregateStatus(final RebalanceStatus status) {
    final var running = status.running();
    if (running == null) {
      return RebalanceStatusResponse.StatusEnum.IDLE;
    }
    return running.cancelRequested()
        ? RebalanceStatusResponse.StatusEnum.CANCELLING
        : RebalanceStatusResponse.StatusEnum.RUNNING;
  }

  /**
   * Reports only the settings the operator overrode. The configured values the rest fall back to
   * belong to each partition's leader, so the coordinator does not know them to report.
   */
  private static RebalanceSettings mapSettings(final RebalanceOverrides overrides) {
    final var settings =
        new RebalanceSettings()
            .replicationLagThreshold(overrides.replicationLagThreshold())
            .maxTransferAttempts(overrides.maxTransferAttempts());
    if (overrides.replicationTimeout() != null) {
      settings.replicationTimeout(overrides.replicationTimeout().toString());
    }
    if (overrides.leaderWaitTimeout() != null) {
      settings.leaderWaitTimeout(overrides.leaderWaitTimeout().toString());
    }
    return settings;
  }

  private static List<PartitionRebalanceStatus> mapPartitions(
      final List<PartitionRebalance> partitions) {
    return partitions.stream().map(RebalanceApiUtils::mapPartition).toList();
  }

  private static PartitionRebalanceStatus mapPartition(final PartitionRebalance partition) {
    return new PartitionRebalanceStatus()
        .id(partition.partitionId())
        .physicalTenantId(partition.physicalTenantId())
        .currentLeader(brokerId(partition.currentLeader()))
        .desiredLeader(brokerId(partition.desiredLeader()))
        .status(PartitionRebalanceStatus.StatusEnum.fromValue(partition.state().name()))
        .reason(partition.reason());
  }

  private static @Nullable BrokerId brokerId(final @Nullable MemberId memberId) {
    return memberId == null ? null : ClusterApiUtils.brokerIdValue(memberId);
  }
}
