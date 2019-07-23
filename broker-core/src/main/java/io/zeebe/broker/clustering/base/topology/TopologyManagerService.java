/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.topology;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class TopologyManagerService implements Service<TopologyManager> {
  private final NodeInfo localMember;
  private final ClusterCfg clusterCfg;
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private TopologyManagerImpl topologyManager;
  private final ServiceGroupReference<Partition> leaderInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(
              (name, partition) ->
                  topologyManager.updateRole(partition.getState(), partition.getPartitionId()))
          .build();
  private final ServiceGroupReference<Partition> followerInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(
              (name, partition) ->
                  topologyManager.updateRole(partition.getState(), partition.getPartitionId()))
          .build();

  public TopologyManagerService(NodeInfo localMember, ClusterCfg clusterCfg) {
    this.localMember = localMember;
    this.clusterCfg = clusterCfg;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Atomix atomix = atomixInjector.getValue();

    topologyManager = new TopologyManagerImpl(atomix, localMember, clusterCfg);

    startContext.async(startContext.getScheduler().submitActor(topologyManager));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(topologyManager.close());
  }

  @Override
  public TopologyManager get() {
    return topologyManager;
  }

  public ServiceGroupReference<Partition> getLeaderInstallReference() {
    return leaderInstallReference;
  }

  public ServiceGroupReference<Partition> getFollowerInstallReference() {
    return followerInstallReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
