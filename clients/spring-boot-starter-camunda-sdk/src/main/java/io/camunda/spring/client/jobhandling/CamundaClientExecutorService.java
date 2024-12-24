/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wrapper bean for {@link ScheduledExecutorService} required in Spring Zeebe for Job Handling,
 * Retry Management and so on.
 *
 * <p>This is wrapped, so you can have multiple executor services in the Spring context and qualify
 * the right one.
 */
public class CamundaClientExecutorService {

  private final ScheduledExecutorService scheduledExecutorService;
  private final boolean ownedByCamundaClient;

  public CamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutorService, final boolean ownedByZeebeClient) {
    this.scheduledExecutorService = scheduledExecutorService;
    ownedByCamundaClient = ownedByZeebeClient;
  }

  public boolean isOwnedByCamundaClient() {
    return ownedByCamundaClient;
  }

  public ScheduledExecutorService get() {
    return scheduledExecutorService;
  }

  public static CamundaClientExecutorService createDefault() {
    return createDefault(1);
  }

  public static CamundaClientExecutorService createDefault(final int threads) {
    final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(threads);
    return new CamundaClientExecutorService(threadPool, true);
  }
}
