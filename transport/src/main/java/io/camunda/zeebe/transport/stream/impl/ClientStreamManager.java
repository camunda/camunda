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
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientStreamManager<M extends BufferWriter> {
  private static final Logger LOG = LoggerFactory.getLogger(ClientStreamManager.class);
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

  void onServerRemoved(final MemberId serverId) {
    servers.remove(serverId);
    registry.list().forEach(clientStream -> clientStream.remove(serverId));
  }

  UUID add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    final var streamId = UUID.randomUUID();

    // add first in memory to handle case of new broker while we're adding
    final AggregatedClientStream<M> serverStream =
        registry.addClient(streamId, streamType, metadata, clientStreamConsumer);
    requestManager.openStream(serverStream, servers);

    return streamId;
  }

  void remove(final UUID streamId) {
    final var clientStream = registry.removeClient(streamId);
    clientStream.ifPresent(
        stream -> {
          stream.close();
          requestManager.removeStream(stream, servers);
        });
  }

  void removeAll() {
    requestManager.removeAll(servers);
  }

  public void onPayloadReceived(
      final PushStreamRequest pushStreamRequest, final CompletableFuture<Void> responseFuture) {
    final var streamId = pushStreamRequest.streamId();
    final var payload = pushStreamRequest.payload();

    final var clientStream = registry.get(streamId);
    clientStream.ifPresentOrElse(
        stream -> {
          stream.getClientStreamConsumer().push(payload);
          responseFuture.complete(null);
        },
        () -> {
          // Stream does not exist. We expect to have already sent remove request to all servers.
          // But just in case that request is lost, we send remove request again. To keep it simple,
          // we do not retry. Otherwise, it is possible that we send it multiple times unnecessary.
          requestManager.removeStreamUnreliable(streamId, servers);
          LOG.warn("Expected to push payload to stream {}, but no stream found.", streamId);
          responseFuture.completeExceptionally(new NoSuchStreamException());
        });
  }
}
