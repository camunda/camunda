/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.atomix.core.Atomix;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor
    implements PartitionListener {

  private final Int2ObjectHashMap<Partition> leaderForPartitions = new Int2ObjectHashMap<>();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private final Atomix atomix;

  public LeaderManagementRequestHandler(Atomix atomix) {
    this.atomix = atomix;
  }

  @Override
  public void onBecomingFollower(Partition partition) {
    addPartition(partition);
  }

  @Override
  public void onBecomingLeader(Partition partition) {
    removePartition(partition);
  }

  @Override
  public String getName() {
    return "management-request-handler";
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler =
        new PushDeploymentRequestHandler(leaderForPartitions, actor, atomix);
    atomix.getCommunicationService().subscribe("deployment", pushDeploymentRequestHandler);
  }

  private void addPartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.put(partition.getPartitionId(), partition));
  }

  private void removePartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.remove(partition.getPartitionId()));
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }
}
