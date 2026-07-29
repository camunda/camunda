/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/**
 * Receives the forwarded rebalance requests on every member and answers them from the local {@link
 * RebalanceApi}. Only the coordinating member has a coordinator to answer them, so anywhere else
 * the api itself refuses with {@link RebalanceErrorCode#NOT_COORDINATOR}.
 */
@NullMarked
public final class RebalanceRequestServer implements AutoCloseable {

  private final ClusterCommunicationService communicationService;
  private final RebalanceRequestsSerializer serializer;
  private final RebalanceApi rebalanceApi;

  public RebalanceRequestServer(
      final ClusterCommunicationService communicationService,
      final RebalanceRequestsSerializer serializer,
      final RebalanceApi rebalanceApi) {
    this.communicationService = communicationService;
    this.serializer = serializer;
    this.rebalanceApi = rebalanceApi;
  }

  public void start() {
    communicationService.replyTo(
        RebalanceRequestTopics.TRIGGER_REBALANCE.topic(),
        serializer::decodeTriggerRebalanceRequest,
        request -> mapResponse(rebalanceApi.triggerRebalance(request)),
        this::encodeStatusResponse);
    communicationService.replyTo(
        RebalanceRequestTopics.REBALANCE_STATUS.topic(),
        Function.identity(),
        request -> mapResponse(rebalanceApi.getRebalanceStatus()),
        this::encodeStatusResponse);
    communicationService.replyTo(
        RebalanceRequestTopics.CANCEL_REBALANCE.topic(),
        Function.identity(),
        request -> mapResponse(rebalanceApi.cancelRebalance()),
        this::encodeCancelResponse);
  }

  @Override
  public void close() {
    Stream.of(RebalanceRequestTopics.values())
        .forEach(topic -> communicationService.unsubscribe(topic.topic()));
  }

  private byte[] encodeStatusResponse(
      final Either<RebalanceErrorResponse, RebalanceStatus> response) {
    return response.isLeft()
        ? serializer.encodeResponse(response.getLeft())
        : serializer.encodeResponse(response.get());
  }

  private byte[] encodeCancelResponse(
      final Either<RebalanceErrorResponse, CancelRebalanceResponse> response) {
    return response.isLeft()
        ? serializer.encodeResponse(response.getLeft())
        : serializer.encodeResponse(response.get());
  }

  private static <T> CompletableFuture<Either<RebalanceErrorResponse, T>> mapResponse(
      final ActorFuture<T> result) {
    return result
        .toCompletableFuture()
        .thenApply(Either::<RebalanceErrorResponse, T>right)
        .exceptionally(RebalanceRequestServer::mapError);
  }

  private static <T> Either<RebalanceErrorResponse, T> mapError(final Throwable throwable) {
    // throwable is a CompletionException wrapping what the coordinator threw
    final var wrapped = throwable.getCause();
    final var failure = wrapped != null ? wrapped : throwable;
    final var message = Objects.toString(failure.getMessage(), failure.toString());
    final RebalanceErrorCode code;
    if (failure instanceof RebalanceRequestFailedException.RebalanceInProgress) {
      code = RebalanceErrorCode.REBALANCE_IN_PROGRESS;
    } else if (failure instanceof RebalanceRequestFailedException.NotCoordinator) {
      code = RebalanceErrorCode.NOT_COORDINATOR;
    } else {
      code = RebalanceErrorCode.INTERNAL_ERROR;
    }
    return Either.left(new RebalanceErrorResponse(code, message));
  }
}
