/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.atomix.cluster.messaging.ClusterEventService;
import io.camunda.zeebe.gateway.Loggers;

public final class LongPollingJobNotification {
  private static final String TOPIC = "jobsAvailable";
  private final ClusterEventService eventService;

  public LongPollingJobNotification(final ClusterEventService eventService) {
    this.eventService = eventService;
  }

  public void onJobsAvailable(final String jobType) {
    Loggers.LONG_POLLING.trace("Sending notification for {}", jobType);
    eventService.broadcast(TOPIC, jobType);
  }
}
