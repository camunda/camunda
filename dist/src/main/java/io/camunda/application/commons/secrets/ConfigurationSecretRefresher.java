/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.ConfigurationValues;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a {@link TenantSecretRefresh} for each physical tenant on this node, at the tenant's refresh
 * interval.
 */
public final class ConfigurationSecretRefresher implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationSecretRefresher.class);

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("configuration-secret-refresh").daemon().factory());

  public ConfigurationSecretRefresher(
      final PhysicalTenantResolver resolver,
      final ConfigurationSecretStores stores,
      final ConfigurationReferences references,
      final RefreshableClients clients) {
    resolver
        .getAll()
        .forEach(
            (tenantId, camunda) -> {
              final Duration interval;
              try {
                interval = camunda.getSecrets().getRefreshInterval();
              } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Physical tenant '%s' has an invalid secrets configuration: %s"
                        .formatted(tenantId, e.getMessage()),
                    e);
              }
              final Map<String, String> configuration = new HashMap<>();
              ConfigurationValues.walk(
                  camunda,
                  (property, field, value) -> {
                    configuration.put(property, value);
                    return value;
                  });
              final var refresh =
                  new TenantSecretRefresh(
                      tenantId,
                      interval,
                      () -> stores.forPhysicalTenant(tenantId),
                      references.forPhysicalTenant(tenantId),
                      configuration,
                      () -> clients.forPhysicalTenant(tenantId),
                      InstantSource.system());
              schedule(tenantId, refresh, interval);
            });
  }

  private void schedule(
      final String tenantId, final TenantSecretRefresh refresh, final Duration delay) {
    if (scheduler.isShutdown()) {
      return;
    }
    scheduler.schedule(
        () ->
            Thread.ofVirtual()
                .name("configuration-secret-refresh-" + tenantId)
                .start(() -> run(tenantId, refresh)),
        delay.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private void run(final String tenantId, final TenantSecretRefresh refresh) {
    Duration next;
    try {
      next = refresh.run();
    } catch (final RuntimeException e) {
      LOG.warn("Physical tenant '{}' failed to refresh its configuration secrets", tenantId, e);
      next = TenantSecretRefresh.INITIAL_BACKOFF;
    }
    try {
      schedule(tenantId, refresh, next);
    } catch (final RejectedExecutionException e) {
      // the node is shutting down
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
