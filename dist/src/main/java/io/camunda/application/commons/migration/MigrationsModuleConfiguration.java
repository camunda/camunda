/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.application.StandaloneMigration.MigrationFinishedEvent;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("migration | identity-migration | process-migration")
@ComponentScan(basePackages = "io.camunda.application.commons.migration")
public class MigrationsModuleConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationsModuleConfiguration.class);
  private final List<Migrator> migrators;
  private final ApplicationArguments args;
  private final ApplicationEventPublisher applicationEventPublisher;

  public MigrationsModuleConfiguration(
      final List<Migrator> migrators,
      final ApplicationArguments args,
      final ApplicationEventPublisher applicationEventPublisher) {
    this.migrators = migrators;
    this.args = args;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @PostConstruct
  public void migrate() {
    try (final ScheduledExecutorService scheduledExecutorService =
        Executors.newScheduledThreadPool(1)) {
      scheduledExecutorService.schedule(
          () -> {
            performMigration();
            LOG.info("Migration finished, shutting down");
          },
          0,
          TimeUnit.SECONDS);
    } catch (final Exception e) {
      LOG.error("Migration failed", e);
      applicationEventPublisher.publishEvent(new MigrationFinishedEvent(-1));
    }
    applicationEventPublisher.publishEvent(new MigrationFinishedEvent(0));
  }

  private void performMigration() {
    final CountDownLatch latch = new CountDownLatch(migrators.size());
    try (final ThreadPoolExecutor executor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(migrators.size())) {
      migrators.forEach(
          migrator ->
              executor.submit(
                  () -> {
                    try {
                      migrator.run(args);
                    } catch (final MigrationException ex) {
                      LOG.error(ex.getMessage());
                    } finally {
                      latch.countDown();
                    }
                  }));
      latch.await();
    } catch (final InterruptedException e) {
      LOG.error("Migration failed", e);
    }
  }
}
