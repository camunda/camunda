/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import static io.zeebe.broker.Broker.actorNamePattern;
import static io.zeebe.broker.clustering.atomix.AtomixFactory.GROUP_NAME;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.PartitionListener;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.util.sched.Actor;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class BrokerHealthCheckService extends Actor implements PartitionListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private final Atomix atomix;
  private final BrokerInfo localMember;
  private Map<Integer, Boolean> partitionInstallStatus;
  /* set to true when all partitions are installed. Once set to true, it is never
  changed. */
  private volatile boolean brokerStarted = false;

  public BrokerHealthCheckService(BrokerInfo localMember, Atomix atomix) {
    this.localMember = localMember;
    this.atomix = atomix;
    initializePartitionInstallStatus();
  }

  public boolean isBrokerReady() {
    return brokerStarted;
  }

  @Override
  public void onBecomingFollower(int partitionId) {
    updateBrokerReadyStatus(partitionId);
  }

  @Override
  public void onBecomingLeader(int partitionId) {
    updateBrokerReadyStatus(partitionId);
  }

  private void updateBrokerReadyStatus(int partitionId) {
    actor.call(
        () -> {
          if (!brokerStarted) {
            partitionInstallStatus.put(partitionId, true);
            brokerStarted = !partitionInstallStatus.containsValue(false);

            if (brokerStarted) {
              LOG.debug("All partitions are installed. Broker is ready!");
            }
          }
        });
  }

  private void initializePartitionInstallStatus() {
    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup(GROUP_NAME);
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    partitionInstallStatus =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(partition -> partition.id().id())
            .collect(Collectors.toMap(Function.identity(), p -> false));

    LOG.info("{}", partitionInstallStatus);
  }

  @Override
  public String getName() {
    return actorNamePattern(localMember, "HealthCheckService");
  }
}
