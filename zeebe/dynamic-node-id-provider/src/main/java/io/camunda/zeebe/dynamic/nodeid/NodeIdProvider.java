/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Initialized;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Uninitialized;
import io.camunda.zeebe.util.ExponentialBackoff;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeIdProvider implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdProvider.class);
  private final NodeIdRepository nodeIdRepository;
  private final int clusterSize;
  private final InstantSource clock;
  private volatile StoredLease.Initialized currentLease;
  private final Duration leaseDuration;
  private final String taskId;
  private final Runnable onLeaseFailure;
  private final ScheduledExecutorService executor;
  private final ExponentialBackoff backoff;
  private long currentDelay;
  private final Duration renewalDelay;
  private ScheduledFuture<?> renewalTask;

  public NodeIdProvider(
      final NodeIdRepository nodeIdRepository,
      final int clusterSize,
      final InstantSource clock,
      final Duration expiryDuration,
      final String taskId,
      final Runnable onLeaseFailure) {
    this.nodeIdRepository = nodeIdRepository;
    this.clusterSize = clusterSize;
    this.clock = clock;
    leaseDuration = expiryDuration;
    this.taskId = taskId;
    this.onLeaseFailure = onLeaseFailure;
    backoff = new ExponentialBackoff(Duration.ofSeconds(1), leaseDuration.dividedBy(2));
    renewalDelay = leaseDuration.dividedBy(3);
    currentDelay = 0L;
    executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "NodeIdProvider"));
    CompletableFuture.runAsync(this::acquireInitialLease, executor)
        .thenRun(this::startRenewalTimer);
  }

  private void startRenewalTimer() {
    renewalTask =
        executor.scheduleAtFixedRate(
            this::renew, renewalDelay.toMillis(), renewalDelay.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Verify the status of the lease, to be used for health checks. If the scheduler is not able to
   * reply in time, then it's marked as invalid. There's no need to set an external timeout, the
   * future will be completed with false.
   *
   * @return A future that completes with true if the lease is acquired and valid. false if the
   *     lease is not present anymore or invalid. The future never fails, all exceptions are
   *     converted to `false` (timeouts included).
   */
  public CompletableFuture<Boolean> isLeaseValid() {
    final var now = clock.millis();
    return CompletableFuture.supplyAsync(
            () -> currentLease != null && currentLease.lease().isStillValid(now, leaseDuration))
        .orTimeout(leaseDuration.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(
            t -> {
              LOG.warn("Failed to check the status of the lease. Marking it as failed", t);
              return false;
            });
  }

  public Initialized getCurrentLease() {
    return currentLease;
  }

  private void renew() {
    try {
      final var newLease = currentLease.lease().renew(clock.millis(), leaseDuration);
      LOG.trace("Renewing lease with {}", newLease);
      currentLease = nodeIdRepository.acquire(newLease, currentLease.eTag());
    } catch (final Exception e) {
      LOG.warn("Failed to renew the lease: process is going to shut down immediately.", e);
      currentLease = null;
      onLeaseFailure.run();
    }
  }

  /**
   * Method to initialize the provider. Performs a "blocking" iteration over all leases to acquire
   * one. Until a lease is acquired, this object cannot perform other tasks, so it's ok to block all
   * the other operations (including health checks)
   */
  private void acquireInitialLease() {
    var i = 0;
    var retryRound = 0;
    while (currentLease == null) {
      if (i % clusterSize == 0) {
        retryRound++;
        // wait a bit before retrying on all leases again.
        if (retryRound > 1) {
          try {
            currentDelay = backoff.supplyRetryDelay(currentDelay);
            LOG.debug(
                "Attempt to acquire the lease failed for all nodeIds, sleeping {} and retrying again",
                currentDelay);
            Thread.sleep(currentDelay);
          } catch (final InterruptedException e) {
            break;
          }
        }
      }
      final var nodeId = i++ % clusterSize;
      final var storedLease = nodeIdRepository.getLease(nodeId);
      currentLease = tryAcquire(storedLease);
    }
    if (currentLease != null) {
      LOG.info(
          "Acquired lease w/ nodeId={}.  {}", currentLease.lease().nodeInstance(), currentLease);
    } else {
      throw new IllegalStateException("Failed to acquire a lease");
    }
  }

  private StoredLease.Initialized tryAcquire(final StoredLease lease) {
    try {
      switch (lease) {
        case final Initialized initialized -> {
          if (initialized.lease().isStillValid(clock.millis(), leaseDuration)) {
            LOG.debug("Lease {} is held by another process, skipping it", initialized);
            return null;
          } else {
            LOG.debug("Trying to acquire an expired lease {}", initialized);
            final var newLease =
                (initialized.metadata().task().equals(taskId))
                    ? initialized.lease().renew(clock.millis(), leaseDuration)
                    : new Lease(
                        taskId,
                        clock.millis() + leaseDuration.toMillis(),
                        initialized.lease().nodeInstance());
            return nodeIdRepository.acquire(newLease, initialized.eTag());
          }
        }
        case final Uninitialized uninitialized -> {
          final var newLease =
              new Lease(taskId, clock.millis() + leaseDuration.toMillis(), uninitialized.node());
          LOG.debug(
              "Trying to take uninitialized lease: {} with new lease {}", uninitialized, newLease);
          return nodeIdRepository.acquire(newLease, uninitialized.eTag());
        }
      }
    } catch (final Exception e) {
      LOG.warn("Failed to acquire the lease {}", lease, e);
      return null;
    }
  }

  @Override
  public void close() throws Exception {
    // use the executor status to avoid closing multiple times
    if (!executor.isShutdown()) {
      try {
        if (renewalTask != null) {
          renewalTask.cancel(true);
        }
        if (currentLease != null) {
          nodeIdRepository.release(currentLease);
        }
      } finally {
        currentLease = null;
        // release is already submitted to the executor, we can shut it down gracefully.
        executor.shutdown();
        // shutdown is taking too much time, let's shut it down by interrupting running tasks.
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      }
    }
  }
}
