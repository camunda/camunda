/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import io.camunda.zeebe.broker.Broker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Bridges the embedded {@link Broker} into Spring's CRaC checkpoint/restore lifecycle. The broker
 * owns the partition RocksDB, command-API transport, and embedded gateway — all of which must be
 * torn down before a checkpoint and recreated after restore.
 *
 * <p>The broker restarts in place: {@code start()} after {@code close()} resets its state and
 * rebuilds the startup actor on the (already restarted) scheduler/cluster, then re-runs the broker
 * startup process (recreating RocksDB resources, partitions, command API and the embedded gateway).
 *
 * <p>Phase: highest of the broker-stack bridges, so the broker stops FIRST at checkpoint and starts
 * LAST on restore — it depends on the scheduler, cluster, and broker client.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class BrokerCheckpointLifecycle implements SmartLifecycle {

  static final int PHASE = Integer.MIN_VALUE + 300;

  private static final Logger LOG = LoggerFactory.getLogger(BrokerCheckpointLifecycle.class);

  private final Broker broker;

  public BrokerCheckpointLifecycle(final Broker broker) {
    this.broker = broker;
  }

  @Override
  public void start() {
    if (!broker.isRunning()) {
      LOG.info("CRaC: restarting Broker after restore");
      broker.start().join();
    }
  }

  @Override
  public void stop() {
    if (broker.isRunning()) {
      LOG.info("CRaC: stopping Broker before checkpoint");
      broker.close();
    }
  }

  @Override
  public boolean isRunning() {
    return broker.isRunning();
  }

  @Override
  public int getPhase() {
    return PHASE;
  }
}
