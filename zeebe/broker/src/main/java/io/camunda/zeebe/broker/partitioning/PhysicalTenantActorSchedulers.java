/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The actor schedulers on which the partition actors of the physical tenants run.
 *
 * <p>Depending on the configuration, either all tenants share the broker-wide scheduler, or each
 * tenant has a scheduler of its own, so that its partition actors cannot take the actor threads of
 * other tenants. A dedicated scheduler is created when the first actor of its tenant is submitted,
 * so tenants without partitions on this broker do not own threads. It lives until {@link
 * #release(String)} is called for the tenant, or until this instance is closed.
 */
@NullMarked
public final class PhysicalTenantActorSchedulers implements CloseableSilently {

  private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalTenantActorSchedulers.class);

  private final @Nullable ActorScheduler sharedScheduler;
  private final @Nullable Function<String, ActorScheduler> schedulerFactory;
  private final Map<String, ActorScheduler> dedicatedSchedulers = new ConcurrentHashMap<>();

  private PhysicalTenantActorSchedulers(
      final @Nullable ActorScheduler sharedScheduler,
      final @Nullable Function<String, ActorScheduler> schedulerFactory) {
    this.sharedScheduler = sharedScheduler;
    this.schedulerFactory = schedulerFactory;
  }

  /** All tenants use the given scheduler. Closing this instance does not close it. */
  public static PhysicalTenantActorSchedulers shared(final ActorScheduler scheduler) {
    return new PhysicalTenantActorSchedulers(scheduler, null);
  }

  /**
   * Each tenant uses its own scheduler, which this instance starts and closes.
   *
   * @param schedulerFactory builds, but does not start, the scheduler of the given tenant id
   */
  public static PhysicalTenantActorSchedulers dedicated(
      final Function<String, ActorScheduler> schedulerFactory) {
    return new PhysicalTenantActorSchedulers(null, schedulerFactory);
  }

  /** Returns the service to submit the partition actors of the given tenant to. */
  public ActorSchedulingService schedulingService(final String physicalTenantId) {
    if (sharedScheduler != null) {
      return sharedScheduler;
    }

    return new ActorSchedulingService() {
      @Override
      public ActorFuture<Void> submitActor(final Actor actor) {
        return schedulerOf(physicalTenantId).submitActor(actor);
      }

      @Override
      public ActorFuture<Void> submitActor(
          final Actor actor, final SchedulingHints schedulingHints) {
        return schedulerOf(physicalTenantId).submitActor(actor, schedulingHints);
      }
    };
  }

  /**
   * Stops the dedicated scheduler of the given tenant, if it has one. Must be called after all
   * actors submitted through {@link #schedulingService(String)} are closed.
   *
   * @return a future that completes when the threads of the scheduler have stopped
   */
  public CompletableFuture<Void> release(final String physicalTenantId) {
    final var scheduler = dedicatedSchedulers.remove(physicalTenantId);
    if (scheduler == null) {
      return CompletableFuture.completedFuture(null);
    }

    // closing blocks until the scheduler threads stop, which must not block the caller
    return CompletableFuture.runAsync(() -> closeScheduler(scheduler));
  }

  @Override
  public void close() {
    dedicatedSchedulers.keySet().forEach(id -> release(id).join());
  }

  private ActorScheduler schedulerOf(final String physicalTenantId) {
    final var factory = Objects.requireNonNull(schedulerFactory);
    return dedicatedSchedulers.computeIfAbsent(
        physicalTenantId,
        id -> {
          final var scheduler = factory.apply(id);
          scheduler.start();
          return scheduler;
        });
  }

  private static void closeScheduler(final ActorScheduler scheduler) {
    try {
      scheduler.close();
    } catch (final Exception e) {
      LOGGER.warn("Failed to stop the actor scheduler of a physical tenant", e);
    }
  }
}
