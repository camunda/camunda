/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.broker.jobstream.StreamRegistry;
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
  private final StreamRegistry registry;

  public LongPollingJobNotification(
      final ClusterEventService eventService, final StreamRegistry registry) {
    this.eventService = eventService;
    this.registry = registry;
  }

  @Override
  public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
      final DirectBuffer streamId) {
    final GatewayStream<JobActivationProperties, ActivatedJob> stream = registry.get(streamId);
    if (stream != null) {
      return Optional.of(stream);
    }

    onJobsAvailable(BufferUtil.bufferAsString(streamId));
    return Optional.empty();
  }

  private void onJobsAvailable(final String jobType) {
    eventService.broadcast(TOPIC, jobType);
  }
}
