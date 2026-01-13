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

/**
 * A readiness checker that verifies if all nodes in the cluster have up-to-date leases in the
 * repository containing the current node's version mapping.
 */
public class RepositoryNodeIdProviderReadinessChecker {
  private final int clusterSize;
  private final int currentNodeId;
  private final Version currentVersion;
  private final NodeIdRepository nodeIdRepository;
  private final Duration checkInterval;

  public RepositoryNodeIdProviderReadinessChecker(
      final int clusterSize,
      final NodeInstance currentNodeInstance,
      final NodeIdRepository nodeIdRepository,
      final Duration checkInterval) {
    this.clusterSize = clusterSize;
    currentNodeId = currentNodeInstance.id();
    currentVersion = currentNodeInstance.version();
    this.nodeIdRepository = nodeIdRepository;
    this.checkInterval = checkInterval;
  }

  /**
   * Waits until all nodes in the cluster are up to date by verifying their leases contain the
   * current version mapping. Returns a future that completes when all nodes are ready.
   *
   * @return CompletableFuture that completes when all nodes are up to date
   */
  public CompletableFuture<Boolean> waitUntilAllNodesAreUpToDate() {
    final var result = new CompletableFuture<Boolean>();
    final var nodesToCheck = new HashSet<Integer>();
    for (int i = 0; i < clusterSize; i++) {
      if (i != currentNodeId) {
        nodesToCheck.add(i);
      }
    }

    Thread.ofVirtual().start(() -> checkLoop(result, nodesToCheck));
    return result;
  }

  private void checkLoop(final CompletableFuture<Boolean> result, final Set<Integer> nodesToCheck) {
    while (!nodesToCheck.isEmpty() && !result.isDone()) {
      try {
        Thread.sleep(checkInterval);
        performCheck(nodesToCheck);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        result.completeExceptionally(e);
        return;
      } catch (final Exception e) {
        // In case of exception, we just retry in the next check
      }
    }

    if (!result.isDone()) {
      result.complete(true);
    }
  }

  private void performCheck(final Set<Integer> nodesToCheck) {
    nodesToCheck.removeIf(this::isNodeUpToDate);
  }

  private boolean isNodeUpToDate(final int nodeId) {
    try {
      final var lease = nodeIdRepository.getLease(nodeId);
      if (!(lease instanceof final Initialized initializedLease)) {
        return false;
      }
      final var knownVersionMap =
          initializedLease.lease().knownVersionMappings().mappingsByNodeId();
      return knownVersionMap.getOrDefault(currentNodeId, Version.zero()).equals(currentVersion);
    } catch (final Exception e) {
      return false;
    }
  }
}
