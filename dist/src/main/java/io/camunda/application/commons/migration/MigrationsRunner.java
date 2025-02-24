/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.migration.api.Migrator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migration | identity-migration | process-migration")
@ComponentScan(basePackages = "io.camunda.application.commons.migration")
public class MigrationsRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationsRunner.class);
  private final List<Migrator> migrators;

  public MigrationsRunner(final List<Migrator> migrators) {
    this.migrators = migrators;
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    LOG.info("Starting {} migration tasks", migrators.size());
    /* Detach from main Spring thread */
    CompletableFuture.runAsync(
            () -> {
              try {
                startMigrators(args);
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            },
            Executors.newSingleThreadExecutor())
        .exceptionally(
            e -> {
              LOG.error("Migration failed", e);
              return null;
            })
        .whenComplete((ignored, throwable) -> LOG.info("All migration tasks finished"));
  }

  private void startMigrators(final ApplicationArguments args) throws Exception {
    final Exception migrationsExceptions = new Exception("migration failed");
    try (final var executor = Executors.newFixedThreadPool(migrators.size())) {
      final var results = executor.invokeAll(migrators);
      for (final var result : results) {
        try {
          result.get();
        } catch (final ExecutionException e) {
          LOG.error("Migrator failed", e.getCause());
          migrationsExceptions.addSuppressed(e.getCause());
        }
      }
    }

    if (migrationsExceptions.getSuppressed().length > 0) {
      throw migrationsExceptions;
    }
  }
}
