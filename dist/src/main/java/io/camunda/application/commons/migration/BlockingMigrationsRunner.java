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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("identity-migration")
@ComponentScan(basePackages = "io.camunda.application.commons.migration")
public class BlockingMigrationsRunner implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(BlockingMigrationsRunner.class);
  private final List<Migrator> migrators;

  public BlockingMigrationsRunner(final List<Migrator> migrators) {
    this.migrators = migrators;
  }

  @Override
  public void run() {
    LOG.info("Starting {} migrations: {}", migrators.size(), migrators);

    for (final Migrator migrator : migrators) {
      try {
        migrator.call();
      } catch (final Exception e) {
        LOG.error("Migrator {} failed with: {}", migrator.getName(), e.getMessage());
        LOG.error(
            "The cause for the failed migration was: {}", e.getCause().getMessage(), e.getCause());
        LOG.info(
            "Please assess the cause, take potential corrective action and rerun the migration.");
        throw new RuntimeException(e);
      }
    }

    LOG.info("All migrations completed successfully.");
  }
}
