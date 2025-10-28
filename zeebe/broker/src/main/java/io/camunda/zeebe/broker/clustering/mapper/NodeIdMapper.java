/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient;
import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient.InitialLease;
import io.camunda.zeebe.broker.clustering.mapper.lease.LeaseClient.Lease;
import io.camunda.zeebe.broker.clustering.mapper.lease.NodeIdMappings;
import io.camunda.zeebe.broker.clustering.mapper.lease.S3Lease;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class NodeIdMapper implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdMapper.class);
  private static final Runnable SHUTDOWN_VM = () -> Runtime.getRuntime().halt(1);
  private final LeaseClient lease;
  private final ScheduledExecutorService executor;
  private NodeInstance nodeInstance;
  private final String taskId;
  private final Runnable onRenewalFailure;
  private ScheduledFuture<?> renewealTimer;
  private boolean previousOwnerExpired;
  private final int clusterSize;
  private final ScheduledExecutorService retryingExecutor;

  public NodeIdMapper(final LeaseClient lease, final int clusterSize) {
    this(lease, SHUTDOWN_VM, clusterSize);
  }

  @VisibleForTesting
  public NodeIdMapper(
      final LeaseClient lease, final Runnable onRenewalFailure, final int clusterSize) {
    this.clusterSize = clusterSize;
    this.lease = lease;
    taskId = lease.taskId();
    this.onRenewalFailure = onRenewalFailure;
    executor = newSingleThreadScheduledExecutor(r -> new Thread(r, "NodeIdMapper-Executor"));
    retryingExecutor =
        newSingleThreadScheduledExecutor(r -> new Thread(r, "NodeIdMapper-RetryingExecutor"));
    executor.execute(() -> MDC.put("taskId", taskId));
    lease.initialize();
  }

  public NodeIdMapper(final S3LeaseConfig config, final String taskId, final int clusterSize) {
    this(new S3Lease(config, taskId, clusterSize, Clock.systemUTC()), clusterSize);
  }

  public NodeInstance getNodeInstance() {
    return nodeInstance;
  }

  public static String randomTaskId() {
    return UUID.randomUUID().toString().substring(0, 6);
  }

  public boolean isHealthy() {
    final ScheduledFuture<Boolean> future = executor.schedule(() -> true, 0, TimeUnit.SECONDS);
    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }

  public NodeInstance start() {
    // additional MDC as it's not running in the executor thread
    try (final var ctx = MDC.putCloseable("taskId", taskId)) {
      LOG.info("Starting nodeIdMapper");
      final var initialLease = acquireLease();
      LOG.info("Acquired lease {}", initialLease);
      previousOwnerExpired = initialLease.previousHolderExpired();
      scheduleRenewal();
      nodeInstance = initialLease.lease().nodeInstance();
      return nodeInstance;
    }
  }

  // Waits until it is safe for the broker to start. It is safe if the previous lease owner
  // gracefully released the lease. If the previous lease was timedout, then wait until all other
  // nodes has kicked out the previous version.
  public CompletableFuture<Boolean> waitUntilReady() {
    if (!previousOwnerExpired) {
      LOG.info("Previous owner released the lease gracefully. No need to wait.");
      return CompletableFuture.completedFuture(true);
    }

    LOG.info(
        "Previous owner did not release the lease gracefully. Waiting until all nodes have updated.");
    final var readyFuture = new CompletableFuture<Boolean>();

    retryingExecutor.submit(
        () -> {
          try {
            waitUntilAllNodesHaveUpdatedVersion();
            LOG.info("All nodes have updated the version. NodeIdMapper is ready.");
            readyFuture.complete(true);
          } catch (final Exception e) {
            LOG.error("Failed to wait until ready: {}", e.getMessage(), e);
            readyFuture.completeExceptionally(e);
          }
        });

    return readyFuture;
  }

  private void waitUntilAllNodesHaveUpdatedVersion() throws Exception {
    final var retryConfig = new RetryConfiguration();
    retryConfig.setMaxRetries(Integer.MAX_VALUE);
    retryConfig.setMinRetryDelay(Duration.ofSeconds(1));
    retryConfig.setMaxRetryDelay(Duration.ofSeconds(10));
    retryConfig.setRetryDelayMultiplier(1.5);
    final RetryDecorator retryDecorator =
        new RetryDecorator(retryConfig, Exception.class::isInstance);
    final var startedAt = Instant.now();

    // TODO: Do not block the thread. Resubmit each retry.
    // TODO: Set max retry timeout
    retryDecorator.decorate(
        "Wait for all nodes to update version",
        () -> {
          if (Instant.now().isAfter(startedAt.plusSeconds(120))) {
            LOG.info("Timed out waiting for all nodes to update version: continuing anyway");
            return null;
          }
          final var allLeases = lease.getAllLeases();
          final var allNodesUpdated = allMappingsAreUpdated(allLeases, nodeInstance, clusterSize);
          if (allNodesUpdated) {
            LOG.info(
                "All nodes have updated to version {} for nodeId {}",
                nodeInstance.version(),
                nodeInstance.id());
            return null;
          } else {
            LOG.debug(
                "Not all nodes have updated to version {} for nodeId {} yet. Retrying...",
                nodeInstance.version(),
                nodeInstance.id());
            throw new IllegalStateException("Not all nodes have updated version yet");
          }
        });
  }

  @VisibleForTesting
  public static boolean allMappingsAreUpdated(
      final Collection<Lease> mappings, final NodeInstance nodeInstance, final int clusterSize) {
    if (mappings.size() != clusterSize) {
      LOG.warn("Mappings do not contain all nodes: {} of {}", mappings.size(), clusterSize);
      return false;
    }
    return mappings.stream()
        .filter(l -> l.nodeInstance().id() != nodeInstance.id())
        .allMatch(
            lease -> {
              final var m = lease.nodeIdMappings();
              final var versionInMapping = m.mappings().get(String.valueOf(nodeInstance.id()));

              // If this node is not in the mapping, it means the node hasn't seen this
              // version yet
              if (versionInMapping == null) {
                LOG.debug(
                    "No mapping found for nodeId {} found in node {}",
                    nodeInstance.id(),
                    lease.nodeInstance());
                return false;
              }

              // Check if the version in the mapping matches this node's version
              final var areEqual = versionInMapping == nodeInstance.version();
              if (!areEqual) {
                LOG.debug(
                    "Node contains mapping version {} which is different than expected {}",
                    versionInMapping,
                    nodeInstance.version());
              }
              return areEqual;
            });
  }

  public long expiresAt() {
    return Optional.ofNullable(lease.currentLease()).map(Lease::timestamp).orElse(0L);
  }

  @Override
  public void close() {
    LOG.debug("Closing nodeIdMapper");
    try {
      renewealTimer.cancel(true);
      executor.submit(lease::releaseLease).get(15, TimeUnit.SECONDS);
    } catch (final Exception e) {
      //
      LOG.warn("Failed to release the lease gracefully");
    }
    executor.shutdown();
    retryingExecutor.shutdownNow();
  }

  private InitialLease acquireLease() {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return busyAcquireLease();
              } catch (final Exception e) {
                LOG.error("Failed to acquire lease: {}", e.getMessage());
                throw new CompletionException(e);
              }
            },
            retryingExecutor)
        .join();
  }

  private InitialLease busyAcquireLease() throws Exception {
    final var retryConfig = new RetryConfiguration();
    // TODO: Externalize config
    retryConfig.setMaxRetries(Integer.MAX_VALUE);
    retryConfig.setMinRetryDelay(Duration.ofMillis(500));
    retryConfig.setMaxRetryDelay(Duration.ofSeconds(5));
    retryConfig.setRetryDelayMultiplier(1.25);
    final RetryDecorator retryDecorator =
        new RetryDecorator(retryConfig, LeaseException.class::isInstance);

    return retryDecorator.decorate(
        "Acquire Initial Lease",
        () -> {
          final var acquiredLease = lease.acquireLease();
          if (acquiredLease != null) {
            LOG.info("Lease acquired={}", acquiredLease);
          } else {
            LOG.warn("Failed to acquire the lease for task={}", taskId);
          }
          return acquiredLease;
        });
  }

  private void scheduleRenewal() {
    renewealTimer =
        executor.scheduleAtFixedRate(
            () -> {
              if (lease.renewLease() == null) {
                LOG.info("Renewal lease not acquired");
                if (executor.isShutdown() || executor.isTerminated()) {
                  if (renewealTimer != null) {
                    renewealTimer.cancel(true);
                  }
                  return;
                }
                onRenewalFailure.run();
              }
            },
            1,
            Math.max(lease.expiryDuration().toSeconds() / 6, 1), // 10sec
            TimeUnit.SECONDS);
  }

  public void setCurrentClusterMembers(final List<MemberId> currentMembers) {
    executor.submit(
        () -> {
          final var map =
              currentMembers.stream()
                  .collect(Collectors.toMap(MemberId::id, MemberId::getIdVersion));

          lease.setNodeIdMappings(new NodeIdMappings(map));
        });
  }
}
