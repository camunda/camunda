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
import java.util.UUID;
import org.agrona.DirectBuffer;

/** Represents a registered client stream. * */
final class ClientStream<M extends BufferWriter> {
  private final UUID streamId;
  private final AggregatedClientStream<M> serverStream;
  private final DirectBuffer streamType;
  private final M metadata;
  private final ClientStreamConsumer streamConsumer;

  ClientStream(
      final UUID streamId,
      final AggregatedClientStream<M> serverStream,
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    this.streamId = streamId;
    this.serverStream = serverStream;
    this.streamType = streamType;
    this.metadata = metadata;
    streamConsumer = clientStreamConsumer;
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

  AggregatedClientStream<M> getServerStream() {
    return serverStream;
  }
}
