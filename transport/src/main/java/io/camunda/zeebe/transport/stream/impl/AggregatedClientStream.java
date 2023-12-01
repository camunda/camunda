/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents a stream which aggregates multiple logically equivalent client streams. * */
final class AggregatedClientStream<M extends BufferWriter> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedClientStream.class);
  private final UUID streamId;
  private final LogicalId<M> logicalId;
  private final Set<MemberId> liveConnections = new HashSet<>();
  private final ClientStreamMetrics metrics;
  private final Int2ObjectHashMap<ClientStreamImpl<M>> clientStreams = new Int2ObjectHashMap<>();

  private boolean isOpened;
  private boolean isReady;
  private int nextLocalId;

  AggregatedClientStream(final UUID streamId, final LogicalId<M> logicalId) {
    this(streamId, logicalId, ClientStreamMetrics.noop());
  }

  AggregatedClientStream(
      final UUID streamId, final LogicalId<M> logicalId, final ClientStreamMetrics metrics) {
    this.streamId = streamId;
    this.logicalId = logicalId;
    this.metrics = metrics;
  }

  void addClient(final ClientStreamImpl<M> clientStream) {
    clientStreams.put(clientStream.streamId().localId(), clientStream);
    metrics.observeAggregatedClientCount(clientStreams.size());
    isReady |= clientStream.isBlocked();
  }

  UUID getStreamId() {
    return streamId;
  }

  Collection<ClientStreamImpl<M>> list() {
    return clientStreams.values();
  }

  int nextLocalId() {
    final var localId = nextLocalId;
    nextLocalId++;
    return localId;
  }

  /**
   * Mark that this stream is registered with the given server. Server can send data to this stream
   * from now on.
   *
   * @param serverId id of the server
   */
  void add(final MemberId serverId) {
    liveConnections.add(serverId);
  }

  /**
   * If true, the stream is registered with the given server. If false, it is also possible the
   * stream is registered with the server, but we failed to receive the acknowledgement.
   *
   * @param serverId id of the server
   * @return true if a server has acknowledged to add stream request
   */
  boolean isConnected(final MemberId serverId) {
    return liveConnections.contains(serverId);
  }

  /**
   * Mark that stream to this server is closed.
   *
   * @param serverId id of the server
   */
  void remove(final MemberId serverId) {
    liveConnections.remove(serverId);
  }

  void close() {
    isOpened = false;
  }

  void removeClient(final ClientStreamIdImpl streamId) {
    clientStreams.remove(streamId.localId());
    metrics.observeAggregatedClientCount(clientStreams.size());
  }

  /** returns true if there are no client streams for this stream * */
  boolean isEmpty() {
    return clientStreams.isEmpty();
  }

  LogicalId<M> logicalId() {
    return logicalId;
  }

  Set<MemberId> liveConnections() {
    return liveConnections;
  }

  void push(final DirectBuffer buffer, final ActorFuture<Void> future) {
    if (isBlocked()) {
      future.completeExceptionally(
          new ClientStreamBlockedException(
              "Cannot forward remote payload as aggregated stream %s is blocked"
                  .formatted(streamId)));
    }

    final var streams = clientStreams.values();
    if (streams.isEmpty()) {
      throw new NoSuchStreamException(
          "Cannot forward remote payload as there is no known client streams for aggregated stream %s"
              .formatted(logicalId));
    }

    final var targets = new ArrayList<>(streams);
    final var index = ThreadLocalRandom.current().nextInt(streams.size());

    tryPush(targets, index, 1, buffer, future);
  }

  private void tryPush(
      final ArrayList<ClientStreamImpl<M>> targets,
      final int index,
      final int currentCount,
      final DirectBuffer buffer,
      final ActorFuture<Void> future) {
    // Try with clients in a round-robin starting from the randomly picked startIndex
    final var clientStream = targets.get(index);

    LOGGER.trace("Pushing data from stream [{}] to client [{}]", streamId, clientStream.streamId());
    clientStream
        .push(buffer)
        .onComplete(
            (ok, pushFailed) -> {
              if (pushFailed == null) {
                future.complete(null);
              } else {
                if (currentCount >= targets.size()) {
                  final StreamExhaustedException error =
                      new StreamExhaustedException(
                          "Failed to push data to all available clients. No more clients left to retry.");
                  error.addSuppressed(pushFailed);
                  future.completeExceptionally(error);
                } else {
                  LOGGER.warn(
                      "Failed to push data to client [{}], retrying with next client.",
                      clientStream.streamId(),
                      pushFailed);
                  tryPush(targets, (index + 1) % targets.size(), currentCount + 1, buffer, future);
                }
              }
            });
  }

  void open(final ClientStreamRequestManager<M> requestManager, final Set<MemberId> servers) {
    if (isOpened) {
      return;
    }

    requestManager.add(this, servers);
    isOpened = true;
  }

  @Override
  public String toString() {
    return "AggregatedClientStream{"
        + "streamId="
        + streamId
        + ", logicalId="
        + logicalId
        + ", liveConnections="
        + liveConnections
        + ", clientStreams="
        + clientStreams.size()
        + ", isOpened="
        + isOpened
        + ", nextLocalId="
        + nextLocalId
        + '}';
  }

  public boolean isBlocked() {
    return clientStreams.values().stream()
        .map(ClientStreamImpl::isBlocked)
        .reduce((a, b) -> a && b)
        .orElse(false);
  }
}
