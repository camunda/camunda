/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.atomix.cluster.ClusterMembershipService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Connects Hub to the engine of one physical tenant (ADR 0015). It runs while its broker leads
 * partition 1 of the tenant, and stops when the broker steps down.
 *
 * <p>So far the receiver only follows the leadership of partition 1; polling Hub and forwarding the
 * updates follows.
 */
@NullMarked
public final class HubUpdateReceiver extends Actor implements PartitionListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final String physicalTenantId;
  private final HubClient hubClient;
  private final PolicyInPort policyInPort;
  private final ClusterMembershipService membershipService;
  private final Duration retryInterval;

  private boolean leading;

  public HubUpdateReceiver(
      final String physicalTenantId,
      final HubClient hubClient,
      final PolicyInPort policyInPort,
      final ClusterMembershipService membershipService,
      final Duration retryInterval) {
    super("HubUpdateReceiver", null, Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    this.hubClient = hubClient;
    this.policyInPort = policyInPort;
    this.membershipService = membershipService;
    this.retryInterval = retryInterval;
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::stop);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    // a failed future would fail the leader transition, so the receiver starts on its own actor
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::start);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
      actor.run(this::stop);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  protected void onActorClosing() {
    stop();
    hubClient.close();
  }

  private void start() {
    leading = true;
    LOG.debug("Start receiving Hub policy updates for physical tenant {}", physicalTenantId);
  }

  private void stop() {
    if (leading) {
      LOG.debug("Stop receiving Hub policy updates for physical tenant {}", physicalTenantId);
    }
    leading = false;
  }
}
