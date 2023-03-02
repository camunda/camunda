/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class LongPollingJobNotification
    implements GatewayStreamer<JobActivationProperties, ActivatedJob> {
  private static final String TOPIC = "jobsAvailable";
  private final ClusterEventService eventService;

  public LongPollingJobNotification(final ClusterEventService eventService) {
    this.eventService = eventService;
  }

  @Override
  public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
      final DirectBuffer streamId) {
    onJobsAvailable(BufferUtil.bufferAsString(streamId));

    return Optional.empty();
  }

  private void onJobsAvailable(final String jobType) {
    eventService.broadcast(TOPIC, jobType);
  }
}
