/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler;
import io.camunda.zeebe.stream.api.GatewayStreamer.GatewayStream;
import io.camunda.zeebe.stream.api.GatewayStreamer.Metadata;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.DirectBuffer;

public final class StreamRegistry<M extends Metadata, P extends BufferWriter> {

  private final ConcurrentMap<DirectBuffer, List<JobStream<M>>> streams = new ConcurrentHashMap<>();

  public void add(
      final DirectBuffer streamType,
      final long streamId,
      final MemberId recipient,
      final M metadata) {
    // Should be concurrent-safe as well
    streams.putIfAbsent(streamType, new CopyOnWriteArrayList<>());
    // TODO: check for duplicate streams from same gateway
    streams.get(streamType).add(new JobStream<>(streamId, recipient, metadata));
  }

  public Optional<GatewayStream<M, P>> get(final DirectBuffer streamType) {
    // Eg:- Random select stream
    final var streamOfType = streams.get(streamType);
    final var stream = streamOfType.get(ThreadLocalRandom.current().nextInt(streamOfType.size()));
    final var gatewayStream = new GatewayStreamImpl(stream.metadata(), stream.recipient());

    return Optional.of(gatewayStream);
  }

  record JobStream<M extends Metadata>(long streamId, MemberId recipient, M metadata) {}

  class GatewayStreamImpl implements GatewayStream<M, P> {

    private final M metadata;
    private final MemberId gateway;

    GatewayStreamImpl(final M metadata, final MemberId gateway) {
      this.metadata = metadata;
      this.gateway = gateway;
    }

    @Override
    public M metadata() {
      return metadata;
    }

    @Override
    public void push(final P payload, final ErrorHandler<P> errorHandler) {
      // send payload to gateway using communicationService
    }
  }
}
