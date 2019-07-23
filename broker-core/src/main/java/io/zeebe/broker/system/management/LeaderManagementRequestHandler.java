/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management;

import io.atomix.core.Atomix;
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
    implements Service<LeaderManagementRequestHandler> {

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private final Int2ObjectHashMap<Partition> leaderForPartitions = new Int2ObjectHashMap<>();
  private final ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((s, p) -> addPartition(p))
          .onRemove((s, p) -> removePartition(p))
          .build();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;
  private Atomix atomix;

  @Override
  public void start(final ServiceStartContext startContext) {
    this.atomix = atomixInjector.getValue();
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  public LeaderManagementRequestHandler get() {
    return this;
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

  public ServiceGroupReference<Partition> getLeaderPartitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }

  public PushDeploymentRequestHandler getPushDeploymentRequestHandler() {
    return pushDeploymentRequestHandler;
  }
}
