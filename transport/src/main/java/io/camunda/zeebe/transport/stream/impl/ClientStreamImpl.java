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
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

/** Represents a registered client stream. * */
final class ClientStreamImpl<M extends BufferWriter> implements ClientStream<M> {
  private final ClientStreamIdImpl streamId;
  private final AggregatedClientStream<M> serverStream;
  private final DirectBuffer streamType;
  private final M metadata;
  private final ClientStreamConsumer clientStreamConsumer;

  private boolean blocked;

  /** */
  ClientStreamImpl(
      final ClientStreamIdImpl streamId,
      final AggregatedClientStream<M> serverStream,
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    this.streamId = streamId;
    this.serverStream = serverStream;
    this.streamType = streamType;
    this.metadata = metadata;
    this.clientStreamConsumer = clientStreamConsumer;
  }

  ActorFuture<Void> push(final DirectBuffer payload) {
    try {
      return clientStreamConsumer.push(payload);
    } catch (final Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
  }

  public void block() {
    blocked = true;
  }

  public void unblock() {
    blocked = false;
  }

  @Override
  public ClientStreamIdImpl streamId() {
    return streamId;
  }

  @Override
  public DirectBuffer streamType() {
    return streamType;
  }

  @Override
  public M metadata() {
    return metadata;
  }

  @Override
  public Map<MemberId, String> liveConnections() {
    return serverStream().liveConnections().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().name()));
  }

  @Override
  public boolean isBlocked() {
    return blocked;
  }

  public AggregatedClientStream<M> serverStream() {
    return serverStream;
  }

  @Override
  public int hashCode() {
    return Objects.hash(streamId, serverStream, streamType, metadata, clientStreamConsumer);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    final var that = (ClientStreamImpl<M>) obj;
    return Objects.equals(streamId, that.streamId)
        && Objects.equals(serverStream, that.serverStream)
        && Objects.equals(streamType, that.streamType)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(clientStreamConsumer, that.clientStreamConsumer);
  }

  @Override
  public String toString() {
    return "ClientStreamImpl["
        + "streamId="
        + streamId
        + ", "
        + "serverStream="
        + serverStream
        + ", "
        + "streamType="
        + streamType
        + ", "
        + "metadata="
        + metadata
        + ", "
        + "clientStreamConsumer="
        + clientStreamConsumer
        + ']';
  }
}
