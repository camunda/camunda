/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse;
import io.camunda.zeebe.rebalance.RebalanceOverrides;
import io.camunda.zeebe.rebalance.RebalanceRequestSender;
import io.camunda.zeebe.rebalance.RebalanceStatus;
import io.camunda.zeebe.rebalance.TriggerRebalanceRequest;
import io.camunda.zeebe.util.Either;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Coordinated cluster-wide leadership rebalancing - connects the API at {@code
 * /cluster/v2/rebalance} to the cluster's rebalance coordinator.
 */
@NullMarked
public final class ClusterRebalanceServices {

  private final RebalanceRequestSender rebalanceRequestSender;

  public ClusterRebalanceServices(final RebalanceRequestSender rebalanceRequestSender) {
    this.rebalanceRequestSender = rebalanceRequestSender;
  }

  public CompletableFuture<RebalanceStatus> triggerRebalance(
      final ClusterRebalanceRequest request) {
    final RebalanceOverrides overrides;
    try {
      overrides =
          new RebalanceOverrides(
              request.replicationLagThreshold(),
              request.replicationTimeout(),
              request.maxTransferAttempts(),
              request.leaderWaitTimeout());
    } catch (final IllegalArgumentException e) {
      return CompletableFuture.failedFuture(
          new ServiceException(e.getMessage(), Status.INVALID_ARGUMENT));
    }
    return rebalanceRequestSender
        .triggerRebalance(new TriggerRebalanceRequest(overrides, request.dryRun()))
        .handle(ClusterRebalanceServices::toStatusOrThrow);
  }

  public CompletableFuture<RebalanceStatus> getRebalanceStatus() {
    return rebalanceRequestSender
        .getRebalanceStatus()
        .handle(ClusterRebalanceServices::toStatusOrThrow);
  }

  public CompletableFuture<CancelRebalanceResponse> cancelRebalance() {
    return rebalanceRequestSender
        .cancelRebalance()
        .handle(ClusterRebalanceServices::toCancellationOrThrow);
  }

  private static RebalanceStatus toStatusOrThrow(
      final @Nullable Either<RebalanceErrorResponse, RebalanceStatus> response,
      final @Nullable Throwable error) {
    if (error != null) {
      throw toServiceException(error);
    }
    if (response == null) {
      throw noAnswer();
    }
    if (response.isLeft()) {
      throw toServiceException(response.getLeft());
    }
    return response.get();
  }

  private static CancelRebalanceResponse toCancellationOrThrow(
      final @Nullable Either<RebalanceErrorResponse, CancelRebalanceResponse> response,
      final @Nullable Throwable error) {
    if (error != null) {
      throw toServiceException(error);
    }
    if (response == null) {
      throw noAnswer();
    }
    if (response.isLeft()) {
      throw toServiceException(response.getLeft());
    }
    return response.get();
  }

  private static ServiceException noAnswer() {
    return new ServiceException("The coordinator gave no answer to the request", Status.ABORTED);
  }

  private static ServiceException toServiceException(final RebalanceErrorResponse response) {
    final var status =
        switch (response.code()) {
          case REBALANCE_IN_PROGRESS, CONFIGURATION_CHANGE_IN_PROGRESS -> Status.ALREADY_EXISTS;
          case NOT_COORDINATOR -> Status.UNAVAILABLE;
          case INTERNAL_ERROR -> Status.INTERNAL;
        };
    return new ServiceException(response.message(), status);
  }

  private static ServiceException toServiceException(final Throwable error) {
    final var cause = unwrap(error);
    if (cause instanceof final ServiceException serviceException) {
      return serviceException;
    }
    final var status =
        switch (cause) {
          case final ConnectException ignored -> Status.UNAVAILABLE;
          case final NoSuchMemberException ignored -> Status.UNAVAILABLE;
          case final TimeoutException ignored -> Status.DEADLINE_EXCEEDED;
          default -> Status.ABORTED;
        };
    return new ServiceException(cause.getMessage(), status);
  }

  private static Throwable unwrap(final Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return unwrap(error.getCause());
    }
    return error;
  }

  public record ClusterRebalanceRequest(
      boolean dryRun,
      @Nullable Long replicationLagThreshold,
      @Nullable Duration replicationTimeout,
      @Nullable Integer maxTransferAttempts,
      @Nullable Duration leaderWaitTimeout) {

    public static ClusterRebalanceRequest withDefaultSettings(final boolean dryRun) {
      return new ClusterRebalanceRequest(dryRun, null, null, null, null);
    }
  }
}
