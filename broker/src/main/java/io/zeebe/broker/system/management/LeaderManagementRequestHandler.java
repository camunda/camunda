/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.atomix.core.Atomix;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor implements PartitionListener {

  private final Int2ObjectHashMap<LogStream> leaderForPartitions = new Int2ObjectHashMap<>();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private final Atomix atomix;

  public LeaderManagementRequestHandler(Atomix atomix) {
    this.atomix = atomix;
  }

  @Override
  public void onBecomingFollower(final int partitionId) {
    Loggers.SYSTEM_LOGGER.info("onBecomingFollower({})", partitionId);
    actor.submit(() -> leaderForPartitions.remove(partitionId));
  }

  @Override
  public void onBecomingLeader(final int partitionId, final LogStream logStream) {
    Loggers.SYSTEM_LOGGER.info("onBecomingLeader({})", partitionId);
    actor.submit(() -> leaderForPartitions.put(partitionId, logStream));
  }

  @Override
  public String getName() {
    return "management-request-handler";
  }

  @Override
  protected void onActorStarting() {
    Loggers.SYSTEM_LOGGER.error("Starting handler");
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, atomix);
    atomix.getCommunicationService().subscribe("deployment", pushDeploymentRequestHandler);
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }
}
