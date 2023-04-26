/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;

/**
 * A thin adapter around a {@link ClientStreamService} instance specifically for streaming jobs.
 * It's intended to be the main entry point when setting up the client side of job streaming in the
 * gateway.
 *
 * <p>NOTE: most methods are synchronized to avoid dealing with concurrency issues with
 * startup/shutdown, and that's not on any hot path. Can be reconsidered if this is not true
 * anymore, or not the only constraint.
 */
public final class JobStreamClientImpl implements JobStreamClient {
  private final ActorSchedulingService schedulingService;
  private final ClientStreamService<JobActivationProperties> streamService;

  private boolean started;
  private ActorFuture<Void> startedFuture;

  public JobStreamClientImpl(
      final ActorSchedulingService schedulingService,
      final ClusterCommunicationService clusterCommunicationService) {
    this.schedulingService = schedulingService;
    this.streamService =
        new TransportFactory(schedulingService)
            .createRemoteStreamClient(clusterCommunicationService, new JobClientStreamMetrics());
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

  @Override
  public ClientStreamer<JobActivationProperties> streamer() {
    return streamService.streamer();
  }

  @Override
  public synchronized ActorFuture<Void> start() {
    if (startedFuture == null) {
      startedFuture = streamService.start(schedulingService);
      started = true;
    }

    return startedFuture;
  }

  @Override
  public synchronized void close() {
    if (!started) {
      return;
    }

    started = false;
    streamService.closeAsync().join();
  }
}
