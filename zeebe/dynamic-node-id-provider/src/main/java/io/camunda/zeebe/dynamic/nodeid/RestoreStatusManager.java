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
   * Initializes restore for the given backup ID. If a restore object already exists, does nothing.
   *
   * @param backupId the backup ID to restore from
   */
  public void initializeRestore(final long backupId) {
    try {
      final var existingStatus = repository.getRestoreStatus();
      if (existingStatus != null) {
        LOG.debug("Restore restoreStatus already exists, skipping initialization");
        return;
      }
    } catch (final Exception e) {
      LOG.debug("Error checking existing restore restoreStatus, proceeding with initialization", e);
    }

    final var initialStatus = new RestoreStatus(backupId, RestoreStatus.Status.RESTORING, Set.of());
    try {
      repository.markRestored(initialStatus, null);
      LOG.info("Initialized restore for backup ID {}", backupId);
    } catch (final Exception e) {
      LOG.warn("Failed to initialize restore for backup ID {}", backupId, e);
      throw e;
    }
  }

  /**
   * Updates the restore restoreStatus by marking the given node ID as completed. Retries on
   * concurrent update conflicts.
   *
   * @param nodeId the node ID that completed restore
   */
  public void markNodeRestored(final int nodeId) {

    // retry forever
    while (true) {
      try {
        final var storedStatus = repository.getRestoreStatus();
        if (storedStatus == null) {
          throw new IllegalStateException("Restore restoreStatus not initialized");
        }

        final var currentStatus = storedStatus.restoreStatus();
        if (currentStatus.restoredNodes().contains(nodeId)) {
          LOG.debug("Node {} already marked as restored", nodeId);
          return;
        }

        final var updatedCompletedNodes = new HashSet<>(currentStatus.restoredNodes());
        updatedCompletedNodes.add(nodeId);

        final var updatedStatus =
            new RestoreStatus(
                currentStatus.backupId(), currentStatus.status(), updatedCompletedNodes);

        repository.markRestored(updatedStatus, storedStatus.etag());
        LOG.info("Marked node {} as restored", nodeId);
        return;

      } catch (final Exception e) {
        LOG.debug("Concurrent update conflict marking node {} as restored, retrying", nodeId);
        try {
          Thread.sleep(RETRY_DELAY.toMillis());
        } catch (final InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while retrying restore restoreStatus update", ie);
        }
      }
    }
  }

  /**
   * Waits until all nodes in the cluster have completed restore.
   *
   * @param clusterSize the total number of nodes in the cluster
   * @param pollInterval the interval between restoreStatus checks
   * @throws InterruptedException if interrupted while waiting
   * @throws IllegalStateException if timeout is reached or restore restoreStatus is not initialized
   */
  public void waitForAllNodesRestored(final int clusterSize, final Duration pollInterval)
      throws InterruptedException {
    final var expectedNodeIds = IntStream.range(0, clusterSize).boxed().collect(Collectors.toSet());

    while (true) {
      final var storedStatus = repository.getRestoreStatus();
      if (storedStatus == null) {
        throw new IllegalStateException("Restore restoreStatus not initialized");
      }

      final var completedNodeIds = storedStatus.restoreStatus().restoredNodes();
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
