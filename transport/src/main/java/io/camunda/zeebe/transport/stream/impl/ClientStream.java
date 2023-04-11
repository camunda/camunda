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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.agrona.DirectBuffer;

/** Represents a registered client stream. * */
final class ClientStream<M extends BufferWriter> {
  private final UUID streamId;
  private final DirectBuffer streamType;
  private final M metadata;
  private final ClientStreamConsumer streamConsumer;
  private final Set<MemberId> liveConnections = new HashSet<>();

  private State state;

  ClientStream(
      final UUID streamId,
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    this.streamId = streamId;
    this.streamType = streamType;
    this.metadata = metadata;
    streamConsumer = clientStreamConsumer;
    state = State.OPEN;
  }

  UUID getStreamId() {
    return streamId;
  }

  DirectBuffer getStreamType() {
    return streamType;
  }

  M getMetadata() {
    return metadata;
  }

  ClientStreamConsumer getClientStreamConsumer() {
    return streamConsumer;
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
    state = State.CLOSED;
  }

  boolean isClosed() {
    return state == State.CLOSED;
  }

  private enum State {
    OPEN,
    CLOSED
  }
}
