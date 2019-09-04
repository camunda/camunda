/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.primitive.partition.PartitionGroup;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class BrokerHealthCheckService extends Actor implements Service<Void> {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Map<Integer, Boolean> partitionInstallStatus;
  /* set to true when all partitions are installed. Once set to true, it is never
  changed. */
  private volatile boolean brokerStarted = false;
  private final ServiceGroupReference<Partition> leaderInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> updateBrokerReadyStatus(partition.getPartitionId()))
          .build();
  private final ServiceGroupReference<Partition> followerInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> updateBrokerReadyStatus(partition.getPartitionId()))
          .build();
  private Atomix atomix;

  public boolean isBrokerReady() {
    return brokerStarted;
  }

  private void updateBrokerReadyStatus(int partitionId) {
    actor.call(
        () -> {
          if (!brokerStarted) {
            partitionInstallStatus.put(partitionId, true);
            brokerStarted = !partitionInstallStatus.containsValue(false);

            LOG.info("Partition '{}' is installed.", partitionId);

            if (brokerStarted) {
              LOG.info("All partitions are installed. Broker is ready!");
            }
          }
        });
  }

  private void initializePartitionInstallStatus() {
    final PartitionGroup partitionGroup =
        atomix.getPartitionService().getPartitionGroup(Partition.GROUP_NAME);
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    partitionInstallStatus =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(partition -> partition.id().id())
            .collect(Collectors.toMap(Function.identity(), p -> false));
  }

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();
    initializePartitionInstallStatus();
    startContext.getScheduler().submitActor(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    actor.close();
  }

  @Override
  public Void get() {
    return null;
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
