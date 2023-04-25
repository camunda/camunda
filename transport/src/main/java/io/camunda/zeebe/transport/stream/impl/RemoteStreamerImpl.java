/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * {@link RemoteStreamImpl} is an actor, and any payload pushed will be pushed asynchronously.
 *
 * <p>NOTE: any payload pushed is sent via the stream from {@link #streamFor(DirectBuffer)} will be
 * asynchronous, so the payload should be immutable, and the errors reported to the given {@link
 * io.camunda.zeebe.transport.stream.api.RemoteStream.ErrorHandler} may be reported on different
 * threads.
 */
public final class RemoteStreamerImpl<M extends BufferReader, P extends BufferWriter> extends Actor
    implements RemoteStreamer<M, P> {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final RemoteStreamPicker<M> streamPicker = new RandomStreamPicker<>();
  private final ClusterCommunicationService transport;
  private final ImmutableStreamRegistry<M> registry;
  private final RemoteStreamMetrics metrics;
  private final RemoteStreamPusher<P> remoteStreamPusher;

  public RemoteStreamerImpl(
      final ClusterCommunicationService transport,
      final ImmutableStreamRegistry<M> registry,
      final RemoteStreamMetrics metrics) {
    this.transport = Objects.requireNonNull(transport, "must specify a network transport");
    this.registry = Objects.requireNonNull(registry, "must specify a job stream registry");
    this.metrics = metrics;
    remoteStreamPusher = new RemoteStreamPusher<>(this::send, actor::run, metrics);
  }

  @Override
  public Optional<RemoteStream<M, P>> streamFor(final DirectBuffer streamType) {
    final UnsafeBuffer streamTypeBuffer = new UnsafeBuffer(streamType);
    final var consumers = registry.get(streamTypeBuffer);
    if (consumers.isEmpty()) {
      return Optional.empty();
    }

    final var target = streamPicker.pickStream(consumers);

    final var targetSet = registry.getStreamsByLogicalId(streamTypeBuffer, target.properties());

    final RemoteStreamImpl<M, P> gatewayStream =
        new RemoteStreamImpl<>(target, targetSet, target.properties(), remoteStreamPusher);
    return Optional.of(gatewayStream);
  }

  private CompletableFuture<Void> send(final PushStreamRequest request, final MemberId receiver) {
    return transport
        .send(
            StreamTopics.PUSH.topic(),
            request,
            BufferUtil::bufferAsArray,
            Function.identity(),
            receiver,
            REQUEST_TIMEOUT)
        .thenApply(ok -> null);
  }
}
