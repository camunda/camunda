/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("process-migration")
@ComponentScan(basePackages = "io.camunda.application.commons.migration")
public class AsyncMigrationsRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncMigrationsRunner.class);
  private final List<Migrator> migrators;
  private final ApplicationEventPublisher eventPublisher;

  public AsyncMigrationsRunner(
      final List<Migrator> migrators, final ApplicationEventPublisher eventPublisher) {
    this.migrators = migrators;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void run(final ApplicationArguments args) throws Exception {
    LOG.info(
        "Starting {} migrations: {}",
        migrators.size(),
        migrators.stream().map(Migrator::getName).collect(Collectors.joining(", ")));
    /* Detach from main Spring thread */
    new Thread(
            () -> {
              try {
                startMigrators(args);
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            },
            "migration-parent")
        .start();
  }

  private void startMigrators(final ApplicationArguments args) throws Exception {
    final Exception migrationsExceptions = new Exception("migration failed");
    boolean migrationSucceeded = true;
    try (final var executor =
        Executors.newFixedThreadPool(
            migrators.size(), new CustomizableThreadFactory("migration-"))) {
      final var results = executor.invokeAll(migrators);
      for (final var result : results) {
        try {
          result.get();
        } catch (final ExecutionException e) {
          migrationSucceeded = !shouldFailMigration(e.getCause());
          LOG.error("Migrator failed", e.getCause());
          migrationsExceptions.addSuppressed(e.getCause());
        }
      }
    }

    eventPublisher.publishEvent(new ProcessMigrationFinishedEvent(migrationSucceeded));

    if (migrationsExceptions.getSuppressed().length > 0) {
      throw migrationsExceptions;
    }
    LOG.info("All migration tasks completed");
  }

  private boolean shouldFailMigration(final Throwable e) {
    if (e instanceof final MigrationException migrationException) {
      return !(migrationException.getCause() instanceof MigrationTimeoutException);
    }
    return true;
  }

  public static class ProcessMigrationFinishedEvent extends MigrationFinishedEvent {

    private final boolean success;

    public ProcessMigrationFinishedEvent(final boolean success) {
      super(success);
      this.success = success;
    }

    @Override
    public boolean isSuccess() {
      return success;
    }
  }
}
