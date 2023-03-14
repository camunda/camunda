/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.RemoteStreamServer;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.Supplier;

public class RemoteStreamServiceImpl<M extends BufferReader, P extends BufferWriter>
    implements RemoteStreamService<M, P> {

  private final ClusterEventService eventService;
  private final ClusterCommunicationService transport;
  private final StreamRegistry<M> registry;
  private final Supplier<M> metadataFactory;

  private final ActorSchedulingService actorSchedulingService;

  public RemoteStreamServiceImpl(
      final ClusterEventService eventService,
      final ClusterCommunicationService transport,
      final StreamRegistry<M> registry,
      final Supplier<M> metadatFactory,
      final ActorSchedulingService actorSchedulingService) {
    this.eventService = eventService;
    this.transport = transport;
    this.registry = registry;
    metadataFactory = metadatFactory;
    this.actorSchedulingService = actorSchedulingService;
  }

  @Override
  public ActorFuture<RemoteStreamServer<M, P>> start() {
    final CompletableActorFuture<RemoteStreamServer<M, P>> future = new CompletableActorFuture<>();
    final JobGatewayStreamer<M, P> streamer =
        new JobGatewayStreamer<>(eventService, transport, registry);
    actorSchedulingService
        .submitActor(streamer)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
                return;
              }
              final var apiHandler = new StreamApiHandler<>(registry, metadataFactory);
              final var apiServer = new JobStreamApiServer<>(transport, apiHandler);
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
}
