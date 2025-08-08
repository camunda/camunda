/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.Closeable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeIdMapper implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdMapper.class);
  private final S3Lease lease;
  private final ScheduledExecutorService executor;
  private int brokerId;
  private final String taskId;

  public NodeIdMapper(final S3LeaseConfig config, final int clusterSize) {
    executor = newSingleThreadScheduledExecutor();
    taskId = UUID.randomUUID().toString().substring(0, 6);
    lease = new S3Lease(config, taskId, clusterSize);
  }

  public boolean isHealthy() {
    final ScheduledFuture<Boolean> future = executor.schedule(() -> true, 0, TimeUnit.SECONDS);
    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }

  public int start() {
    final var brokerId = acquireLease();
    scheduleRenewal(brokerId);
    this.brokerId = brokerId;
    return brokerId;
  }

  @Override
  public void close() {
    executor.shutdown();
    lease.releaseLease(brokerId);
  }

  private int acquireLease() {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return busyAcquireLease();
              } catch (final Exception e) {
                LOG.error("Failed to acquire lease: {}", e.getMessage());
                throw new CompletionException(e);
              }
            },
            executor)
        .join();
  }

  private int busyAcquireLease() throws Exception {
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
          final var acquiredId = lease.acquireLease();
          if (acquiredId >= 0) {
            LOG.info("Lease acquired for brokerId: {} by task: {}", acquiredId, taskId);
          }
          return acquiredId;
        });
  }

  private void scheduleRenewal(final int brokerId) {
    executor.schedule(
        () -> {
          if (lease.renewLease(brokerId)) {
            scheduleRenewal(brokerId);
          } else {
            LOG.info("Renewal lease not acquired");
            if (executor.isShutdown() || executor.isTerminated()) {
              return;
            }
            Runtime.getRuntime().halt(1);
          }
        },
        S3Lease.LEASE_EXPIRY_SECONDS / 6, // 10sec
        TimeUnit.SECONDS);
  }
}
