/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.protocol.impl.stream.JobStreamTopics;
import io.camunda.zeebe.protocol.impl.stream.PushStreamRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
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
 * {@link JobGatewayStreamer} is a job specific implementation of {@link GatewayStreamer}, allowing
 * the engine to activate & push out jobs to an available stream.
 *
 * <p>It is itself an actor, and any jobs pushed will be pushed asynchronously.
 *
 * <p>NOTE: any payload pushed is sent via the stream from {@link #streamFor(DirectBuffer)} will be
 * asynchronous, so the payload should be immutable, and the errors reported to the given {@link
 * io.camunda.zeebe.stream.api.GatewayStreamer.ErrorHandler} may be reported on different threads.
 */
public final class JobGatewayStreamer extends Actor
    implements GatewayStreamer<JobActivationProperties, ActivatedJob> {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final StreamPicker<JobActivationProperties> streamPicker = new RandomStreamPicker<>();

  private final ClusterEventService eventService;
  private final ClusterCommunicationService transport;
  private final ImmutableStreamRegistry<JobActivationProperties> registry;

  public JobGatewayStreamer(
      final ClusterEventService eventService,
      final ClusterCommunicationService transport,
      final ImmutableStreamRegistry<JobActivationProperties> registry) {
    this.eventService =
        Objects.requireNonNull(eventService, "must specify an event service for broadcasting");
    this.transport = Objects.requireNonNull(transport, "must specify a network transport");
    this.registry = Objects.requireNonNull(registry, "must specify a job stream registry");
  }

  @Override
  public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
      final DirectBuffer streamId) {
    final var consumers = registry.get(new UnsafeBuffer(streamId));
    if (consumers.isEmpty()) {
      final var jobType = BufferUtil.bufferAsString(streamId);
      actor.run(() -> eventService.broadcast(JobStreamTopics.JOB_AVAILABLE.topic(), jobType));
      return Optional.empty();
    }

    final var target = streamPicker.pickStream(consumers);
    final var gatewayStream =
        new JobGatewayStream(
            target.properties(), new StreamPusher<>(target.id(), this::send, actor::run));
    return Optional.of(gatewayStream);
  }

  private CompletableFuture<Void> send(final PushStreamRequest request, final MemberId receiver) {
    return transport
        .send(
            JobStreamTopics.PUSH.topic(),
            request,
            this::serialize,
            Function.identity(),
            receiver,
            REQUEST_TIMEOUT)
        .thenApply(ok -> null);
  }

  private byte[] serialize(final BufferWriter payload) {
    final var bytes = new byte[payload.getLength()];
    final var writeBuffer = new UnsafeBuffer();
    writeBuffer.wrap(bytes);

    payload.write(writeBuffer, 0);
    return bytes;
  }

  private record JobGatewayStream(
      JobActivationProperties metadata, StreamPusher<ActivatedJob> streamer)
      implements GatewayStream<JobActivationProperties, ActivatedJob> {

    @Override
    public void push(
        final ActivatedJob activatedJob, final ErrorHandler<ActivatedJob> errorHandler) {
      streamer.pushAsync(activatedJob, errorHandler);
    }
  }
}
