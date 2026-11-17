/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Receives the forwarded rebalance requests on every member and answers them from the local {@link
 * RebalanceApi} implementation. On any node other than the current coordinator, the API refuses
 * requests with {@link RebalanceErrorCode#NOT_COORDINATOR}.
 */
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
    final var failure = FuturesUtil.unwrapCompletionException(throwable);
    final var message = Objects.toString(failure.getMessage(), failure.toString());

    final RebalanceErrorCode code;
    if (failure instanceof final RebalanceRequestFailedException rebalanceException) {
      code = rebalanceException.getErrorCode();
    } else {
      code = RebalanceErrorCode.INTERNAL_ERROR;
    }

    return Either.left(new RebalanceErrorResponse(code, message));
  }
}
