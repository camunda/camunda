/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientStreamManager extends Actor {
  private final ClientStreamRegistry registry;
  private final RemoteStreamClient client;
  private final Supplier<Collection<MemberId>> brokerSupplier;

  public void onNewServer(final MemberId serverId) {
    registry.list().forEach(c -> client.add(c, Collections.singleton(serverId)));
  }

  public ActorFuture<UUID> add(final ZpaStream<?, ?> zpaStream) {
    return actor.call(() -> doAdd(zpaStream));
  }

  private UUID doAdd(final ZpaStream<?, ?> zpaStream) {
    final var streamId = UUID.randomUUID();
    final var clientStream = new ClientStream(streamId, zpaStream);

    // add first in memory to handle case of new broker while we're adding
    registry.add(clientStream);
    final var brokers = brokerSupplier.get();
    client.add(clientStream, brokers);

    return streamId;
  }

  public ActorFuture<Void> remove(final UUID streamId) {
    return actor.call(() -> doRemove(streamId));
  }

  private void doRemove(final UUID streamId) {
    // remove in memory first for the listener stuff
    final ClientStream clientStream = registry.remove(streamId);
    client.remove(clientStream);
  }
}
