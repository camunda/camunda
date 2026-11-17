/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import org.junit.jupiter.api.Test;

final class RemoteJobStreamerTest {

  private static final String JOB_TYPE = "foo";

  private final ClusterEventService eventService = mock(ClusterEventService.class);
  private final RemoteStreamer<JobActivationProperties, ActivatedJob> delegate =
      mock(RemoteStreamer.class);

  @Test
  void shouldBroadcastOnlyOnTenantScopedTopicForNonDefaultTenant() {
    // given
    final var streamer = new RemoteJobStreamer(delegate, eventService, "tenanta");

    // when
    streamer.notifyWorkAvailable(JOB_TYPE);

    // then
    verify(eventService).broadcast(eq("tenanta-jobsAvailable"), eq(JOB_TYPE));
    verify(eventService, never()).broadcast(eq("jobsAvailable"), any());
  }

  @Test
  void shouldDualBroadcastOnLegacyAndScopedTopicForDefaultTenant() {
    // given
    final var streamer = new RemoteJobStreamer(delegate, eventService, "default");

    // when
    streamer.notifyWorkAvailable(JOB_TYPE);

    // then
    verify(eventService).broadcast(eq("default-jobsAvailable"), eq(JOB_TYPE));
    verify(eventService).broadcast(eq("jobsAvailable"), eq(JOB_TYPE));
  }
}
