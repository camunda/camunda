/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.agrona.DirectBuffer;

/** A registry to keeps tracks of all open streams. */
final class ClientStreamRegistry<M extends BufferWriter> {

  // This class is currently a very simple wrapper around a map. When we aggregate multiple streams
  // into one stream, we may have to keep track of them also here.
  private final Map<UUID, ClientStream<M>> clientStreams = new HashMap<>();
  private final Map<UUID, AggregatedClientStream<M>> serverStreams = new HashMap<>();

  Optional<AggregatedClientStream<M>> get(final UUID streamId) {
    return Optional.ofNullable(serverStreams.get(streamId));
  }

  Collection<AggregatedClientStream<M>> list() {
    return serverStreams.values();
  }

  public AggregatedClientStream<M> addClient(
      final UUID streamId,
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    final var serverStreamId = streamId; // TODO: generate aggregated stream id
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

  public Optional<AggregatedClientStream<M>> removeClient(final UUID streamId) {
    final var clientStream = clientStreams.remove(streamId);
    if (clientStream != null) {
      final var serverStream = clientStream.getServerStream();
      serverStream.removeClient(streamId);
      if (serverStream.isEmpty()) {
        serverStreams.remove(serverStream.getStreamId());
        return Optional.of(serverStream);
      }
    }
    return Optional.empty();
  }
}
