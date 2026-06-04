/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Bridges the {@link BrokerClient} into Spring's CRaC checkpoint/restore lifecycle. The broker
 * client submits actors to the actor scheduler, which is rebuilt across a checkpoint, so it must be
 * stopped before the checkpoint and restarted (recreating its actors) after restore.
 *
 * <p>Phase: between the cluster and the broker — the broker uses the broker client, so the client
 * must start before the broker (lower phase) and stop after it.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class BrokerClientCheckpointLifecycle implements SmartLifecycle {

  static final int PHASE = Integer.MIN_VALUE + 200;

  private static final Logger LOG = LoggerFactory.getLogger(BrokerClientCheckpointLifecycle.class);

  private final BrokerClient brokerClient;
  // The broker client is started eagerly by its @Bean before this bridge is constructed.
  private volatile boolean running = true;

  public BrokerClientCheckpointLifecycle(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @Override
  public void start() {
    if (!running) {
      LOG.info("CRaC: restarting BrokerClient after restore");
      brokerClient.start().forEach(ActorFuture::join);
      running = true;
    }
  }

  @Override
  public void stop() {
    if (running) {
      LOG.info("CRaC: stopping BrokerClient before checkpoint");
      brokerClient.close();
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }
}
