/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.crac;

import io.camunda.zeebe.scheduler.ActorScheduler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Bridges the {@link ActorScheduler} into Spring's CRaC checkpoint/restore lifecycle (Spring stops
 * {@link SmartLifecycle} beans before a checkpoint and starts them after restore).
 *
 * <p>The scheduler owns the actor threads — the timer/event file descriptors that otherwise abort a
 * checkpoint — and those threads are single-use, so {@link ActorScheduler#start()} rebuilds the
 * executor on restore. This bean is the foundational worked example of the "recreate-on-restore"
 * wiring; see {@code package-info.java} for the full plan.
 *
 * <p>Opt-in via {@code camunda.crac.enabled=true}.
 *
 * <p><b>Not yet sufficient on its own.</b> Equivalent SmartLifecycle bridges are still required for
 * {@code AtomixCluster} and the {@code Broker} (which must also recreate their internals on start,
 * following {@code EmbeddedBrokerRule.restartBroker}'s recreate pattern), ordered so the broker
 * stops before the scheduler. Consumers that cache references (e.g. {@code BrokerClient} holds the
 * cluster messaging service) must be rewired on restore. Until those land, enabling this on the
 * unified app will not produce a clean checkpoint.
 */
@Component
@ConditionalOnProperty(prefix = "camunda.crac", name = "enabled", havingValue = "true")
public class ActorSchedulerCheckpointLifecycle implements SmartLifecycle {

  private static final Logger LOG =
      LoggerFactory.getLogger(ActorSchedulerCheckpointLifecycle.class);
  private static final long STOP_TIMEOUT_SECONDS = 30;

  private final ActorScheduler scheduler;

  public ActorSchedulerCheckpointLifecycle(final ActorScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public void start() {
    // The scheduler is started eagerly by its @Bean during context refresh; only (re)start it here
    // when it is not running, i.e. after a CRaC restore (ActorScheduler.start() rebuilds the
    // threads).
    if (!scheduler.isRunning()) {
      LOG.info("CRaC: restarting ActorScheduler after restore");
      scheduler.start();
    }
  }

  @Override
  public void stop() {
    if (scheduler.isRunning()) {
      LOG.info("CRaC: stopping ActorScheduler before checkpoint");
      try {
        scheduler.stop().get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while stopping ActorScheduler", e);
      } catch (final ExecutionException | TimeoutException e) {
        throw new IllegalStateException("Failed to stop ActorScheduler for checkpoint", e);
      }
    }
  }

  @Override
  public boolean isRunning() {
    return scheduler.isRunning();
  }

  @Override
  public int getPhase() {
    // Start first, stop last — everything else runs on the scheduler.
    return Integer.MIN_VALUE;
  }
}
