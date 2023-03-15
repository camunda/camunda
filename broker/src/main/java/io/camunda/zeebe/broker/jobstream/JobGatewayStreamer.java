/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.protocol.impl.stream.JobStreamTopics;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class JobGatewayStreamer
    implements GatewayStreamer<JobActivationProperties, ActivatedJob> {

  private final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate;
  private final ClusterEventService eventService;

  public JobGatewayStreamer(
      final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate,
      final ClusterEventService eventService) {
    this.delegate = delegate;
    this.eventService = eventService;
  }

  @Override
  public void notifyWorkAvailable(final String streamType) {
    eventService.broadcast(JobStreamTopics.JOB_AVAILABLE.topic(), streamType);
  }

  @Override
  public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
      final DirectBuffer streamId) {
    return delegate.streamFor(streamId).map(JobGatewayStream::new);
  }
}
