/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.transport.impl.AtomixServerTransport.TopicSupplier;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.impl.ClientStreamServiceImpl;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamApiHandler;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamRegistry;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamServiceImpl;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamTransport;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamerImpl;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdGenerator;

public final class TransportFactory {

  private final ActorSchedulingService actorSchedulingService;

  public TransportFactory(final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
  }

  public ServerTransport createServerTransport(
      final MessagingService messagingService,
      final IdGenerator requestIdGenerator,
      final List<TopicSupplier> topicSuppliers) {

    final var atomixServerTransport =
        new AtomixServerTransport(messagingService, requestIdGenerator, topicSuppliers);
    actorSchedulingService.submitActor(atomixServerTransport);
    return atomixServerTransport;
  }

  public ClientTransport createClientTransport(final MessagingService messagingService) {
    return createClientTransport(messagingService, TopicSupplier.withLegacyTopicName());
  }

  public ClientTransport createClientTransport(
      final MessagingService messagingService, final TopicSupplier topicSupplier) {
    final var atomixClientTransportAdapter =
        new AtomixClientTransportAdapter(messagingService, topicSupplier);
    actorSchedulingService.submitActor(atomixClientTransportAdapter);
    return atomixClientTransportAdapter;
  }

  public <M, P extends BufferWriter> RemoteStreamService<M, P> createRemoteStreamServer(
      final ClusterCommunicationService clusterCommunicationService,
      final Function<DirectBuffer, M> metadataFactory,
      final RemoteStreamErrorHandler<P> errorHandler,
      final RemoteStreamMetrics metrics) {
    final RemoteStreamRegistry<M> registry = new RemoteStreamRegistry<>(metrics);
    return new RemoteStreamServiceImpl<>(
        new RemoteStreamerImpl<>(clusterCommunicationService, registry, errorHandler, metrics),
        new RemoteStreamTransport<>(
            clusterCommunicationService, new RemoteStreamApiHandler<>(registry, metadataFactory)),
        registry);
  }

  public <M extends BufferWriter> ClientStreamService<M> createRemoteStreamClient(
      final ClusterCommunicationService clusterCommunicationService,
      final ClientStreamMetrics metrics) {
    return new ClientStreamServiceImpl<>(clusterCommunicationService, metrics);
  }
}
