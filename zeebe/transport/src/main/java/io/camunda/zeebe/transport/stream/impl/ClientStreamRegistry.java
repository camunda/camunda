/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** A registry to keeps tracks of all open streams. */
final class ClientStreamRegistry<M extends BufferWriter> {
  private final Map<ClientStreamId, ClientStreamImpl<M>> clientStreams = new HashMap<>();
  private final Map<UUID, AggregatedClientStream<M>> serverStreams = new HashMap<>();
  private final Map<ServerStreamKey<M>, UUID> serverStreamIds = new HashMap<>();

  private final Function<String, ClientStreamMetrics> metricsFactory;

  ClientStreamRegistry() {
    this(physicalTenantId -> ClientStreamMetrics.noop());
  }

  ClientStreamRegistry(final ClientStreamMetrics metrics) {
    this(physicalTenantId -> metrics);
    Objects.requireNonNull(metrics, "must specify metrics");
  }

  ClientStreamRegistry(final Function<String, ClientStreamMetrics> metricsFactory) {
    this.metricsFactory = Objects.requireNonNull(metricsFactory, "must specify metrics factory");
  }

  Optional<AggregatedClientStream<M>> get(final UUID serverStreamId) {
    return Optional.ofNullable(serverStreams.get(serverStreamId));
  }

  Collection<AggregatedClientStream<M>> list() {
    return serverStreams.values();
  }

  ClientStreamImpl<M> addClient(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer,
      final String physicalTenantId) {
    final var streamTypeBuffer = new UnsafeBuffer(streamType);
    final LogicalId<M> logicalId = new LogicalId<>(streamTypeBuffer, metadata);
    final var streamKey = new ServerStreamKey<>(physicalTenantId, logicalId);
    final var metrics = metricsFactory.apply(physicalTenantId);
    // Keyed by physicalTenantId too, so identical (streamType, metadata) pairs from different
    // tenants don't aggregate into the same stream.
    final var serverStreamId = serverStreamIds.computeIfAbsent(streamKey, k -> UUID.randomUUID());
    final var serverStream =
        serverStreams.computeIfAbsent(
            serverStreamId,
            k ->
                new AggregatedClientStream<>(serverStreamId, logicalId, physicalTenantId, metrics));
    final var streamId = new ClientStreamIdImpl(serverStreamId, serverStream.nextLocalId());
    final var clientStream =
        new ClientStreamImpl<>(
            streamId, serverStream, streamTypeBuffer, metadata, clientStreamConsumer);
    serverStream.addClient(clientStream);
    clientStreams.put(streamId, clientStream);

    metrics.aggregatedStreamCount(countAggregatedStreamsFor(physicalTenantId));
    metrics.clientCount(countClientsFor(physicalTenantId));
    return clientStream;
  }

  /**
   * @return aggregated stream if it can be removed
   */
  Optional<AggregatedClientStream<M>> removeClient(final ClientStreamId streamId) {
    final var clientStream = clientStreams.remove(streamId);
    if (clientStream != null) {
      final var serverStream = clientStream.serverStream();
      final var physicalTenantId = serverStream.physicalTenantId();
      final var metrics = metricsFactory.apply(physicalTenantId);
      serverStream.removeClient(clientStream.streamId());
      metrics.clientCount(countClientsFor(physicalTenantId));

      if (serverStream.isEmpty()) {
        serverStreams.remove(serverStream.streamId());
        serverStreamIds.remove(new ServerStreamKey<>(physicalTenantId, serverStream.logicalId()));
        metrics.aggregatedStreamCount(countAggregatedStreamsFor(physicalTenantId));

        return Optional.of(serverStream);
      }
    }

    return Optional.empty();
  }

  void clear() {
    final var physicalTenantIds =
        serverStreams.values().stream()
            .map(AggregatedClientStream::physicalTenantId)
            .distinct()
            .toList();

    clientStreams.clear();
    serverStreams.clear();
    serverStreamIds.clear();

    physicalTenantIds.forEach(
        physicalTenantId -> {
          final var metrics = metricsFactory.apply(physicalTenantId);
          metrics.clientCount(0);
          metrics.aggregatedStreamCount(0);
        });
  }

  Optional<ClientStreamImpl<M>> getClient(final ClientStreamId clientStreamId) {
    return Optional.ofNullable(clientStreams.get(clientStreamId));
  }

  private int countAggregatedStreamsFor(final String physicalTenantId) {
    return (int)
        serverStreams.values().stream()
            .filter(s -> physicalTenantId.equals(s.physicalTenantId()))
            .count();
  }

  private int countClientsFor(final String physicalTenantId) {
    return (int)
        clientStreams.values().stream()
            .filter(c -> physicalTenantId.equals(c.serverStream().physicalTenantId()))
            .count();
  }

  /**
   * Identifies a server stream by physical tenant in addition to its logical id, so identical
   * (streamType, metadata) pairs from different tenants never aggregate into the same stream.
   */
  private record ServerStreamKey<T>(String physicalTenantId, LogicalId<T> logicalId) {}
}
