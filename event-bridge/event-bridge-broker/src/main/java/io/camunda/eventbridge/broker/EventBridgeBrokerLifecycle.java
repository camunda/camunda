/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker;

import io.camunda.eventbridge.broker.bootstrap.BrokerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Spring lifecycle bean that manages the broker bootstrap. Starts after all infrastructure beans
 * (scheduler, cluster, topology manager) are ready. Stops before them.
 */
public class EventBridgeBrokerLifecycle implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(EventBridgeBrokerLifecycle.class);

  private final BrokerBootstrap brokerBootstrap;
  private volatile boolean running = false;

  public EventBridgeBrokerLifecycle(final BrokerBootstrap brokerBootstrap) {
    this.brokerBootstrap = brokerBootstrap;
  }

  @Override
  public void start() {
    LOG.info("Starting EventBridge broker");
    brokerBootstrap.start();
    running = true;
    LOG.info("EventBridge broker started");
  }

  @Override
  public void stop() {
    LOG.info("Stopping EventBridge broker");
    brokerBootstrap.stop();
    running = false;
    LOG.info("EventBridge broker stopped");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Start last — after scheduler, cluster, topology manager
    // Stop first — before infrastructure is torn down
    return Integer.MAX_VALUE - 1;
  }
}
