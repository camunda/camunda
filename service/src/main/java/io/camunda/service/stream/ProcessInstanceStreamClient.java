/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.stream;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.Collection;

public class ProcessInstanceStreamClient implements BrokerTopologyListener, CloseableSilently {

  private final ActorSchedulingService schedulingService;
  private final ClientStreamService<ProcessInstanceProperties> streamService;

  private boolean started;
  private ActorFuture<Void> startedFuture;

  public ProcessInstanceStreamClient(
      final ActorSchedulingService schedulingService,
      final ClusterCommunicationService clusterCommunicationService) {
    this.schedulingService = schedulingService;
    streamService =
        new TransportFactory(schedulingService)
            .createRemoteStreamClient(clusterCommunicationService, ClientStreamMetrics.noop());
  }

  @Override
  public synchronized void brokerAdded(final MemberId memberId) {
    if (!started) {
      return;
    }

    streamService.onServerJoined(memberId);
  }

  @Override
  public synchronized void brokerRemoved(final MemberId memberId) {
    if (!started) {
      return;
    }

    streamService.onServerRemoved(memberId);
  }

  /** Returns the underlying job streamer. */
  public ClientStreamer<ProcessInstanceProperties> streamer() {
    return streamService.streamer();
  }

  /** Asynchronously starts the job stream client. */
  public ActorFuture<Void> start() {
    if (startedFuture == null) {
      startedFuture = streamService.start(schedulingService);
      started = true;
    }

    return startedFuture;
  }

  /** Returns the list of registered job streams */
  public ActorFuture<Collection<ClientStream<ProcessInstanceProperties>>> list() {
    return streamService.streams();
  }

  @Override
  public void close() {
    if (!started) {
      return;
    }

    started = false;
    streamService.closeAsync().join();
  }
}
