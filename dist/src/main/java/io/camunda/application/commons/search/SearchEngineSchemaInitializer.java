/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.SchemaManagerContainer;
import io.camunda.search.schema.SearchEngineHealthCheckPermissionException;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.retry.RetryDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

public class SearchEngineSchemaInitializer implements InitializingBean, SchemaManagerContainer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineSchemaInitializer.class);
  private final MeterRegistry meterRegistry;
  private final boolean awaitSchemaInitialization;
  private final boolean healthCheckEnabled;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);
  private final Map<String, SearchEngineConfiguration> configs;
  private final Map<String, IndexDescriptors> descriptors;
  private final Map<String, AtomicBoolean> initialized;

  public SearchEngineSchemaInitializer(
      final Map<String, SearchEngineConfiguration> configsByTenant,
      final Map<String, IndexDescriptors> descriptorsByTenant,
      final MeterRegistry meterRegistry,
      final boolean awaitSchemaInitialization,
      final boolean healthCheckEnabled) {
    this.meterRegistry = meterRegistry;
    this.awaitSchemaInitialization = awaitSchemaInitialization;
    this.healthCheckEnabled = healthCheckEnabled;

    configs = configsByTenant;
    descriptors = descriptorsByTenant;
    initialized =
        configs.keySet().stream()
            .collect(toUnmodifiableMap(id -> id, id -> new AtomicBoolean(false)));
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    LOGGER.info(
        "Initializing search engine schema for {} physical tenant(s): {}",
        configs.size(),
        configs.keySet());

    final var executor = Executors.newSingleThreadExecutor();
    if (!addShutdownHook(executor)) {
      // skipping schema initialization as JVM is shutting down
      return;
    }

    final var future = CompletableFuture.runAsync(this::startupSchemaManagers, executor);
    if (awaitSchemaInitialization) {
      synchronousSchemaInitialization(future, executor);
    } else {
      asyncSchemaInitialization(future, executor);
    }
  }

  private void startupSchemaManagers() {
    configs.keySet().forEach(this::startupSchemaManagerForTenant);
  }

  @VisibleForTesting
  void synchronousSchemaInitialization(
      final CompletableFuture<Void> future, final ExecutorService executor) throws Exception {
    try {
      future.get();
      LOGGER.info("Search engine schema initialization complete.");
    } catch (final InterruptedException ie) {
      LOGGER.debug(
          "Schema initialization task was interrupted. Shutdown signal is caught={}",
          isShutdown.get(),
          ie);
      if (!isShutdown.get()) {
        // Only re-interrupt if this is not a shutdown-triggered interrupt.
        // During shutdown, we must let Spring finish context refresh so that
        // Broker can perform a graceful shutdown.
        Thread.currentThread().interrupt();
      }
    } catch (final Exception e) {
      if (isShutdown.get()) {
        LOGGER.debug("Schema initialization interrupted with shutdown.", e);
        return;
      }
      throw e;
    } finally {
      executor.close();
    }
  }

  @VisibleForTesting
  void asyncSchemaInitialization(
      final CompletableFuture<Void> future, final ExecutorService executor) {
    future.whenCompleteAsync(
        (result, error) -> {
          if (error != null) {
            LOGGER.warn("Failed to initialize search engine schema", error);
          } else {
            LOGGER.info("Search engine schema initialization complete.");
          }
          executor.close();
        });
  }

  private void startupSchemaManagerForTenant(final String physicalTenantId) {
    if (isShutdown.get()) {
      return;
    }

    final SearchEngineConfiguration configuration = configs.get(physicalTenantId);
    final IndexDescriptors indexDescriptors = descriptors.get(physicalTenantId);

    try (final ClientAdapter clientAdapter = ClientAdapter.of(configuration.connect());
        final SchemaManager schemaManager =
            new SchemaManager(
                    clientAdapter.getSearchEngineClient(),
                    indexDescriptors.indices(),
                    indexDescriptors.templates(),
                    configuration,
                    clientAdapter.objectMapper())
                .withMetrics(new SchemaManagerMetrics(meterRegistry, physicalTenantId))) {
      schemaManager.startup();
      if (healthCheckEnabled) {
        try {
          new RetryDecorator(configuration.schemaManager().getRetry())
              .withRetryOnException(e -> !(e instanceof SearchEngineHealthCheckPermissionException))
              .decorate(
                  "cluster health check after schema init",
                  clientAdapter.getSearchEngineClient()::isHealthy,
                  healthy -> !healthy);
        } catch (final SearchEngineHealthCheckPermissionException e) {
          LOGGER.warn(e.getMessage());
          return;
        } catch (final Exception e) {
          LOGGER.warn(
              "Cluster health check failed after schema init; not declaring schema as ready. "
                  + "If this is a permission issue, set 'camunda.database.schema-manager.health-check-enabled=false'.",
              e);
          return;
        }
      }
      initialized.get(physicalTenantId).set(true);
    } catch (final IOException e) {
      if (!isInitialized(physicalTenantId)) {
        throw new UncheckedIOException(e);
      }
      LOGGER.debug("Failed to close the search client", e);
    }
  }

  /**
   * Adds a shutdown hook to the JVM that will be triggered when the application is shutting
   *
   * @return true if the shutdown hook was added successfully, false if the JVM is already shutting
   *     down
   */
  private boolean addShutdownHook(final ExecutorService executor) {
    try {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    LOGGER.trace("Shutdown hook triggered");
                    if (isShutdown.compareAndSet(false, true)) {
                      executor.shutdownNow();
                    }
                  }));
      return true;
    } catch (final IllegalStateException e) {
      // This can happen if the shutdown hook is added after the JVM has started shutting down.
      // In this case, we just ignore the exception.
      LOGGER.debug("JVM is shutting down, cannot add the schema initializer shutdown hook", e);
      return false;
    }
  }

  @Override
  public boolean isInitialized(final String physicalTenantId) {
    final AtomicBoolean flag = initialized.get(physicalTenantId);
    return flag != null && flag.get();
  }

  /**
   * Returns true if the schema initialization completed successfully for <em>all</em> physical
   * tenants. This can be used by dependent components to check if they should proceed with their
   * initialization.
   *
   * @return true if schema initialization completed successfully for all tenants, false otherwise
   */
  @Override
  public boolean isInitialized() {
    return !initialized.isEmpty() && initialized.values().stream().allMatch(AtomicBoolean::get);
  }
}
