/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** A registry to keeps tracks of all open streams. */
final class ClientStreamRegistry<M extends BufferWriter> {
  private final Map<ClientStreamId, ClientStream<M>> clientStreams = new HashMap<>();
  private final Map<UUID, AggregatedClientStream<M>> serverStreams = new HashMap<>();
  private final Map<LogicalId<M>, UUID> serverStreamIds = new HashMap<>();

  private final ClientStreamMetrics metrics;

  ClientStreamRegistry() {
    this(ClientStreamMetrics.noop());
  }

  ClientStreamRegistry(final ClientStreamMetrics metrics) {
    this.metrics = Objects.requireNonNull(metrics, "must specify metrics");
  }

  Optional<AggregatedClientStream<M>> get(final UUID serverStreamId) {
    return Optional.ofNullable(serverStreams.get(serverStreamId));
  }

  Collection<AggregatedClientStream<M>> list() {
    return serverStreams.values();
  }

  ClientStream<M> addClient(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    final var streamTypeBuffer = new UnsafeBuffer(streamType);
    final LogicalId<M> logicalId = new LogicalId<>(streamTypeBuffer, metadata);
    // Find serverStreamId given streamType and metadata. Once a server stream is removed, a new
    // server stream with same streamType and metadata will get a new UUID.
    final var serverStreamId = serverStreamIds.computeIfAbsent(logicalId, k -> UUID.randomUUID());
    final var serverStream =
        serverStreams.computeIfAbsent(
            serverStreamId, k -> new AggregatedClientStream<>(serverStreamId, logicalId));
    final var streamId = new ClientStreamIdImpl(serverStreamId, serverStream.nextLocalId());
    final var clientStream =
        new ClientStream<>(
            streamId, serverStream, streamTypeBuffer, metadata, clientStreamConsumer);
    serverStream.addClient(clientStream);
    clientStreams.put(streamId, clientStream);

    metrics.aggregatedStreamCount(serverStreams.size());
    metrics.clientCount(clientStreams.size());
    return clientStream;
  }

  /**
   * @return aggregated stream if it can be removed
   */
  Optional<AggregatedClientStream<M>> removeClient(final ClientStreamId streamId) {
    final var clientStream = clientStreams.remove(streamId);
    if (clientStream != null) {
      final var serverStream = clientStream.serverStream();
      serverStream.removeClient(clientStream.streamId());
      metrics.clientCount(clientStreams.size());

      if (serverStream.isEmpty()) {
        serverStreams.remove(serverStream.getStreamId());
        serverStreamIds.remove(serverStream.logicalId());
        metrics.aggregatedStreamCount(serverStreams.size());

        return Optional.of(serverStream);
      }
    }

    return Optional.empty();
  }

  void clear() {
    clientStreams.clear();
    serverStreams.clear();
    serverStreamIds.clear();

    metrics.clientCount(0);
    metrics.aggregatedStreamCount(0);
  }

  @VisibleForTesting(
      "To inspect the registry state to see if the client is added or removed as expected")
  Optional<ClientStream<M>> getClient(final ClientStreamId clientStreamId) {
    return Optional.ofNullable(clientStreams.get(clientStreamId));
  }
}
