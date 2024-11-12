/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamResponse;
import io.camunda.zeebe.transport.stream.impl.messages.StreamResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.collections.ArrayUtil;

final class ClientStreamApiHandler {
  private final ClientStreamManager<?> clientStreamManager;
  private final Executor executor;

  ClientStreamApiHandler(
      final ClientStreamManager<?> clientStreamManager, final Executor executor) {
    this.clientStreamManager = clientStreamManager;
    this.executor = executor;
  }

  CompletableFuture<StreamResponse> handlePushRequest(final PushStreamRequest request) {
    final CompletableFuture<StreamResponse> responseFuture = new CompletableFuture<>();

    final ActorFuture<Void> payloadPushed = new CompletableActorFuture<>();
    clientStreamManager.onPayloadReceived(request, payloadPushed);
    payloadPushed.onComplete((ok, error) -> handlePayloadPushed(responseFuture, error), executor);

    return responseFuture;
  }

  byte[] handleRestartRequest(final MemberId sender, final byte[] ignored) {
    clientStreamManager.onServerRemoved(MemberId.from(sender.id()));
    clientStreamManager.onServerJoined(MemberId.from(sender.id()));
    return ArrayUtil.EMPTY_BYTE_ARRAY;
  }

  private void handlePayloadPushed(
      final CompletableFuture<StreamResponse> response, final Throwable error) {
    if (error == null) {
      response.complete(new PushStreamResponse());
      return;
    }

    final var errorResponse =
        new ErrorResponse().code(ErrorResponse.mapErrorToCode(error)).message(error.getMessage());
    for (final var detail : error.getSuppressed()) {
      errorResponse.addDetail(ErrorResponse.mapErrorToCode(detail), detail.getMessage());
    }

    response.complete(errorResponse);
  }
}
