/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper.lease;

import io.camunda.zeebe.broker.clustering.mapper.NodeInstance;
import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient.Lease;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class InMemory {

  public static class Server {

    private final ReentrantLock[] locks;
    private final Lease[] leases;

    public Server(final int clusterSize) {
      leases = new Lease[clusterSize];
      locks = new ReentrantLock[clusterSize];
      for (int i = 0; i < clusterSize; i++) {
        locks[i] = new ReentrantLock();
      }
    }

    public Lease updateAt(final int nodeId, final Function<Lease, Lease> f) {
      final var lock = locks[nodeId];
      lock.lock();
      try {
        leases[nodeId] = f.apply(leases[nodeId]);
        return leases[nodeId];
      } finally {
        lock.unlock();
      }
    }
  }

  public static class Client extends AbstractLeaseClient {

    private final Server server;

    public Client(
        final Server server,
        final int clusterSize,
        final String taskId,
        final Clock clock,
        final Duration leaseExpirationDuration) {
      super(clusterSize, taskId, clock, leaseExpirationDuration);
      this.server = server;
    }

    @Override
    public void setNodeIdMappings(final NodeIdMappings nodeIdMappings) {
      super.setNodeIdMappings(nodeIdMappings);
    }

    @Override
    public Lease tryAcquireLease(final int nodeId) {
      return server.updateAt(
          nodeId,
          lease -> {
            if (lease == null) {
              currentLease = makeLease(NodeInstance.initial(nodeId));
              return currentLease;
            } else if (lease.isStillValid(clock.millis(), leaseExpirationDuration)) {
              currentLease = makeLease(lease.nodeInstance());
              return currentLease;
            } else {
              return null;
            }
          });
    }

    @Override
    protected void initializeForNode(final int id) {}

    @Override
    public Lease renewCurrentLease() {
      return server.updateAt(
          currentLease.nodeInstance().id(),
          lease -> currentLease.renew(clock.millis(), leaseExpirationDuration.toMillis()));
    }

    @Override
    public void releaseLease() {
      if (currentLease != null) {
        server.updateAt(currentLease.nodeInstance().id(), lease -> null);
      }
    }

    private Lease makeLease(final NodeInstance currentNodeInstance) {
      return new Lease(
          taskId,
          System.currentTimeMillis() + leaseExpirationDuration.toMillis(),
          currentNodeInstance.nextVersion(),
          nodeIdMappings);
    }
  }
}
