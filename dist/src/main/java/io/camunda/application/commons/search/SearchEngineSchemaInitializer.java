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
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  public SearchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration,
      final MeterRegistry meterRegistry) {
    this.searchEngineConfiguration = searchEngineConfiguration;
    schemaManagerMetrics = new SchemaManagerMetrics(meterRegistry);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    LOGGER.info("Initializing search engine schema...");
    try (final var clientAdapter = ClientAdapter.of(searchEngineConfiguration.connect());
        final var executor = Executors.newSingleThreadExecutor()) {
      final IndexDescriptors indexDescriptors =
          new IndexDescriptors(
              searchEngineConfiguration.connect().getIndexPrefix(),
              searchEngineConfiguration.connect().getTypeEnum().isElasticSearch());
      final SchemaManager schemaManager =
          new SchemaManager(
                  clientAdapter.getSearchEngineClient(),
                  indexDescriptors.indices(),
                  indexDescriptors.templates(),
                  searchEngineConfiguration,
                  clientAdapter.objectMapper())
              .withMetrics(schemaManagerMetrics);
      if (!addShutdownHook(executor)) {
        LOGGER.info("skipping schema initialization, JVM is shutting down");
        return;
      }
      executor.submit(schemaManager::startup).get();
    } catch (final InterruptedException ie) {
      LOGGER.warn("Schema initialization task was interrupted");
      LOGGER.debug("Stack trace:", ie);
      Thread.currentThread().interrupt();
      return;
    } catch (final Exception e) {
      if (isShutdown.get()) {
        LOGGER.warn("Schema initialization interrupted with shutdown. Message: {}", e.getMessage());
        LOGGER.debug("Stack trace:", e);
        Thread.currentThread().interrupt();
        return;
      }
      throw e;
    }
    LOGGER.info("Search engine schema initialization complete.");
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
                    LOGGER.debug("Shutdown hook triggered");
                    if (isShutdown.compareAndSet(false, true)) {
                      executor.shutdownNow();
                    }
                  }));
      return true;
    } catch (final IllegalStateException e) {
      // This can happen if the shutdown hook is added after the JVM has started shutting down.
      // In this case, we just ignore the exception.
      LOGGER.info("JVM is shutting down, cannot add shutdown hook: {}", e.getMessage());
      LOGGER.debug("Stack trace:", e);
      return false;
    }
  }
}
