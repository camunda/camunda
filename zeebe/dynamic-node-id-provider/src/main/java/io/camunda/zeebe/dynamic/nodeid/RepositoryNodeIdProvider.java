/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease.Initialized;
import io.camunda.zeebe.util.ExponentialBackoffRetryDelay;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepositoryNodeIdProvider implements NodeIdProvider, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryNodeIdProvider.class);

  private final NodeIdRepository nodeIdRepository;
  private final InstantSource clock;
  private volatile StoredLease.Initialized currentLease;
  private final Duration leaseDuration;
  private final Duration readinessCheckTimeout;
  private final String taskId;
  private final Runnable onLeaseFailure;
  private final ScheduledExecutorService executor;
  private final ExponentialBackoffRetryDelay backoff;
  private final Duration renewalDelay;
  private ScheduledFuture<?> renewalTask;
  private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
  private VersionMappings knownVersionMappings = VersionMappings.empty();
  private final CompletableFuture<Boolean> previousNodeGracefullyShutdown =
      new CompletableFuture<>();
  private final CompletableFuture<Boolean> readinessFuture = new CompletableFuture<>();
  private final ExecutorService backgroundTaskExecutor;

  public RepositoryNodeIdProvider(
      final NodeIdRepository nodeIdRepository,
      final InstantSource clock,
      final Duration expiryDuration,
      final Duration leaseAcquireMaxDelay,
      final Duration readinessCheckTimeout,
      final String taskId,
      final Runnable onLeaseFailure) {
    this.nodeIdRepository =
        Objects.requireNonNull(nodeIdRepository, "nodeIdRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    leaseDuration = Objects.requireNonNull(expiryDuration, "expiryDuration cannot be null");
    this.readinessCheckTimeout =
        Objects.requireNonNull(readinessCheckTimeout, "readinessCheckTimeout cannot be null");
    this.taskId = Objects.requireNonNull(taskId, "taskId cannot be null");
    this.onLeaseFailure = Objects.requireNonNull(onLeaseFailure, "onLeaseFailure cannot be null");
    backoff = new ExponentialBackoffRetryDelay(leaseAcquireMaxDelay, Duration.ofSeconds(1));
    renewalDelay = leaseDuration.dividedBy(3);
    executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final var thread = new Thread(r, "NodeIdProvider");
              thread.setDaemon(true);
              return thread;
            });

    // Used for short tasks that need to be executed asynchronously without blocking the main lease
    // renewal
    backgroundTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @Override
  public CompletableFuture<Void> initialize(final int clusterSize) {
    final var availableNodeIdCount = nodeIdRepository.initialize(clusterSize);
    return CompletableFuture.runAsync(() -> acquireInitialLease(availableNodeIdCount), executor)
        .thenRun(this::startRenewalTimer)
        .thenRun(() -> scheduleReadinessCheck(clusterSize));
  }

  @Override
  public CompletableFuture<Void> scale(final int newClusterSize) {
    return CompletableFuture.runAsync(
        () -> nodeIdRepository.scale(newClusterSize), backgroundTaskExecutor);
  }

  @Override
  public NodeInstance currentNodeInstance() {
    return getCurrentLease().lease().nodeInstance();
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
  @Override
  public CompletableFuture<Boolean> isValid() {
    final var now = clock.millis();
    return CompletableFuture.supplyAsync(
            () -> currentLease != null && currentLease.lease().isStillValid(now), executor)
        .orTimeout(leaseDuration.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(
            t -> {
              LOG.warn("Failed to check the status of the lease. Marking it as failed", t);
              return false;
            });
  }

  @Override
  public CompletableFuture<Boolean> previousNodeGracefullyShutdown() {
    return previousNodeGracefullyShutdown;
  }

  @Override
  public void setMembers(final Set<Member> currentMembers) {
    executor.execute(() -> updateVersionMappings(currentMembers));
  }

  @Override
  public CompletableFuture<Boolean> awaitReadiness() {
    return readinessFuture;
  }

  private void scheduleReadinessCheck(final int clusterSize) {
    if (!previousNodeGracefullyShutdown.isDone()) {
      throw new IllegalStateException(
          "Readiness check cannot be scheduled until the lease is acquired");
    }
    if (previousNodeGracefullyShutdown.join()) {
      // if the previous node shut down gracefully, we can consider ourselves ready immediately
      readinessFuture.complete(true);
      return;
    }

    new RepositoryNodeIdProviderReadinessChecker(
            clusterSize,
            currentLease.node(),
            nodeIdRepository,
            Duration.ofSeconds(5),
            readinessCheckTimeout)
        .waitUntilAllNodesAreUpToDate()
        .exceptionally(
            t -> {
              // This should never happen as the checker should wait indefinitely until timeout
              // until the nodes are up-to-date
              LOG.warn(
                  "Failed to verify that all nodes are up to date. Marking readiness as failed.",
                  t);
              return false;
            })
        .thenAccept(readinessFuture::complete);
  }

  private void startRenewalTimer() {
    renewalTask =
        executor.scheduleAtFixedRate(
            this::renew, renewalDelay.toMillis(), renewalDelay.toMillis(), TimeUnit.MILLISECONDS);
  }

  public Initialized getCurrentLease() {
    return currentLease;
  }

  private void renew() {
    if (currentLease == null) {
      LOG.warn(
          "No current lease found, skipping renew. The process is shutting down already {}",
          shutdownInitiated.get());
    }
    try {
      final var newLease =
          currentLease.lease().renew(clock.millis(), leaseDuration, knownVersionMappings);
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
  private void acquireInitialLease(final int clusterSize) {
    if (currentLease != null) {
      throw new IllegalStateException(
          "Expected to acquire initial lease, but lease is already acquired: " + currentLease);
    }
    var i = 0;
    var retryRound = 0;
    NodeIdRepository.StoredLease storedLease = null;
    while (currentLease == null) {
      if (i % clusterSize == 0) {
        retryRound++;
        // wait a bit before retrying on all leases again.
        if (retryRound > 1) {
          try {
            final var currentDelay = backoff.nextDelay();
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
      storedLease = nodeIdRepository.getLease(nodeId);
      currentLease = tryAcquireInitialLease(storedLease);
    }
    if (currentLease != null) {
      LOG.info(
          "Acquired lease w/ nodeId={}.  {}", currentLease.lease().nodeInstance(), currentLease);
      // storedLease should always be non-null as currentLease is not null.
      if (storedLease != null) {
        previousNodeGracefullyShutdown.complete(!storedLease.isInitialized());
      }
      backoff.reset();
    } else {
      throw new IllegalStateException("Failed to acquire a lease");
    }
  }

  private StoredLease.Initialized tryAcquireInitialLease(final StoredLease lease) {
    try {
      final var newLease = lease.acquireInitialLease(taskId, clock, leaseDuration);
      return newLease.map(value -> nodeIdRepository.acquire(value, lease.eTag())).orElse(null);
    } catch (final Exception e) {
      LOG.warn("Failed to acquire the lease {}", lease, e);
      return null;
    }
  }

  private void updateVersionMappings(final Set<Member> currentMembers) {
    final var nodeInstances =
        currentMembers.stream()
            .filter(m -> isBroker(m.id()))
            .map(m -> Map.entry(Integer.parseInt(m.id().id()), Version.of(m.nodeVersion())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    knownVersionMappings = new VersionMappings(nodeInstances);
  }

  private boolean isBroker(final MemberId memberId) {
    // TODO improve the way we identify brokers vs gateways
    try {
      final var id = Integer.parseInt(memberId.id());
      return id >= 0;
    } catch (final NumberFormatException e) {
      return false;
    }
  }

  @Override
  public void close() throws Exception {
    // use the executor status to avoid closing multiple times
    if (!executor.isShutdown() && !shutdownInitiated.compareAndExchange(false, true)) {
      LOG.debug("Shutting down RepositoryNodeIdProvider");
      final var shutdownFuture = executor.submit(this::closeInternal);
      // closeInternal is already submitted to the executor, we can shut it down gracefully.
      executor.shutdown();
      try {
        shutdownFuture.get(5, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.warn("Failed to gracefully shutdown NodeIdProvider, interrupting.", e);
      } finally {
        // shutdown is taking too much time, let's shut it down by interrupting running tasks.
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      }
    }

    if (!backgroundTaskExecutor.isShutdown()) {
      backgroundTaskExecutor.shutdown();
      if (!backgroundTaskExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
        backgroundTaskExecutor.shutdownNow();
      }
    }
  }

  // To be run in the executor
  private void closeInternal() {
    try {
      if (renewalTask != null) {
        renewalTask.cancel(true);
      }
      if (currentLease != null && currentLease.lease().isStillValid(clock.millis())) {
        nodeIdRepository.release(currentLease);
      }
    } finally {
      currentLease = null;
    }
  }
}
