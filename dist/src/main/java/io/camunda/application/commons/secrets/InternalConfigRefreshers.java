/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.application.commons.secrets.PhysicalTenantSecretStores.Owned;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One {@link InternalConfigRefresher} per physical tenant, each run at the refresh interval of its
 * tenant on one shared thread. A run is a read of the store and, rarely, a client swap, so one
 * thread serves many tenants; a slow store or a long drain delays the next run of the others.
 *
 * <p>The set of refreshers follows the tenants of the node: {@link #start} adds the refresher of a
 * tenant, or re-points the existing one to the store and interval of the tenant's new
 * configuration, and {@link #stop} closes it. Both run on the refresh thread, so they never overlap
 * a run of a refresher.
 */
public final class InternalConfigRefreshers implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(InternalConfigRefreshers.class);

  private final Map<String, Tenant> byPhysicalTenant = new ConcurrentHashMap<>();
  private final SecretRefreshHealth health;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("internal-config-refresh").daemon().factory());

  private InternalConfigRefreshers(final SecretRefreshHealth health) {
    this.health = health;
  }

  public static InternalConfigRefreshers of(
      final InternalConfigSecrets secrets,
      final Map<String, Duration> intervals,
      final SecretRefreshHealth health) {
    final var refreshers = new InternalConfigRefreshers(health);
    intervals.forEach(
        (tenantId, interval) ->
            refreshers.start(
                tenantId, Owned.borrowed(secrets.forPhysicalTenant(tenantId)), interval));
    return refreshers;
  }

  /** No tenant refreshes, for an application that does not build the internal-config stores. */
  public static InternalConfigRefreshers none() {
    return new InternalConfigRefreshers(SecretRefreshHealth.IGNORED);
  }

  public Optional<InternalConfigRefresher> forPhysicalTenant(final String physicalTenantId) {
    return Optional.ofNullable(byPhysicalTenant.get(physicalTenantId)).map(Tenant::refresher);
  }

  /**
   * Starts refreshing a tenant from the given store at the given interval. If the tenant already
   * has a refresher, it keeps its consumers and is re-pointed, and the previous store is released.
   *
   * @param store the tenant's internal-config store; this takes over releasing it
   */
  public void start(
      final String physicalTenantId,
      final Owned<InternalConfigSecretResolver> store,
      final Duration interval) {
    onRefreshThread(
        () -> {
          final var previous = byPhysicalTenant.get(physicalTenantId);
          final InternalConfigRefresher refresher;
          if (previous == null) {
            refresher = new InternalConfigRefresher(store.value(), health);
          } else {
            previous.schedule().cancel(false);
            refresher = previous.refresher();
            refresher.repoint(store.value());
            release(physicalTenantId, previous.store());
          }
          final var schedule =
              scheduler.scheduleWithFixedDelay(
                  refresher::refresh,
                  interval.toMillis(),
                  interval.toMillis(),
                  TimeUnit.MILLISECONDS);
          byPhysicalTenant.put(physicalTenantId, new Tenant(refresher, schedule, store));
          LOG.debug(
              "{} the secret refresher of physical tenant '{}', every {}",
              previous == null ? "Started" : "Re-pointed",
              physicalTenantId,
              interval);
        });
  }

  /**
   * Stops refreshing a tenant that was removed, and closes its refresher, its consumers and its
   * store.
   */
  public void stop(final String physicalTenantId) {
    onRefreshThread(
        () -> {
          final var tenant = byPhysicalTenant.remove(physicalTenantId);
          if (tenant != null) {
            tenant.schedule().cancel(false);
            tenant.refresher().close();
            release(physicalTenantId, tenant.store());
            LOG.debug("Stopped the secret refresher of physical tenant '{}'", physicalTenantId);
          }
        });
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    byPhysicalTenant.forEach((tenantId, tenant) -> release(tenantId, tenant.store()));
    byPhysicalTenant.clear();
  }

  private void onRefreshThread(final Runnable change) {
    try {
      scheduler
          .submit(
              (Callable<Void>)
                  () -> {
                    change.run();
                    return null;
                  })
          .get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException e) {
      throw e.getCause() instanceof final RuntimeException runtime
          ? runtime
          : new IllegalStateException(e.getCause());
    }
  }

  private static void release(
      final String physicalTenantId, final Owned<InternalConfigSecretResolver> store) {
    try {
      store.close();
    } catch (final RuntimeException e) {
      LOG.warn("Failed to release the secret store of physical tenant '{}'", physicalTenantId, e);
    }
  }

  private record Tenant(
      InternalConfigRefresher refresher,
      ScheduledFuture<?> schedule,
      Owned<InternalConfigSecretResolver> store) {}
}
