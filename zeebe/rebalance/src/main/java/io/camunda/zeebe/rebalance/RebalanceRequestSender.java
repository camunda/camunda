/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Forwards rebalance requests to the coordinator (the lowest-id member of the cluster).
 *
 * <p>Rebalances are run asynchronously, so these requests only ever start, check, or stop one (none
 * of them waits for a rebalance to finish).
 */
public final class RebalanceRequestSender {
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final ClusterCommunicationService communicationService;
  private final ClusterConfigurationCoordinatorSupplier coordinatorSupplier;
  private final RebalanceRequestsSerializer serializer;

  public RebalanceRequestSender(
      final ClusterCommunicationService communicationService,
      final ClusterConfigurationCoordinatorSupplier coordinatorSupplier,
      final RebalanceRequestsSerializer serializer) {
    this.communicationService = communicationService;
    this.coordinatorSupplier = coordinatorSupplier;
    this.serializer = serializer;
  }

  public CompletableFuture<Either<RebalanceErrorResponse, RebalanceStatus>> triggerRebalance(
      final TriggerRebalanceRequest request) {
    return communicationService.send(
        RebalanceRequestTopics.TRIGGER_REBALANCE.topic(),
        request,
        serializer::encodeTriggerRebalanceRequest,
        serializer::decodeRebalanceStatusResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<RebalanceErrorResponse, RebalanceStatus>> getRebalanceStatus() {
    return communicationService.send(
        RebalanceRequestTopics.REBALANCE_STATUS.topic(),
        new byte[0],
        Function.identity(),
        serializer::decodeRebalanceStatusResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }

  public CompletableFuture<Either<RebalanceErrorResponse, CancelRebalanceResponse>>
      cancelRebalance() {
    return communicationService.send(
        RebalanceRequestTopics.CANCEL_REBALANCE.topic(),
        new byte[0],
        Function.identity(),
        serializer::decodeCancelRebalanceResponse,
        coordinatorSupplier.getDefaultCoordinator(),
        TIMEOUT);
  }
}
