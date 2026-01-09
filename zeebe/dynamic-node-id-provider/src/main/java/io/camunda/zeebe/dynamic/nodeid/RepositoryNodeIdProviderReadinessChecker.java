/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Initialized;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A readiness checker that verifies if all nodes in the cluster have up-to-date leases in the
 * repository containing the current node's version mapping.
 */
public class RepositoryNodeIdProviderReadinessChecker {
  private final int clusterSize;
  private final int currentNodeId;
  private final Version currentVersion;
  private final NodeIdRepository nodeIdRepository;
  private final ScheduledExecutorService scheduler;
  private final Duration checkInterval;

  public RepositoryNodeIdProviderReadinessChecker(
      final int clusterSize,
      final NodeInstance currentNodeInstance,
      final NodeIdRepository nodeIdRepository,
      final ScheduledExecutorService scheduler,
      final Duration checkInterval) {
    this.clusterSize = clusterSize;
    currentNodeId = currentNodeInstance.id();
    currentVersion = currentNodeInstance.version();
    this.nodeIdRepository = nodeIdRepository;
    this.scheduler = scheduler;
    this.checkInterval = checkInterval;
  }

  /**
   * Waits until all nodes in the cluster are up to date by verifying their leases contain the
   * current version mapping. Returns a future that completes when all nodes are ready
   *
   * @return CompletableFuture that completes when all nodes are up to date
   */
  public CompletableFuture<Void> waitUntilAllNodesAreUpToDate() {
    final var result = new CompletableFuture<Void>();
    final var nodesToCheck = new HashSet<Integer>();
    for (int i = 0; i < clusterSize; i++) {
      if (i != currentNodeId) {
        nodesToCheck.add(i);
      }
    }

    final var startTime = System.currentTimeMillis();
    scheduleCheck(result, nodesToCheck, startTime);
    return result;
  }

  private void scheduleCheck(
      final CompletableFuture<Void> result, final Set<Integer> nodesToCheck, final long startTime) {
    scheduler.schedule(
        () -> performCheck(result, nodesToCheck, startTime),
        checkInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void performCheck(
      final CompletableFuture<Void> result, final Set<Integer> nodesToCheck, final long startTime) {
    try {
      nodesToCheck.removeIf(this::isNodeUpToDate);
    } catch (final Exception e) {
      // In case of exception, we just retry in the next scheduled check
    }

    if (nodesToCheck.isEmpty()) {
      result.complete(null);
    } else {
      scheduleCheck(result, nodesToCheck, startTime);
    }
  }

  private boolean isNodeUpToDate(final int nodeId) {
    try {
      final var lease = nodeIdRepository.getLease(nodeId);
      if (!(lease instanceof final Initialized initializedLease)) {
        return false;
      }
      final var knownVersionMap =
          initializedLease.lease().knownVersionMappings().mappingsByNodeId();
      return knownVersionMap.containsKey(currentNodeId)
          && knownVersionMap.get(currentNodeId).equals(currentVersion);
    } catch (final Exception e) {
      return false;
    }
  }
}
