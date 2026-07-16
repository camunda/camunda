/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  /** Tracks which physicalTenantId topics already have a RESTART_STREAMS listener registered. */
  private final Set<String> registeredRestartPhysicalTenants = new HashSet<>();

  /**
   * Caches one {@link ClientStreamMetrics} instance per physical tenant, shared by the registry and
   * the manager so each tenant's meters are only registered once.
   */
  private final Map<String, ClientStreamMetrics> metricsByPhysicalTenantId = new HashMap<>();

  public ClientStreamServiceImpl(
      final ClusterCommunicationService communicationService,
      final Function<String, ClientStreamMetrics> metricsFactory) {
    this.communicationService = communicationService;
    final Function<String, ClientStreamMetrics> cachedMetricsFactory =
        physicalTenantId ->
            metricsByPhysicalTenantId.computeIfAbsent(physicalTenantId, metricsFactory);
    registry = new ClientStreamRegistry<>(cachedMetricsFactory);

    // ClientStreamRequestManager must use same actor as this because it is mutating shared
    // ClientStream objects.
    clientStreamManager =
        new ClientStreamManager<>(
            registry,
            new ClientStreamRequestManager<>(communicationService, actor),
            cachedMetricsFactory);
    apiHandler = new ClientStreamApiHandler(clientStreamManager, actor);
  }

  @Override
  protected void onActorStarted() {
    communicationService.replyToAsync(
        StreamTopics.PUSH.topic(DEFAULT_PHYSICAL_TENANT_ID),
        MessageUtil::parsePushRequest,
        apiHandler::handlePushRequest,
        BufferUtil::bufferAsArray,
        actor::run);

    // Pre-register the default group's RESTART topic so restarted legacy brokers are handled
    // even before the first brokerAdded callback fires.
    registerRestartHandler(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Override
  protected void onActorCloseRequested() {
    clientStreamManager.close();
  }

  @Override
  public ActorFuture<ClientStreamId> add(
      final DirectBuffer streamType,
      final M metadata,
      final ClientStreamConsumer clientStreamConsumer,
      final String physicalTenantId) {
    return actor.call(
        () ->
            clientStreamManager.add(streamType, metadata, clientStreamConsumer, physicalTenantId));
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
  public void onServerJoined(final MemberId memberId, final String physicalTenantId) {
    actor.run(
        () -> {
          registerRestartHandler(physicalTenantId);
          clientStreamManager.onServerJoined(memberId, physicalTenantId);
        });
  }

  @Override
  public void onServerRemoved(final MemberId memberId, final String physicalTenantId) {
    actor.run(() -> clientStreamManager.onServerRemoved(memberId, physicalTenantId));
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

  private void registerRestartHandler(final String physicalTenantId) {
    if (registeredRestartPhysicalTenants.add(physicalTenantId)) {
      registerRestartTopicHandler(
          StreamTopics.RESTART_STREAMS.topic(physicalTenantId), physicalTenantId);

      if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
        // Rolling-upgrade compat; remove alongside the legacy topic in 8.11.
        registerRestartTopicHandler(StreamTopics.RESTART_STREAMS.dualTopic(), physicalTenantId);
      }
    }
  }

  private void registerRestartTopicHandler(final String topic, final String physicalTenantId) {
    communicationService.replyTo(
        topic,
        Function.identity(),
        (sender, payload) -> apiHandler.handleRestartRequest(sender, physicalTenantId, payload),
        Function.identity(),
        actor::run);
  }
}
