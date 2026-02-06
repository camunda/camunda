/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.dynamic.nodeid;

import io.camunda.zeebe.dynamic.nodeid.StoredRestoreStatus.RestoreStatus;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreStatusManager {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreStatusManager.class);
  private static final Duration RETRY_DELAY = Duration.ofMillis(1000);

  private final NodeIdRepository repository;

  public RestoreStatusManager(final NodeIdRepository repository) {
    this.repository = repository;
  }

  /**
   * Initializes the restore status for the given restore ID if it does not already exist.
   *
   * @param restoreId the restore ID to initialize
   * @return the initialized or existing restore status
   */
  public RestoreStatus initializeRestore(final long restoreId) throws InterruptedException {
    while (true) {
      try {
        final var existingStatus = repository.getRestoreStatus(restoreId);
        if (existingStatus != null) {
          if (existingStatus.restoreStatus().restoreId() != restoreId) {
            throw new RestoreIdMismatchException(
                restoreId, existingStatus.restoreStatus().restoreId());
          }
          LOG.debug("Restore status is already initialized {}", existingStatus.restoreStatus());
          return existingStatus.restoreStatus();
        }
      } catch (final Exception e) {
        LOG.debug("Error checking existing restore status, proceeding with initialization", e);
      }

      final var initialStatus = new RestoreStatus(restoreId, Set.of());
      try {
        repository.updateRestoreStatus(initialStatus, null);
        LOG.info("Initialized restore status: {}", initialStatus);
        return initialStatus;
      } catch (final Exception e) {
        LOG.debug("Failed to initialize restore, retrying", e);
        // Most likely due to concurrent update, retry to ensure restore status is initialized
        Thread.sleep(RETRY_DELAY);
      }
    }
  }

  /**
   * Updates the restore status by marking the given node ID as completed. Retries on concurrent
   * update conflicts.
   *
   * @param restoreId the restore ID to update
   * @param nodeId the node ID that completed restore
   */
  public void markNodeRestored(final long restoreId, final int nodeId) throws InterruptedException {

    // retry forever
    while (true) {
      try {
        final var storedStatus = repository.getRestoreStatus(restoreId);
        if (storedStatus == null) {
          throw new IllegalStateException("Restore status not initialized");
        }

        final var currentStatus = storedStatus.restoreStatus();

        if (currentStatus.restoreId() != restoreId) {
          throw new RestoreIdMismatchException(restoreId, currentStatus.restoreId());
        }

        if (currentStatus.restoredNodes().contains(nodeId)) {
          LOG.debug("Node {} already marked as restored", nodeId);
          return;
        }

        final var updatedCompletedNodes = new HashSet<>(currentStatus.restoredNodes());
        updatedCompletedNodes.add(nodeId);

        final var updatedStatus = new RestoreStatus(restoreId, updatedCompletedNodes);

        repository.updateRestoreStatus(updatedStatus, storedStatus.etag());
        LOG.info("Marked node {} as restored", nodeId);
        return;

      } catch (final Exception e) {
        LOG.debug("Concurrent update conflict marking node {} as restored, retrying", nodeId);
        Thread.sleep(RETRY_DELAY);
      }
    }
  }

  /**
   * Waits until all nodes in the cluster have completed restore.
   *
   * @param clusterSize the total number of nodes in the cluster
   * @param pollInterval the interval between status checks
   * @throws InterruptedException if interrupted while waiting
   * @throws IllegalStateException if timeout is reached or restore status is not initialized
   */
  public void waitForAllNodesRestored(
      final long restoreId, final int clusterSize, final Duration pollInterval)
      throws InterruptedException {
    final var expectedNodeIds = IntStream.range(0, clusterSize).boxed().collect(Collectors.toSet());

    while (true) {
      final var storedStatus = repository.getRestoreStatus(restoreId);
      if (storedStatus == null) {
        throw new IllegalStateException("Restore status not initialized");
      }

      final var currentStatus = storedStatus.restoreStatus();

      if (currentStatus.restoreId() != restoreId) {
        throw new RestoreIdMismatchException(restoreId, currentStatus.restoreId());
      }

      final var completedNodeIds = currentStatus.restoredNodes();
      if (completedNodeIds.containsAll(expectedNodeIds)) {
        LOG.info("All {} nodes have completed restore", clusterSize);
        return;
      }

      LOG.debug(
          "Waiting for restore completion: {}/{} nodes completed",
          completedNodeIds.size(),
          clusterSize);
      Thread.sleep(pollInterval.toMillis());
    }
  }
}
