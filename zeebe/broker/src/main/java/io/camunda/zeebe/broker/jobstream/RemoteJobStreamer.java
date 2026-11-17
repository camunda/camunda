/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
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
  private final String physicalTenantId;

  public RemoteJobStreamer(
      final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate,
      final ClusterEventService eventService,
      final String physicalTenantId) {
    this.delegate = delegate;
    this.eventService = eventService;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public void notifyWorkAvailable(final String jobType) {
    eventService.broadcast(topic(physicalTenantId), jobType);
    legacyBroadcastIfDefaultTenant(jobType);
  }

  private static String topic(final String physicalTenantId) {
    return physicalTenantId + "-" + JOBS_AVAILABLE_TOPIC;
  }

  /** Rolling-upgrade compat; remove alongside the legacy topic in 8.11. */
  private void legacyBroadcastIfDefaultTenant(final String jobType) {
    if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      eventService.broadcast(JOBS_AVAILABLE_TOPIC, jobType);
    }
  }

  @Override
  public Optional<JobStream> streamFor(
      final DirectBuffer jobType, final Predicate<JobActivationProperties> filter) {
    return delegate.streamFor(jobType, filter).map(RemoteJobStream::new);
  }
}
