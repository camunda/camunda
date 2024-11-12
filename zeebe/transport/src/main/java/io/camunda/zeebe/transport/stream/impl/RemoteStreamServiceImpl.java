/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

public class RemoteStreamServiceImpl<M, P extends BufferWriter>
    implements RemoteStreamService<M, P> {
  private final RemoteStreamerImpl<M, P> streamer;
  private final RemoteStreamTransport<M> apiServer;
  private final RemoteStreamRegistry<M> registry;

  public RemoteStreamServiceImpl(
      final RemoteStreamerImpl<M, P> streamer,
      final RemoteStreamTransport<M> apiServer,
      final RemoteStreamRegistry<M> registry) {
    this.streamer = streamer;
    this.apiServer = apiServer;
    this.registry = registry;
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
  public Collection<RemoteStreamInfo<M>> streams() {
    return new HashSet<>(registry.list());
  }

  @Override
  public boolean isRelevant(final ClusterMembershipEvent event) {
    return event.type() == Type.MEMBER_REMOVED || event.type() == Type.MEMBER_ADDED;
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    final var type = event.type();

    if (type == Type.MEMBER_REMOVED) {
      apiServer.removeAll(event.subject().id());
    } else if (type == Type.MEMBER_ADDED) {
      apiServer.restartStreams(event.subject().id());
    }
  }
}
