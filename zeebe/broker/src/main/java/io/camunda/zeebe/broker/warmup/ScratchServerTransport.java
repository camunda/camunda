/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestHandler;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerResponse;
import io.camunda.zeebe.transport.ServerTransport;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stands in for the network: requests are handed straight to the subscribed command handler, and
 * responses are serialised exactly as {@code AtomixServerTransport} does before being handed back
 * to the workload instead of a remote gateway.
 */
@NullMarked
final class ScratchServerTransport implements ServerTransport {

  private final BiConsumer<Long, DirectBuffer> responseConsumer;
  private volatile @Nullable RequestHandler commandHandler;

  ScratchServerTransport(final BiConsumer<Long, DirectBuffer> responseConsumer) {
    this.responseConsumer = responseConsumer;
  }

  @Override
  public ActorFuture<Void> subscribe(
      final PartitionId partitionId,
      final RequestType requestType,
      final RequestHandler requestHandler) {
    if (requestType == RequestType.COMMAND) {
      commandHandler = requestHandler;
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> unsubscribe(
      final PartitionId partitionId, final RequestType requestType) {
    if (requestType == RequestType.COMMAND) {
      commandHandler = null;
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public void sendResponse(final ServerResponse response) {
    final var bytes = new byte[response.getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    response.write(buffer, 0);
    responseConsumer.accept(response.getRequestId(), buffer);
  }

  /** Returns false if no command handler is subscribed yet, i.e. the partition is not ready. */
  boolean sendCommand(final int partitionId, final long requestId, final DirectBuffer request) {
    final var handler = commandHandler;
    if (handler == null) {
      return false;
    }
    handler.onRequest(this, partitionId, requestId, request, 0, request.capacity());
    return true;
  }

  @Override
  public void close() {
    commandHandler = null;
  }
}
