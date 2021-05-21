/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbMigratorImpl implements DbMigrator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());

  // add new migration tasks here, jobs are executed in the order they appear in the list
  private static final List<MigrationTask> MIGRATION_TASKS =
      List.of(
          new MessageSubscriptionSentTimeMigration(), new ProcessSubscriptionSentTimeMigration());

  private final MutableZeebeState zeebeState;

  private boolean abortRequested = false;

  public DbMigratorImpl(final MutableZeebeState zeebeState) {
    this.zeebeState = zeebeState;
  }

  @Override
  public void runMigrations() {
    LOGGER.info("Starting migrations ...");
    MIGRATION_TASKS.forEach(this::handleMigrationJob);
    LOGGER.info("Finished running migrations");
  }

  @Override
  public void abort() {
    LOGGER.info("Received abort signal");
    abortRequested = true;
  }

  private void handleMigrationJob(final MigrationTask migrationTask) {
    if (abortRequested) {
      return;
    }

    final var identifier = migrationTask.getIdentifier();

    if (migrationTask.needsToRun(zeebeState)) {
      runMigration(migrationTask);
    } else {
      LOGGER.info(
          "Skipping "
              + migrationTask.getIdentifier()
              + " migration. It was determined it does not need to run right now.");
    }
  }

  private void runMigration(final MigrationTask migrationTask) {
    LOGGER.info("Start " + migrationTask.getIdentifier() + " migration.");
    migrationTask.runMigration(zeebeState);
    LOGGER.info("Finished " + migrationTask.getIdentifier() + " migration.");
  }
}
