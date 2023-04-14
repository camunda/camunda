/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.agrona.DirectBuffer;

/** A registry to keeps tracks of all open streams. */
final class ClientStreamRegistry<M extends BufferWriter> {
  private final Map<UUID, ClientStream<M>> clientStreams = new HashMap<>();
  private final Map<UUID, AggregatedClientStream<M>> serverStreams = new HashMap<>();

  private final Map<Tuple<DirectBuffer, M>, UUID> serverStreamIds = new HashMap<>();

  Optional<AggregatedClientStream<M>> get(final UUID streamId) {
    return Optional.ofNullable(serverStreams.get(streamId));
  }

  Collection<AggregatedClientStream<M>> list() {
    return serverStreams.values();
  }

  AggregatedClientStream<M> addClient(
      final UUID streamId,
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    // Find serverStreamId given streamType and metadata. Once a server stream is removed, a new
    // server stream with same streamType and metadata will get a new UUID.
    final var serverStreamId =
        serverStreamIds.computeIfAbsent(new Tuple<>(streamType, metadata), k -> UUID.randomUUID());
    final var serverStream =
        serverStreams.computeIfAbsent(
            serverStreamId,
            k -> new AggregatedClientStream<>(serverStreamId, streamType, metadata));
    final var clientStream =
        new ClientStream<>(streamId, serverStream, streamType, metadata, clientStreamConsumer);
    serverStream.addClient(clientStream);
    clientStreams.put(streamId, clientStream);
    return serverStream;
  }

  /**
   * @return aggregated stream if it can be removed
   */
  Optional<AggregatedClientStream<M>> removeClient(final UUID streamId) {
    final var clientStream = clientStreams.remove(streamId);
    if (clientStream != null) {
      final var serverStream = clientStream.getServerStream();
      serverStream.removeClient(streamId);
      if (serverStream.isEmpty()) {
        serverStreams.remove(serverStream.getStreamId());
        serverStreamIds.remove(
            new Tuple<>(serverStream.getStreamType(), serverStream.getMetadata()));
        return Optional.of(serverStream);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting(
      "To inspect the registry state to see if the client is added or removed as expected")
  Optional<ClientStream<M>> getClient(final UUID clientStreamId) {
    return Optional.ofNullable(clientStreams.get(clientStreamId));
  }
}
