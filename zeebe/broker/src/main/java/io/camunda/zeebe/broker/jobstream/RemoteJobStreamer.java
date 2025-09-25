/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.engine.common.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

public final class RemoteJobStreamer implements JobStreamer {
  private static final String JOBS_AVAILABLE_TOPIC = "jobsAvailable";

  private final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate;
  private final ClusterEventService eventService;

  public RemoteJobStreamer(
      final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate,
      final ClusterEventService eventService) {
    this.delegate = delegate;
    this.eventService = eventService;
  }

  @Override
  public void notifyWorkAvailable(final String jobType) {
    eventService.broadcast(JOBS_AVAILABLE_TOPIC, jobType);
  }

  @Override
  public Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> filter) {
    return delegate.streamFor(jobType, filter).map(RemoteJobStream::new);
  }
}
