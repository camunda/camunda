/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.camunda.eventbridge.messaging.threading.ExecutorServiceFactory;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutorServiceSetup {

  private static final Logger LOG = LoggerFactory.getLogger(TopologySetup.class);

  private final ExecutorServiceFactory factory;
  private ExecutorService executorService;

  public ExecutorServiceSetup(final ExecutorServiceFactory factory) {
    this.factory = factory;
  }

  ExecutorService start() {
    executorService = factory.create("fetch-stream");
    LOG.info("Executor Service Created");
    return executorService;
  }

  void stop() {
    LOG.info("Fetch Stream Executor Service Shutdown");
    executorService.shutdown();
  }
}
