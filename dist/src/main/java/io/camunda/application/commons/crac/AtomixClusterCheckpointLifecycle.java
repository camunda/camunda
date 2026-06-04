/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import io.atomix.cluster.AtomixCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Bridges the {@link AtomixCluster} cluster transport into Spring's CRaC checkpoint/restore
 * lifecycle. The transport owns the netty event loops (epoll/eventfd) and cluster sockets that
 * otherwise abort a checkpoint.
 *
 * <p>{@code AtomixCluster} restarts in place: {@code stop()} tears down the services and {@code
 * start()} resets the terminal close/open futures, rebuilds the thread context, and restarts the
 * underlying {@code Managed*} services (which recreate their netty event loops / executors).
 * Because the restart is in place, consumers that cached {@code getMessagingService()} / {@code
 * getEventService()} (e.g. {@code BrokerClient}) stay valid across the cycle.
 *
 * <p>Phase: above the scheduler (which is {@link Integer#MIN_VALUE}) so the cluster stops BEFORE
 * the scheduler at checkpoint and starts AFTER it on restore — the cluster's services run on the
 * actor scheduler.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}. Still required for a fully clean unified
 * checkpoint: a SmartLifecycle bridge for the {@code Broker} (RocksDB + partitions, recreated per
 * {@code EmbeddedBrokerRule.restartBroker}), phased to stop before the cluster.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class AtomixClusterCheckpointLifecycle implements SmartLifecycle {

  // Stop before the scheduler (Integer.MIN_VALUE), after the broker (added later, higher phase).
  static final int PHASE = Integer.MIN_VALUE + 100;

  private static final Logger LOG = LoggerFactory.getLogger(AtomixClusterCheckpointLifecycle.class);

  private final AtomixCluster cluster;

  public AtomixClusterCheckpointLifecycle(final AtomixCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public void start() {
    // The cluster is started eagerly by its @Bean during refresh; only (re)start it here when it is
    // not running, i.e. after a CRaC restore (AtomixCluster.start() restarts the transport in
    // place).
    if (!cluster.isRunning()) {
      LOG.info("CRaC: restarting AtomixCluster transport after restore");
      cluster.start().join();
    }
  }

  @Override
  public void stop() {
    if (cluster.isRunning()) {
      LOG.info("CRaC: stopping AtomixCluster transport before checkpoint");
      cluster.stop().join();
    }
  }

  @Override
  public boolean isRunning() {
    return cluster.isRunning();
  }

  @Override
  public int getPhase() {
    return PHASE;
  }
}
