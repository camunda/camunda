/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

public class SearchEngineSchemaInitializer implements InitializingBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineSchemaInitializer.class);
  private final SearchEngineConfiguration searchEngineConfiguration;
  private final SchemaManagerMetrics schemaManagerMetrics;
  private final boolean awaitSchemaInitialization;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  public SearchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration,
      final MeterRegistry meterRegistry,
      final boolean awaitSchemaInitialization) {
    this.searchEngineConfiguration = searchEngineConfiguration;
    schemaManagerMetrics = new SchemaManagerMetrics(meterRegistry);
    this.awaitSchemaInitialization = awaitSchemaInitialization;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    LOGGER.info("Initializing search engine schema...");
    final var executor = Executors.newSingleThreadExecutor();
    if (!addShutdownHook(executor)) {
      // skipping schema initialization as JVM is shutting down
      return;
    }
    final var future = CompletableFuture.runAsync(this::startupSchemaManager, executor);
    if (awaitSchemaInitialization) {
      synchronousSchemaInitialization(future, executor);
    } else {
      asyncSchemaInitialization(future, executor);
    }
  }

  private void synchronousSchemaInitialization(
      final CompletableFuture<Void> future, final ExecutorService executor) throws Exception {
    try {
      future.get();
      LOGGER.info("Search engine schema initialization complete.");
    } catch (final InterruptedException ie) {
      LOGGER.debug("Schema initialization task was interrupted.", ie);
      Thread.currentThread().interrupt();
    } catch (final Exception e) {
      if (isShutdown.get()) {
        LOGGER.debug("Schema initialization interrupted with shutdown.", e);
        Thread.currentThread().interrupt();
        return;
      }
      throw e;
    } finally {
      executor.close();
    }
  }

  private void asyncSchemaInitialization(
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

  private void startupSchemaManager() {
    final var indexDescriptors =
        new IndexDescriptors(
            searchEngineConfiguration.connect().getIndexPrefix(),
            searchEngineConfiguration.connect().getTypeEnum().isElasticSearch());
    final ClientAdapter clientAdapter = ClientAdapter.of(searchEngineConfiguration.connect());
    try {
      new SchemaManager(
              clientAdapter.getSearchEngineClient(),
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              searchEngineConfiguration,
              clientAdapter.objectMapper())
          .withMetrics(schemaManagerMetrics)
          .startup();
    } finally {
      try {
        clientAdapter.close();
      } catch (final IOException e) {
        LOGGER.debug("Failed to close search client", e);
      }
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
}
