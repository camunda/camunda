/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbMigratorImpl implements DbMigrator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());

  // add new migration tasks here, migrations are executed in the order they appear in the list
  private static final List<MigrationTask> MIGRATION_TASKS =
      List.of(
          new ProcessMessageSubscriptionSentTimeMigration(),
          new MessageSubscriptionSentTimeMigration(),
          new TemporaryVariableMigration());
  // Be mindful of https://github.com/camunda/zeebe/issues/7248. In particular, that issue
  // should be solved first, before adding any migration that can take a long time

  private final MutableProcessingState processingState;
  private final Supplier<List<MigrationTask>> migrationSupplier;
  private boolean abortRequested = false;

  private MigrationTask currentMigration;

  public DbMigratorImpl(final MutableProcessingState processingState) {
    this(processingState, () -> MIGRATION_TASKS);
  }

  DbMigratorImpl(
      final MutableProcessingState processingState,
      final Supplier<List<MigrationTask>> migrationSupplier) {

    this.processingState = processingState;
    this.migrationSupplier = migrationSupplier;
  }

  @Override
  public void runMigrations() {

    final var migrationTasks = migrationSupplier.get();
    logPreview(migrationTasks);

    final var executedMigrations = new ArrayList<MigrationTask>();
    for (int index = 1; index <= migrationTasks.size() && !abortRequested; index++) {
      // one based index looks nicer in logs

      final var migration = migrationTasks.get(index - 1);
      final var executed = handleMigrationTask(migration, index, migrationTasks.size());
      if (executed) {
        executedMigrations.add(migration);
      }
    }
    if (!abortRequested) {
      logSummary(executedMigrations);
    }
  }

  @Override
  public void abort() {
    final var message =
        currentMigration == null
            ? "Received abort signal (no migration running)"
            : "Aborting " + currentMigration.getIdentifier() + " migration as requested";
    LOGGER.info(message);
    abortRequested = true;
  }

  private void logPreview(final List<MigrationTask> migrationTasks) {
    LOGGER.info(
        "Starting processing of migration tasks (use LogLevel.DEBUG for more details) ... ");
    LOGGER.debug(
        "Found "
            + migrationTasks.size()
            + " migration tasks: "
            + migrationTasks.stream()
                .map(MigrationTask::getIdentifier)
                .collect(Collectors.joining(", ")));
  }

  private void logSummary(final List<MigrationTask> migrationTasks) {
    LOGGER.info(
        "Completed processing of migration tasks (use LogLevel.DEBUG for more details) ... ");
    LOGGER.debug(
        "Executed "
            + migrationTasks.size()
            + " migration tasks: "
            + migrationTasks.stream()
                .map(MigrationTask::getIdentifier)
                .collect(Collectors.joining(", ")));
  }

  private boolean handleMigrationTask(
      final MigrationTask migrationTask, final int index, final int total) {
    if (migrationTask.needsToRun(processingState)) {
      try {
        currentMigration = migrationTask;
        runMigration(migrationTask, index, total);
      } finally {
        currentMigration = null;
      }
      return true;
    } else {
      logMigrationSkipped(migrationTask, index, total);
      return false;
    }
  }

  private void logMigrationSkipped(
      final MigrationTask migrationTask, final int index, final int total) {
    LOGGER.info(
        "Skipping "
            + migrationTask.getIdentifier()
            + " migration ("
            + index
            + "/"
            + total
            + ").  It was determined it does not need to run right now.");
  }

  private void runMigration(final MigrationTask migrationTask, final int index, final int total) {
    LOGGER.info(
        "Starting " + migrationTask.getIdentifier() + " migration (" + index + "/" + total + ")");
    final var startTime = System.currentTimeMillis();
    migrationTask.runMigration(processingState);
    final var duration = System.currentTimeMillis() - startTime;

    LOGGER.debug(migrationTask.getIdentifier() + " migration completed in " + duration + " ms.");
    LOGGER.info(
        "Finished " + migrationTask.getIdentifier() + " migration (" + index + "/" + total + ")");
  }
}
