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
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The actor scheduler of the gateway side: the broker client and its topology manager, the job
 * stream client, and the gateway actors (gRPC and REST).
 *
 * <p>Which scheduler this is depends on the deployment:
 *
 * <ul>
 *   <li>Gateway next to the broker ({@code api.enabled} on a broker): a dedicated scheduler, so
 *       that the gateway request path does not compete with the partitions for the actor threads of
 *       the broker. Its CPU thread count is {@code camunda.api.grpc.management-threads}. The broker
 *       uses the same broker client for its own requests (checkpoints, rebalancing, scaling), so
 *       these requests also run on this scheduler.
 *   <li>All other cases (standalone gateway, broker without gateway): the only {@link
 *       ActorScheduler} of the process, shared with everything else.
 * </ul>
 *
 * <h2>Why a holder and not a second {@link ActorScheduler} bean</h2>
 *
 * The broker and the gateway live in one Spring context, so a second {@link ActorScheduler} bean
 * would make every injection by type ambiguous.
 *
 * <p>Gateway-side components inject this type and use {@link #scheduler()}. Broker-side components
 * inject {@link ActorScheduler} directly.
 *
 * @param scheduler the scheduler to submit gateway-side actors to
 * @param ownsScheduler true for a dedicated scheduler, which closing this holder also closes
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
