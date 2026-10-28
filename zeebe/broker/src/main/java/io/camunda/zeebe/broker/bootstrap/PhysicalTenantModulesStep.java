/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.PhysicalTenantModules;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Owns the partition manager of every physical tenant on this broker. At startup it starts one per
 * boot tenant, in order; while the broker runs it starts and stops whole tenant modules, see {@link
 * PhysicalTenantModules}; at shutdown it stops every partition manager it owns, including the ones
 * of tenants added at runtime.
 */
@NullMarked
final class PhysicalTenantModulesStep extends AbstractBrokerStartupStep
    implements PhysicalTenantModules {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  // only accessed on the broker startup actor
  private final Map<String, PartitionManagerStep> partitionManagerSteps = new LinkedHashMap<>();
  private volatile Set<String> running = Set.of();
  private volatile @Nullable BrokerStartupContext context;

  @Override
  public String getName() {
    return "Physical Tenant Modules";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    context = brokerStartupContext;
    final var bootTenants = List.copyOf(brokerStartupContext.getPhysicalTenantIds().known());
    startSequentially(
        brokerStartupContext,
        concurrencyControl,
        bootTenants.iterator(),
        proceed(
            () -> {
              brokerStartupContext.setPhysicalTenantModules(this);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    brokerShutdownContext.setPhysicalTenantModules(null);
    final var reverse = new ArrayList<>(partitionManagerSteps.keySet());
    Collections.reverse(reverse);
    stopSequentially(
        brokerShutdownContext,
        concurrencyControl,
        reverse.iterator(),
        null,
        error -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  @Override
  public CompletableFuture<Void> start(
      final String physicalTenantId, final PhysicalTenantContext tenantContext) {
    return onStartupActor(
        (ctx, concurrencyControl) ->
            startModule(ctx, concurrencyControl, physicalTenantId, tenantContext));
  }

  @Override
  public CompletableFuture<Void> stop(final String physicalTenantId) {
    return onStartupActor(
        (ctx, concurrencyControl) -> stopModule(ctx, concurrencyControl, physicalTenantId));
  }

  @Override
  public CompletableFuture<Void> awaitReady(final String physicalTenantId) {
    return onStartupActor(
        (ctx, concurrencyControl) -> {
          final var healthCheckService = ctx.getHealthCheckService();
          return healthCheckService
              .awaitPhysicalTenantReady(physicalTenantId)
              .thenApply(
                  ignored -> {
                    healthCheckService.expectPhysicalTenant(physicalTenantId);
                    return null;
                  },
                  concurrencyControl);
        });
  }

  @Override
  public Set<String> running() {
    return running;
  }

  private ActorFuture<@Nullable Void> startModule(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final String physicalTenantId,
      final PhysicalTenantContext tenantContext) {
    if (partitionManagerSteps.containsKey(physicalTenantId)) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException(
              "The module of physical tenant '%s' already runs".formatted(physicalTenantId)));
    }
    LOG.info("Starting the module of physical tenant '{}'", physicalTenantId);
    ctx.putPhysicalTenantContext(physicalTenantId, tenantContext);
    final ActorFuture<@Nullable Void> result = concurrencyControl.createFuture();
    final var started =
        JobStreamServiceStep.startTenantService(
                ctx, physicalTenantId, ctx.getActorSchedulingService(), concurrencyControl)
            .andThen(
                ignored -> startPartitionManager(ctx, concurrencyControl, physicalTenantId),
                concurrencyControl)
            .andThen(
                ignored ->
                    BrokerAdminServiceStep.startTenantService(
                        ctx, physicalTenantId, concurrencyControl),
                concurrencyControl)
            .andThen(
                ignored ->
                    ctx.getClusterConfigurationService()
                        .provisionPhysicalTenant(ctx, physicalTenantId),
                concurrencyControl);
    concurrencyControl.runOnCompletion(
        started,
        (ok, error) -> {
          if (error == null) {
            result.complete(null);
            return;
          }
          LOG.warn(
              "Failed to start the module of physical tenant '{}', stopping what started",
              physicalTenantId,
              error);
          concurrencyControl.runOnCompletion(
              stopModule(ctx, concurrencyControl, physicalTenantId),
              (stopped, stopError) -> result.completeExceptionally(error));
        });
    return result;
  }

  private ActorFuture<@Nullable Void> startPartitionManager(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final String physicalTenantId) {
    final var step = new PartitionManagerStep(physicalTenantId);
    partitionManagerSteps.put(physicalTenantId, step);
    running = Set.copyOf(partitionManagerSteps.keySet());
    return step.startup(ctx).thenApply(ignored -> null, concurrencyControl);
  }

  private ActorFuture<@Nullable Void> stopModule(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final String physicalTenantId) {
    LOG.info("Stopping the module of physical tenant '{}'", physicalTenantId);
    final var healthCheckService = ctx.getHealthCheckService();
    healthCheckService.releasePhysicalTenant(physicalTenantId);
    final ActorFuture<@Nullable Void> result = concurrencyControl.createFuture();
    final var stopped =
        BrokerAdminServiceStep.shutdownTenantService(ctx, physicalTenantId, concurrencyControl)
            .andThen(
                ignored -> stopPartitionManager(ctx, concurrencyControl, physicalTenantId),
                concurrencyControl)
            .andThen(
                ignored ->
                    JobStreamServiceStep.shutdownTenantService(
                        ctx, physicalTenantId, concurrencyControl),
                concurrencyControl);
    concurrencyControl.runOnCompletion(
        stopped,
        (ok, error) -> {
          healthCheckService.unregisterPhysicalTenant(physicalTenantId);
          ctx.removePhysicalTenantContext(physicalTenantId);
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  private ActorFuture<@Nullable Void> stopPartitionManager(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final String physicalTenantId) {
    final var step = partitionManagerSteps.remove(physicalTenantId);
    running = Set.copyOf(partitionManagerSteps.keySet());
    if (step == null) {
      return CompletableActorFuture.completed(null);
    }
    return step.shutdown(ctx).thenApply(ignored -> null, concurrencyControl);
  }

  private void startSequentially(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final Iterator<String> tenants,
      final BiConsumer<Void, Throwable> onDone) {
    if (!tenants.hasNext()) {
      onDone.accept(null, null);
      return;
    }
    final var physicalTenantId = tenants.next();
    concurrencyControl.runOnCompletion(
        startPartitionManager(ctx, concurrencyControl, physicalTenantId),
        (ok, error) -> {
          if (error != null) {
            onDone.accept(null, error);
          } else {
            startSequentially(ctx, concurrencyControl, tenants, onDone);
          }
        });
  }

  private void stopSequentially(
      final BrokerStartupContext ctx,
      final ConcurrencyControl concurrencyControl,
      final Iterator<String> tenants,
      final @Nullable Throwable firstError,
      final Consumer<@Nullable Throwable> onDone) {
    if (!tenants.hasNext()) {
      onDone.accept(firstError);
      return;
    }
    final var physicalTenantId = tenants.next();
    concurrencyControl.runOnCompletion(
        stopPartitionManager(ctx, concurrencyControl, physicalTenantId),
        (ok, error) ->
            stopSequentially(
                ctx, concurrencyControl, tenants, firstError != null ? firstError : error, onDone));
  }

  private CompletableFuture<Void> onStartupActor(
      final BiFunction<BrokerStartupContext, ConcurrencyControl, ActorFuture<@Nullable Void>>
          call) {
    final var result = new CompletableFuture<Void>();
    final var ctx = context;
    if (ctx == null || ctx.getPhysicalTenantModules() != this) {
      result.completeExceptionally(
          new IllegalStateException("The physical tenant modules are not running"));
      return result;
    }
    final var concurrencyControl = ctx.getConcurrencyControl();
    concurrencyControl.run(
        () -> {
          final ActorFuture<@Nullable Void> future;
          try {
            future = call.apply(ctx, concurrencyControl);
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
            return;
          }
          concurrencyControl.runOnCompletion(
              future,
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(null);
                }
              });
        });
    return result;
  }
}
