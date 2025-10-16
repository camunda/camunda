/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper.lease;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;

public abstract class AbstractLeaseClient implements LeaseClient {
  private static final Logger LOG = getLogger(AbstractLeaseClient.class);
  protected final String taskId;
  protected final Clock clock;
  protected final Duration leaseExpirationDuration;
  protected NodeIdMappings nodeIdMappings = NodeIdMappings.empty();
  protected Lease currentLease = null;
  protected final int clusterSize;

  protected AbstractLeaseClient(
      final int clusterSize,
      final String taskId,
      final Clock clock,
      final Duration leaseExpirationDuration) {
    this.clusterSize = clusterSize;
    this.taskId = taskId;
    this.clock = clock;
    this.leaseExpirationDuration = leaseExpirationDuration;
  }

  @Override
  public String taskId() {
    return taskId;
  }

  @Override
  public final void initialize() {
    for (int i = 0; i < clusterSize; i++) {
      initializeForNode(i);
    }
  }

  @Override
  public final InitialLease acquireLease() {
    if (currentLease != null) {
      throw new IllegalStateException(
          "Tried to acquire a new lease, but  it is already in use: " + currentLease);
    }

    for (int i = 0; i < clusterSize; i++) {
      final var lease = tryAcquireLease(i);
      if (lease != null) {
        currentLease = lease.lease();
        return lease;
      }
    }
    throw new IllegalStateException("Failed to acquire lease");
  }

  @Override
  public void setNodeIdMappings(final NodeIdMappings nodeIdMappings) {
    if (!nodeIdMappings.equals(this.nodeIdMappings)) {
      LOG.info("Updated known cluster members and mapping: {}", nodeIdMappings);
      this.nodeIdMappings = nodeIdMappings;
    }
  }

  @Override
  public final Lease renewLease() {
    if (currentLease == null) {
      throw new IllegalStateException("Cannot renew lease, no lease acquired already");
    }
    if (!currentLease.isStillValid(clock.millis(), leaseExpirationDuration)) {
      throw new IllegalStateException("Cannot renew lease, current one expired");
    }
    currentLease = renewCurrentLease();
    return currentLease;
  }

  @Override
  public final Lease currentLease() {
    return currentLease;
  }

  @Override
  public Duration expiryDuration() {
    return leaseExpirationDuration;
  }

  // Atomic operation
  protected abstract InitialLease tryAcquireLease(int id);

  // Atomic operation
  protected abstract void initializeForNode(int id);

  // Atomic operation
  protected abstract Lease renewCurrentLease();
}
