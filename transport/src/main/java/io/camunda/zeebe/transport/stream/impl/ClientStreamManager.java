/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.agrona.DirectBuffer;

final class ClientStreamManager<M extends BufferWriter> {
  private final ClientStreamRegistry<M> registry;
  private final ClientStreamRequestManager<M> requestManager;
  private final Set<MemberId> servers = new HashSet<>();

  ClientStreamManager(
      final ClientStreamRegistry<M> registry, final ClientStreamRequestManager<M> requestManager) {
    this.registry = registry;
    this.requestManager = requestManager;
  }

  /**
   * When a new server is added to the cluster, or an existing server restarted, existing client
   * streams must be registered (again) with the server.
   *
   * @param serverId id of the server that is added or restarted
   */
  void onServerJoined(final MemberId serverId) {
    servers.add(serverId);
    registry.list().forEach(c -> requestManager.openStream(c, Collections.singleton(serverId)));
  }

  UUID add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    final var streamId = UUID.randomUUID();
    final var clientStreamMeta =
        new ClientStream<>(streamId, streamType, metadata, clientStreamConsumer);

    // add first in memory to handle case of new broker while we're adding
    registry.add(clientStreamMeta);
    requestManager.openStream(clientStreamMeta, servers);

    return streamId;
  }

  void remove(final UUID streamId) {
    final var clientStream = registry.remove(streamId);
    requestManager.removeStream(clientStream, servers);
  }

  void removeAll() {
    requestManager.removeAll(servers);
  }
}
