/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationFailed;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationTimedOut;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.ConfigurationException;
import io.camunda.zeebe.backup.s3.util.CompletableFutureUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetBucketReplicationResponse;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Waits until objects have been replicated by the bucket's own replication (S3 replication or MinIO
 * bucket replication), as reported by the {@code x-amz-replication-status} of each object on the
 * source bucket. Replication itself stays asynchronous and is managed by the object store; this
 * only observes it.
 *
 * <p>Polling never blocks a thread: each round issues HEAD requests for the objects that are still
 * pending and schedules the next round on a delayed executor.
 */
final class ReplicationAwaiter {

  static final String REPLICATION_CONFIGURATION_NOT_FOUND = "ReplicationConfigurationNotFoundError";
  private static final Logger LOG = LoggerFactory.getLogger(ReplicationAwaiter.class);
  private static final int MAX_KEYS_IN_MESSAGE = 10;

  private final S3AsyncClient client;
  private final String bucketName;
  private final Duration pollInterval;
  private final Duration timeout;
  private volatile boolean replicationConfigured;

  ReplicationAwaiter(final S3AsyncClient client, final S3BackupConfig config) {
    this.client = client;
    bucketName = config.bucketName();
    pollInterval = config.replicationPollInterval();
    timeout = config.replicationTimeout();
  }

  /** Returns the point in time after which {@link #awaitReplication} gives up. */
  Instant deadline() {
    return Instant.now().plus(timeout);
  }

  /**
   * Completes successfully if the bucket has at least one replication rule. Without replication, no
   * object ever reports a replication status, so waiting for one would only end with the timeout. A
   * successful check is remembered; a failed one is repeated on the next call.
   */
  CompletableFuture<Void> verifyReplicationConfigured() {
    if (replicationConfigured) {
      return CompletableFuture.completedFuture(null);
    }
    return client
        .getBucketReplication(req -> req.bucket(bucketName))
        .handle(
            (final GetBucketReplicationResponse response, final @Nullable Throwable error) -> {
              if (error != null) {
                throw replicationCheckFailed(unwrap(error));
              }
              final var configuration = response.replicationConfiguration();
              if (configuration == null || !configuration.hasRules()) {
                throw noReplicationConfigured(null);
              }
              replicationConfigured = true;
              return null;
            });
  }

  /**
   * Completes once every given object reports that it was replicated. Completes exceptionally with
   * {@link BackupReplicationFailed} as soon as one object reports a failed replication or no
   * replication status, and with {@link BackupReplicationTimedOut} if some objects are still
   * pending at the deadline.
   */
  CompletableFuture<Void> awaitReplication(final Collection<String> keys, final Instant deadline) {
    final var result = new CompletableFuture<Void>();
    final var startedAt = Instant.now();
    LOG.debug("Waiting for replication of {} objects in bucket {}", keys.size(), bucketName);
    poll(Set.copyOf(keys), deadline, result);
    return result.whenComplete(
        (ignored, error) -> {
          if (error == null) {
            LOG.debug(
                "Replication of {} objects completed after {}",
                keys.size(),
                Duration.between(startedAt, Instant.now()));
          }
        });
  }

  private void poll(
      final Set<String> pending, final Instant deadline, final CompletableFuture<Void> result) {
    CompletableFutureUtils.mapAsync(pending, this::replicationStatus)
        .whenComplete(
            (statuses, error) -> {
              if (error != null) {
                result.completeExceptionally(unwrap(error));
                return;
              }
              try {
                final var stillPending = stillPending(statuses);
                if (stillPending.isEmpty()) {
                  result.complete(null);
                  return;
                }
                final var now = Instant.now();
                if (!now.isBefore(deadline)) {
                  result.completeExceptionally(timedOut(stillPending));
                  return;
                }
                // Poll once more right at the deadline rather than overshooting it by a full
                // interval, so that a short timeout is honored precisely.
                final var untilDeadline = Duration.between(now, deadline);
                final var delay =
                    untilDeadline.compareTo(pollInterval) < 0 ? untilDeadline : pollInterval;
                CompletableFuture.delayedExecutor(delay.toNanos(), TimeUnit.NANOSECONDS)
                    .execute(() -> poll(stillPending, deadline, result));
              } catch (final RuntimeException e) {
                result.completeExceptionally(e);
              }
            });
  }

  /** Empty if the object has no replication status, because no replication rule covers it. */
  private CompletableFuture<Optional<ReplicationStatus>> replicationStatus(final String key) {
    return client
        .headObject(req -> req.bucket(bucketName).key(key))
        .thenApply(response -> Optional.ofNullable(response.replicationStatus()));
  }

  private Set<String> stillPending(final Map<String, Optional<ReplicationStatus>> statuses) {
    final var stillPending = new HashSet<String>();
    for (final var entry : statuses.entrySet()) {
      final var key = entry.getKey();
      final var status = entry.getValue().orElse(null);
      if (status == null) {
        throw new BackupReplicationFailed(
            ("Object '%s' in bucket '%s' has no replication status. Check that the bucket's "
                    + "replication rules cover all backup objects.")
                .formatted(key, bucketName));
      }
      switch (status) {
        // Both spellings are in use across S3 and MinIO versions. A REPLICA was written at
        // another site and replicated here, so it exists there too.
        case COMPLETED, COMPLETE, REPLICA -> {}
        case FAILED ->
            throw new BackupReplicationFailed(
                "Replication of object '%s' in bucket '%s' failed.".formatted(key, bucketName));
        // PENDING, or a status this SDK version does not know: keep waiting until the deadline
        default -> stillPending.add(key);
      }
    }
    return stillPending;
  }

  private BackupReplicationTimedOut timedOut(final Set<String> pending) {
    final var sample = pending.stream().sorted().limit(MAX_KEYS_IN_MESSAGE).toList();
    return new BackupReplicationTimedOut(
        "Timed out after %s waiting for replication of %d objects in bucket '%s', for example %s"
            .formatted(timeout, pending.size(), bucketName, sample));
  }

  private ConfigurationException replicationCheckFailed(final Throwable error) {
    if (error instanceof final S3Exception s3Exception
        && s3Exception.awsErrorDetails() != null
        && REPLICATION_CONFIGURATION_NOT_FOUND.equals(s3Exception.awsErrorDetails().errorCode())) {
      return noReplicationConfigured(error);
    }
    return new ConfigurationException(
        ("Failed to read the replication configuration of bucket '%s', which is required to "
                + "wait for replication")
            .formatted(bucketName),
        error);
  }

  private ConfigurationException noReplicationConfigured(final @Nullable Throwable cause) {
    return new ConfigurationException(
        ("The S3 backup store is configured to wait for replication, but bucket '%s' has no "
                + "replication configured")
            .formatted(bucketName),
        cause);
  }

  private static Throwable unwrap(final Throwable error) {
    return error instanceof CompletionException && error.getCause() != null
        ? error.getCause()
        : error;
  }
}
