/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering.mapper;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeIdMapper {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdMapper.class);
  private final S3Lease lease;
  private final ScheduledExecutorService executor;
  private int brokerId;

  public NodeIdMapper(final S3LeaseConfig config, final int clusterSize) {
    executor = newSingleThreadScheduledExecutor();
    lease = new S3Lease(config, UUID.randomUUID().toString().substring(0, 6), clusterSize);
  }

  public int start() {
    final var brokerId = busyAcquireLease();
    scheduleRenewal(brokerId);
    this.brokerId = brokerId;
    return brokerId;
  }

  public void shutdown() {
    executor.shutdown();
    lease.releaseLease(brokerId);
  }

  private int busyAcquireLease() {
    final var future = new CompletableFuture<Integer>();
    scheduleBusyLease(future);
    return future.join();
  }

  private void scheduleBusyLease(final CompletableFuture future) {
    executor.schedule(
        () -> {
          final int brokerId = lease.acquireLease();
          if (brokerId < 0) {
            scheduleBusyLease(future);
          } else {
            LOG.info("Lease acquired for brokerId: {}", brokerId);
            future.complete(brokerId);
          }
        },
        10,
        TimeUnit.SECONDS);
  }

  private void scheduleRenewal(final int brokerId) {
    executor.schedule(
        () -> {
          if (lease.renewLease(brokerId)) {
            scheduleRenewal(brokerId);
          } else {
            LOG.info("Renewal lease not acquired");
            Runtime.getRuntime().halt(1);
          }
        },
        10,
        TimeUnit.SECONDS);
  }
}
