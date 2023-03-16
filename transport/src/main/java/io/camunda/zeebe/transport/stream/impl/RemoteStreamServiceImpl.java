/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.stream.Stream;

public class RemoteStreamServiceImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStreamService<M, P> {
  private final RemoteStreamerImpl<M, P> streamer;
  private final RemoteStreamApiServer<M> apiServer;

  public RemoteStreamServiceImpl(
      final RemoteStreamerImpl<M, P> streamer, final RemoteStreamApiServer<M> apiServer) {
    this.streamer = streamer;
    this.apiServer = apiServer;
  }

  @Override
  public ActorFuture<RemoteStreamer<M, P>> start(
      final ActorSchedulingService actorSchedulingService, final ConcurrencyControl executor) {
    final CompletableActorFuture<RemoteStreamer<M, P>> future = new CompletableActorFuture<>();
    final var streamerStarted = actorSchedulingService.submitActor(streamer);
    final var serverStarted = actorSchedulingService.submitActor(apiServer);
    final var combined =
        Stream.of(streamerStarted, serverStarted).collect(new ActorFutureCollector<>(executor));
    executor.runOnCompletion(
        combined,
        (ok, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          } else {
            future.complete(streamer);
          }
        });
    return future;
  }

  @Override
  public ActorFuture<Void> closeAsync(final ConcurrencyControl executor) {
    final CompletableActorFuture<Void> closed = new CompletableActorFuture<>();
    final var streamerClosed = streamer.closeAsync();
    final var serverClosed = apiServer.closeAsync();
    final var combined =
        Stream.of(streamerClosed, serverClosed).collect(new ActorFutureCollector<>(executor));
    executor.runOnCompletion(
        combined,
        (ok, error) -> {
          if (error != null) {
            closed.completeExceptionally(error);
          } else {
            closed.complete(null);
          }
        });
    return closed;
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_REMOVED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    apiServer.removeAll(event.subject().id());
  }
}
