/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;

/**
 * A thin adapter around a {@link ClientStreamService} instance specifically for streaming jobs.
 * It's intended to be the main entry point when setting up the client side of job streaming in the
 * gateway.
 */
public final class JobStreamClientImpl implements JobStreamClient {
  private final ActorSchedulingService schedulingService;
  private final ClientStreamService<JobActivationProperties> streamService;

  public JobStreamClientImpl(
      final ActorSchedulingService schedulingService,
      final ClusterCommunicationService clusterCommunicationService,
      final MeterRegistry meterRegistry) {
    this.schedulingService = schedulingService;
    streamService =
        new TransportFactory(schedulingService)
            .createRemoteStreamClient(
                clusterCommunicationService, new JobClientStreamMetrics(meterRegistry));
  }

  @Override
  public void brokerAdded(final BrokerMemberId memberId, final String physicalTenantId) {
    streamService.onServerJoined(memberId.memberId(), physicalTenantId);
  }

  @Override
  public void brokerRemoved(final BrokerMemberId memberId, final String physicalTenantId) {
    streamService.onServerRemoved(memberId.memberId());
  }

  @Override
  public ClientStreamer<JobActivationProperties> streamer() {
    return streamService.streamer();
  }

  @Override
  public ActorFuture<Void> start() {
    return streamService.start(schedulingService);
  }

  @Override
  public ActorFuture<Collection<ClientStream<JobActivationProperties>>> list() {
    return streamService.streams();
  }

  @Override
  public void close() {
    streamService.closeAsync().join();
  }
}
