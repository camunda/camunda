/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.UUID;
import org.agrona.DirectBuffer;

public class ClientStreamService<M extends BufferWriter> extends Actor
    implements ClientStreamer<M> {

  private final ClientStreamManager<M> clientStreamManager;

  public ClientStreamService(final ClusterCommunicationService communicationService) {
    // ClientStreamRequestManager must use same actor as this because it is mutating shared
    // ClientStream objects.
    clientStreamManager =
        new ClientStreamManager<>(
            new ClientStreamRegistry<>(),
            new ClientStreamRequestManager<>(communicationService, actor));
  }

  @Override
  public ActorFuture<UUID> add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    return actor.call(() -> clientStreamManager.add(streamType, metadata, clientStreamConsumer));
  }

  @Override
  public ActorFuture<Void> remove(final UUID streamId) {
    return actor.call(() -> clientStreamManager.remove(streamId));
  }

  @Override
  protected void onActorCloseRequested() {
    clientStreamManager.removeAll();
  }
}
