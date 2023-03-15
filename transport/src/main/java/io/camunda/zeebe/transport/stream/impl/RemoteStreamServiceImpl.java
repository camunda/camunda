/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.Supplier;

public class RemoteStreamServiceImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStreamService<M, P> {
  private final ClusterCommunicationService transport;
  private final RemoteStreamRegistry<M> registry;
  private final Supplier<M> metadataFactory;

  private final ActorSchedulingService actorSchedulingService;
  private RemoteStreamerImpl<M, P> streamer;
  private RemoteStreamApiServer<M> apiServer;

  public RemoteStreamServiceImpl(
      final ClusterCommunicationService transport,
      final RemoteStreamRegistry<M> registry,
      final Supplier<M> metadatFactory,
      final ActorSchedulingService actorSchedulingService) {
    this.transport = transport;
    this.registry = registry;
    metadataFactory = metadatFactory;
    this.actorSchedulingService = actorSchedulingService;
  }

  @Override
  public ActorFuture<RemoteStreamer<M, P>> start() {
    final CompletableActorFuture<RemoteStreamer<M, P>> future = new CompletableActorFuture<>();
    streamer = new RemoteStreamerImpl<>(transport, registry);
    final var apiHandler = new RemoteStreamApiHandler<>(registry, metadataFactory);
    apiServer = new RemoteStreamApiServer<>(transport, apiHandler);
    actorSchedulingService
        .submitActor(streamer)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
                return;
              }

              actorSchedulingService
                  .submitActor(apiServer)
                  .onComplete(
                      (ignore, serverStartError) -> {
                        if (serverStartError != null) {
                          future.completeExceptionally(serverStartError);
                        } else {
                          future.complete(streamer);
                        }
                      });
            });

    return future;
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    final ActorFuture<Void> closed = new CompletableActorFuture<>();
    if (apiServer != null) {
      apiServer
          .closeAsync()
          .onComplete(
              (ok, error) -> {
                if (error != null) {
                  closed.completeExceptionally(error);
                } else {
                  if (streamer != null) {
                    streamer.closeAsync().onComplete(closed);
                  }
                }
              });
    } else {
      closed.complete(null);
    }
    return closed;
  }
}
