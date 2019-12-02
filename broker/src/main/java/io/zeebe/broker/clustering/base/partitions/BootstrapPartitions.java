/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.clustering.base.partitions.Partition.getPartitionName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionInstallServiceName;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.zeebe.broker.clustering.atomix.AtomixFactory;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Always installed on broker startup: reads configuration of all locally available partitions and
 * starts the corresponding services (logstream, partition ...)
 */
public class BootstrapPartitions implements Service<Void> {
  private final BrokerCfg brokerCfg;
  private final ServiceContainer serviceContainer;
  private ServiceStartContext startContext;
  private final Atomix atomix;

  public BootstrapPartitions(final Atomix atomix, final BrokerCfg brokerCfg) {
    this.atomix = atomix;
    this.brokerCfg = brokerCfg;
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup(Partition.GROUP_NAME);

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

          for (final RaftPartition owningPartition : owningPartitions) {
            installPartition(owningPartition);
          }
  }

  @Override
  public Void get() {
    return null;
  }

  private void installPartition(final RaftPartition partition) {
    installPartition(startContext, partition);
  }

  private void installPartition(
      final ServiceStartContext startContext, final RaftPartition partition) {
    final String partitionName = getPartitionName(partition.id().id());
    final ServiceName<PartitionInstallService> partitionInstallServiceName =
        partitionInstallServiceName(partitionName);

    final PartitionInstallService partitionInstallService =
        new PartitionInstallService(
            partition, atomix.getEventService(), serviceContainer, brokerCfg);

    startContext
        .createService(partitionInstallServiceName, partitionInstallService)
        .dependency(ATOMIX_JOIN_SERVICE)
        .install();
  }

  public Injector<Atomix> getAtomixInjector() {
    return this.atomixInjector;
  }
}
