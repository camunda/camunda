/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationFailed;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationTimedOut;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;

/**
 * Waits until objects have been replicated by the bucket's own replication (S3 replication or MinIO
 * bucket replication), as reported by the {@code x-amz-replication-status} of each object on the
 * source bucket. Replication itself stays asynchronous and is managed by the object store; this
 * only observes it.
 *
 * <p>Polling never blocks a thread: each round issues HEAD requests for the objects that are still
 * pending, a bounded number at a time, and schedules the next round on a delayed executor.
 */
final class PollingReplicationAwaiter implements ReplicationAwaiter {

  private static final Logger LOG = LoggerFactory.getLogger(PollingReplicationAwaiter.class);
  private static final int MAX_KEYS_IN_MESSAGE = 10;
  // Time given to the single check made when the deadline has already passed
  private static final Duration FINAL_CHECK_GRACE = Duration.ofSeconds(5);

  private final S3AsyncClient client;
  private final String bucketName;
  private final Duration pollInterval;
  private final Duration timeout;
  private final int maxInFlight;

  PollingReplicationAwaiter(final S3AsyncClient client, final S3BackupConfig config) {
    this.client = client;
    bucketName = config.bucketName();
    pollInterval = config.replicationPollInterval();
    timeout = config.replicationTimeout();
    // Same share of the connection pool as uploads, see FileSetManager
    maxInFlight = Math.max(1, config.maxConcurrentConnections() / 2);
  }

  @Override
  public Instant deadline() {
    return Instant.now().plus(timeout);
  }

  /**
   * Completes once every given object reports that it was replicated. Completes exceptionally with
   * {@link BackupReplicationFailed} as soon as one object reports a failed replication or no
   * replication status, and with {@link BackupReplicationTimedOut} if some objects are still
   * pending at the deadline.
   */
  @Override
  public CompletableFuture<Void> awaitReplication(
      final Collection<String> keys, final Instant deadline) {
    final var startedAt = Instant.now();
    LOG.debug("Waiting for replication of {} objects in bucket {}", keys.size(), bucketName);
    final var wait = new ReplicationWait(Set.copyOf(keys));
    return wait.start(deadline)
        .whenComplete(
            (ignored, error) -> {
              if (error == null) {
                LOG.debug(
                    "Replication of {} objects completed after {}",
                    keys.size(),
                    Duration.between(startedAt, Instant.now()));
              }
            });
  }

  /** Empty if the object has no replication status, because no replication rule covers it. */
  private CompletableFuture<Optional<ReplicationStatus>> replicationStatus(final String key) {
    return client
        .headObject(req -> req.bucket(bucketName).key(key))
        .thenApply(response -> Optional.ofNullable(response.replicationStatus()));
  }

  /**
   * A completed manifest is reported by its own replication status: in progress while it is
   * pending, failed if its replication failed. The backup only counts as completed once the replica
   * site has its completed manifest too. Manifests without a replication status, for example ones
   * written before replication was set up, are reported as they are.
   */
  @Override
  public BackupStatus reportedStatus(
      final BackupStatus status, final Optional<ReplicationStatus> replicationStatus) {
    if (status.statusCode() != BackupStatusCode.COMPLETED) {
      return status;
    }
    return switch (ReplicationState.classify(replicationStatus)) {
      case REPLICATED, UNTRACKED -> status;
      case PENDING -> withStatus(status, BackupStatusCode.IN_PROGRESS, Optional.empty());
      case FAILED ->
          withStatus(
              status,
              BackupStatusCode.FAILED,
              Optional.of("The backup's manifest failed to replicate to the replica site."));
    };
  }

  private static BackupStatus withStatus(
      final BackupStatus status,
      final BackupStatusCode statusCode,
      final Optional<String> failureReason) {
    return new BackupStatusImpl(
        status.id(),
        status.descriptor(),
        statusCode,
        failureReason,
        status.created(),
        status.lastModified());
  }

  private BackupReplicationFailed replicationFailed(final String key) {
    return new BackupReplicationFailed(
        "Replication of object '%s' in bucket '%s' failed.".formatted(key, bucketName));
  }

  private BackupReplicationFailed notTracked(final String key) {
    return new BackupReplicationFailed(
        ("Object '%s' in bucket '%s' has no replication status. Check that the bucket's "
                + "replication rules cover all backup objects.")
            .formatted(key, bucketName));
  }

  private static Duration max(final Duration a, final Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  private BackupReplicationTimedOut timedOut(final Set<String> pending) {
    final var sample = pending.stream().sorted().limit(MAX_KEYS_IN_MESSAGE).toList();
    return new BackupReplicationTimedOut(
        "Timed out after %s waiting for replication of %d objects in bucket '%s', for example %s"
            .formatted(timeout, pending.size(), bucketName, sample));
  }

  /**
   * One wait for a set of objects. Each round checks the objects that are still pending, with at
   * most {@link #maxInFlight} requests at a time so the checks share the connection pool with
   * uploads instead of queuing behind it. Every response is inspected as it arrives, so a failed
   * replication or request ends the wait right away. The deadline is enforced by its own timer,
   * independently of requests that are still outstanding.
   */
  private final class ReplicationWait {
    private final CompletableFuture<Void> result = new CompletableFuture<>();
    private final Set<String> keys;
    // Objects not reported as replicated yet, updated as each response arrives
    private final Set<String> notReplicated = ConcurrentHashMap.newKeySet();

    private ReplicationWait(final Set<String> keys) {
      this.keys = keys;
      notReplicated.addAll(keys);
    }

    private CompletableFuture<Void> start(final Instant deadline) {
      final var now = Instant.now();
      // If the deadline has already passed, check once more instead of failing without looking:
      // the manifest's wait shares the deadline of the contents and may start right at it.
      final var enforcedDeadline =
          deadline.isAfter(now) ? deadline : now.plus(max(pollInterval, FINAL_CHECK_GRACE));
      round(keys);
      final var untilDeadline =
          max(Duration.between(Instant.now(), enforcedDeadline), Duration.ZERO);
      // The timer starts after the first round's checks are sent, so checks that answer at once
      // are counted before it can fire. It is cancelled when the wait completes.
      return result
          .orTimeout(untilDeadline.toNanos(), TimeUnit.NANOSECONDS)
          .exceptionallyCompose(
              error ->
                  CompletableFuture.failedFuture(
                      error instanceof TimeoutException
                          ? timedOut(Set.copyOf(notReplicated))
                          : error));
    }

    private void round(final Set<String> keys) {
      if (result.isDone()) {
        return;
      }
      if (keys.isEmpty()) {
        result.complete(null);
        return;
      }
      final var queue = new ConcurrentLinkedQueue<>(keys);
      final Set<String> stillPending = ConcurrentHashMap.newKeySet();
      final var outstanding = new AtomicInteger(keys.size());
      for (int i = 0; i < Math.min(maxInFlight, keys.size()); i++) {
        checkNext(queue, stillPending, outstanding);
      }
    }

    private void checkNext(
        final Queue<String> queue,
        final Set<String> stillPending,
        final AtomicInteger outstanding) {
      final var key = queue.poll();
      if (key == null || result.isDone()) {
        return;
      }
      replicationStatus(key)
          .whenComplete(
              (status, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                  return;
                }
                final var state = ReplicationState.classify(status);
                if (state == ReplicationState.FAILED) {
                  result.completeExceptionally(replicationFailed(key));
                  return;
                }
                if (state == ReplicationState.UNTRACKED) {
                  result.completeExceptionally(notTracked(key));
                  return;
                }
                if (state == ReplicationState.PENDING) {
                  stillPending.add(key);
                } else {
                  notReplicated.remove(key);
                }
                if (outstanding.decrementAndGet() == 0) {
                  roundCompleted(Set.copyOf(stillPending));
                } else {
                  checkNext(queue, stillPending, outstanding);
                }
              });
    }

    private void roundCompleted(final Set<String> stillPending) {
      if (stillPending.isEmpty()) {
        result.complete(null);
        return;
      }
      CompletableFuture.delayedExecutor(pollInterval.toNanos(), TimeUnit.NANOSECONDS)
          .execute(() -> round(stillPending));
    }
  }
}
