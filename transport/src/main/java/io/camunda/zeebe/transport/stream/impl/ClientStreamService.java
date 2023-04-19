/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.transport.stream.impl.messages.MessageUtil;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;

public class ClientStreamService<M extends BufferWriter> extends Actor
    implements ClientStreamer<M> {

  private final ClientStreamManager<M> clientStreamManager;
  private final ClusterCommunicationService communicationService;

  public ClientStreamService(final ClusterCommunicationService communicationService) {
    // ClientStreamRequestManager must use same actor as this because it is mutating shared
    // ClientStream objects.
    clientStreamManager =
        new ClientStreamManager<>(
            new ClientStreamRegistry<>(),
            new ClientStreamRequestManager<>(communicationService, actor));
    this.communicationService = communicationService;
  }

  @Override
  protected void onActorStarted() {
    // TODO: Define an PushResponse to inform server if push was successful or not. Currently, an
    // exception will be received by the server response handler.
    communicationService.subscribe(
        StreamTopics.PUSH.topic(),
        MessageUtil::parsePushRequest,
        request -> {
          final CompletableFuture<Void> future = new CompletableFuture<>();
          actor.run(
              () -> {
                try {
                  clientStreamManager.onPayloadReceived(request, future);
                } catch (final Exception e) {
                  future.completeExceptionally(e);
                }
              });
          return future;
        },
        ignore -> new byte[0]);
  }

  @Override
  protected void onActorCloseRequested() {
    clientStreamManager.removeAll();
  }

  @Override
  public ActorFuture<ClientStreamId> add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    return actor.call(() -> clientStreamManager.add(streamType, metadata, clientStreamConsumer));
  }

  @Override
  public ActorFuture<Void> remove(final ClientStreamId streamId) {
    return actor.call(() -> clientStreamManager.remove(streamId));
  }

  public void onServerJoined(final MemberId memberId) {
    actor.run(() -> clientStreamManager.onServerJoined(memberId));
  }

  public void onServerRemoved(final MemberId memberId) {
    actor.run(() -> clientStreamManager.onServerRemoved(memberId));
  }
}
