/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.agrona.collections.Int2ObjectHashMap;

/** Represents a stream which aggregates multiple logically equivalent client streams. * */
final class AggregatedClientStream<M extends BufferWriter> {

  private final UUID streamId;
  private final LogicalId<M> logicalId;
  private final Set<MemberId> liveConnections = new HashSet<>();
  private final ClientStreamMetrics metrics;
  private final Int2ObjectHashMap<ClientStreamImpl<M>> clientStreams = new Int2ObjectHashMap<>();

  private boolean isOpened;
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
  }

  UUID streamId() {
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

  Int2ObjectHashMap<ClientStreamImpl<M>> clientStreams() {
    return clientStreams;
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
}
