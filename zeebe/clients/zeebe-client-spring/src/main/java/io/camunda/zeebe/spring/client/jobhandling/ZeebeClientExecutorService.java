/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.jobhandling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wrapper bean for {@link ScheduledExecutorService} required in Spring Zeebe for Job Handling,
 * Retry Management and so on.
 *
 * <p>This is wrapped so you can have multiple executor services in the Spring context and qualify
 * the right one.
 */
public class ZeebeClientExecutorService {

  private final ScheduledExecutorService scheduledExecutorService;

  public ZeebeClientExecutorService(final ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public ScheduledExecutorService get() {
    return scheduledExecutorService;
  }

  public static ZeebeClientExecutorService createDefault() {
    return createDefault(1);
  }

  public static ZeebeClientExecutorService createDefault(final int threads) {
    final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(threads);
    return new ZeebeClientExecutorService(threadPool);
  }
}
