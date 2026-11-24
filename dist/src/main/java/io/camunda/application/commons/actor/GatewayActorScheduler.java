/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The actor scheduler of the gateway side: the broker client, its topology manager and the gateway
 * actors (gRPC and REST).
 *
 * <p>When the gateway runs next to the broker, this is a separate scheduler. Thus the gateway
 * request path does not compete with the partitions for the actor threads of the broker. In all
 * other cases, it is the only scheduler of the process.
 */
@NullMarked
public final class GatewayActorScheduler implements CloseableSilently {

  private static final Logger LOGGER = LoggerFactory.getLogger(GatewayActorScheduler.class);

  private final ActorScheduler scheduler;
  private final boolean dedicated;

  private GatewayActorScheduler(final ActorScheduler scheduler, final boolean dedicated) {
    this.scheduler = scheduler;
    this.dedicated = dedicated;
  }

  /** Uses the scheduler of the process. Closing this instance does not close the scheduler. */
  @VisibleForTesting
  public static GatewayActorScheduler shared(final ActorScheduler scheduler) {
    return new GatewayActorScheduler(scheduler, false);
  }

  /** Uses a scheduler only for the gateway side. Closing this instance closes the scheduler. */
  static GatewayActorScheduler dedicated(final ActorScheduler scheduler) {
    return new GatewayActorScheduler(scheduler, true);
  }

  public ActorScheduler scheduler() {
    return scheduler;
  }

  @Override
  public void close() {
    if (!dedicated) {
      return;
    }

    try {
      scheduler.close();
    } catch (final Exception e) {
      LOGGER.warn("Failed to close the actor scheduler of the gateway", e);
    }
  }
}
