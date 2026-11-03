/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.probe;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.engine.processing.streamprocessor.ProbeNotifier;

/**
 * Tells the gateways that a probe ID has probes to collect, as {@code RemoteJobStreamer} announces
 * job types. The topic is the physical tenant's and the probes' own, so a probe never wakes a job
 * poll.
 */
public final class RemoteProbeNotifier implements ProbeNotifier {

  private static final String PROBES_AVAILABLE_TOPIC = "probesAvailable";

  private final ClusterEventService eventService;
  private final String topic;

  public RemoteProbeNotifier(
      final ClusterEventService eventService, final String physicalTenantId) {
    this.eventService = eventService;
    topic = topic(physicalTenantId);
  }

  /** The topic the gateways listen on for the probes of the physical tenant. */
  public static String topic(final String physicalTenantId) {
    return physicalTenantId + "-" + PROBES_AVAILABLE_TOPIC;
  }

  @Override
  public void notifyProbesAvailable(final String probeId) {
    eventService.broadcast(topic, probeId);
  }
}
