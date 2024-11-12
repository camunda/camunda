/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.transport.stream.impl.messages.MessageUtil;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;

/**
 * Implementation for both {@link ClientStreamer} and {@link ClientStreamService}.
 *
 * <p>TODO: In the future we may want to split this into more than implementation, where the
 * streamer receives an execution context, and the service manages it.
 */
public final class ClientStreamServiceImpl<M extends BufferWriter> extends Actor
    implements ClientStreamer<M>, ClientStreamService<M> {
  private final ClientStreamManager<M> clientStreamManager;
  private final ClusterCommunicationService communicationService;
  private final ClientStreamRegistry<M> registry;
  private final ClientStreamApiHandler apiHandler;

  public ClientStreamServiceImpl(
      final ClusterCommunicationService communicationService, final ClientStreamMetrics metrics) {
    this.communicationService = communicationService;
    registry = new ClientStreamRegistry<>(metrics);

    // ClientStreamRequestManager must use same actor as this because it is mutating shared
    // ClientStream objects.
    clientStreamManager =
        new ClientStreamManager<>(
            registry, new ClientStreamRequestManager<>(communicationService, actor), metrics);
    apiHandler = new ClientStreamApiHandler(clientStreamManager, actor);
  }

  @Override
  protected void onActorStarted() {
    communicationService.replyToAsync(
        StreamTopics.PUSH.topic(),
        MessageUtil::parsePushRequest,
        apiHandler::handlePushRequest,
        BufferUtil::bufferAsArray,
        actor::run);

    communicationService.replyTo(
        StreamTopics.RESTART_STREAMS.topic(),
        Function.identity(),
        apiHandler::handleRestartRequest,
        Function.identity(),
        actor::run);
  }

  @Override
  protected void onActorCloseRequested() {
    clientStreamManager.close();
  }

  @Override
  public ActorFuture<ClientStreamId> add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer) {
    return actor.call(() -> clientStreamManager.add(streamType, metadata, clientStreamConsumer));
  }

  @Override
  public ActorFuture<Void> remove(final ClientStreamId streamId) {
    return actor.call(() -> clientStreamManager.remove(streamId));
  }

  @Override
  public ActorFuture<Void> start(final ActorSchedulingService schedulingService) {
    return schedulingService.submitActor(this);
  }

  @Override
  public void onServerJoined(final MemberId memberId) {
    actor.run(() -> clientStreamManager.onServerJoined(memberId));
  }

  @Override
  public void onServerRemoved(final MemberId memberId) {
    actor.run(() -> clientStreamManager.onServerRemoved(memberId));
  }

  @Override
  public ClientStreamer<M> streamer() {
    return this;
  }

  @Override
  public ActorFuture<Optional<ClientStream<M>>> streamFor(final ClientStreamId id) {
    // mapping to itself is necessary to cast from impl to interface type
    return actor.call(() -> registry.getClient(id).map(s -> s));
  }

  @Override
  public ActorFuture<Collection<ClientStream<M>>> streams() {
    return actor.call(
        () ->
            registry.list().stream()
                .flatMap(agg -> agg.list().stream())
                .map(s -> (ClientStream<M>) s)
                .toList());
  }
}
