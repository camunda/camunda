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
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobGatewayStreamer extends Actor
    implements GatewayStreamer<JobActivationProperties, ActivatedJob> {

  private final ClusterEventService eventService;
  private final ImmutableStreamRegistry<JobActivationProperties> registry;

  public JobGatewayStreamer(
      final ClusterEventService eventService,
      final ImmutableStreamRegistry<JobActivationProperties> registry) {
    this.eventService =
        Objects.requireNonNull(eventService, "must specify an event service for broadcasting");
    this.registry = Objects.requireNonNull(registry, "must specify a job stream registry");
  }

  @Override
  public Optional<GatewayStream<JobActivationProperties, ActivatedJob>> streamFor(
      final DirectBuffer streamId) {
    final var consumers = registry.get(new UnsafeBuffer(streamId));
    if (consumers == null || consumers.isEmpty()) {
      final var jobType = BufferUtil.bufferAsString(streamId);
      actor.run(() -> eventService.broadcast(JobStreamTopics.JOB_AVAILABLE.topic(), jobType));
    }

    // TODO: for now keep returning empty, but fill out later
    return Optional.empty();
  }
}
