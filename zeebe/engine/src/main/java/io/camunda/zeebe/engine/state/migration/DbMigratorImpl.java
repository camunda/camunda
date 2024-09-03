/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.engine.state.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
import io.camunda.zeebe.engine.state.migration.to_8_2.DecisionMigration;
import io.camunda.zeebe.engine.state.migration.to_8_2.DecisionRequirementsMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyDecisionStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyJobStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyMessageStartEventSubscriptionStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyMessageStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyMessageSubscriptionStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyProcessMessageSubscriptionStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.MultiTenancyProcessStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_3.ProcessInstanceByProcessDefinitionMigration;
import io.camunda.zeebe.engine.state.migration.to_8_4.MultiTenancySignalSubscriptionStateMigration;
import io.camunda.zeebe.engine.state.migration.to_8_5.ColumnFamilyPrefixCorrectionMigration;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.ClusterContext;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.List;
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
          new TemporaryVariableMigration(),
          new DecisionMigration(),
          new DecisionRequirementsMigration(),
          new ProcessInstanceByProcessDefinitionMigration(),
          new JobTimeoutCleanupMigration(),
          new JobBackoffCleanupMigration(),
          new MultiTenancyProcessStateMigration(),
          new MultiTenancyDecisionStateMigration(),
          new MultiTenancyMessageStateMigration(),
          new MultiTenancyMessageStartEventSubscriptionStateMigration(),
          new MultiTenancyMessageSubscriptionStateMigration(),
          new MultiTenancyProcessMessageSubscriptionStateMigration(),
          new MultiTenancyJobStateMigration(),
          new ColumnFamilyPrefixCorrectionMigration(),
          new MultiTenancySignalSubscriptionStateMigration(),
          new JobBackoffRestoreMigration(),
          new RoutingInfoMigration());
  // Be mindful of https://github.com/camunda/camunda/issues/7248. In particular, that issue
  // should be solved first, before adding any migration that can take a long time

  private final MutableMigrationTaskContext migrationTaskContext;
  private final List<MigrationTask> migrationTasks;

  public DbMigratorImpl(
      final ClusterContext clusterContext, final MutableProcessingState processingState) {
    this(new MigrationTaskContextImpl(clusterContext, processingState), MIGRATION_TASKS);
  }

  public DbMigratorImpl(
      final MutableMigrationTaskContext migrationTaskContext,
      final List<MigrationTask> migrationTasks) {
    this.migrationTaskContext = migrationTaskContext;
    this.migrationTasks = migrationTasks;
  }

  @Override
  public void runMigrations() {
    if (checkVersionCompatibility() instanceof Compatible.SameVersion) {
      LOGGER.info("No migrations to run, snapshot is the same as current version");
      return;
    }
    logPreview(migrationTasks);

    final var executedMigrations = new ArrayList<MigrationTask>();
    for (int index = 1; index <= migrationTasks.size(); index++) {
      // one based index looks nicer in logs
      final var migration = migrationTasks.get(index - 1);
      final var executed = handleMigrationTask(migration, index, migrationTasks.size());
      if (executed) {
        executedMigrations.add(migration);
      }
    }
    markMigrationsAsCompleted();
    logSummary(executedMigrations);
  }

  private VersionCompatibilityCheck.CheckResult checkVersionCompatibility() {
    final var migratedByVersion =
        migrationTaskContext.processingState().getMigrationState().getMigratedByVersion();
    final var currentVersion = VersionUtil.getVersion();
    final CheckResult checkResult =
        VersionCompatibilityCheck.check(migratedByVersion, currentVersion);
    switch (checkResult) {
      case final Indeterminate.PreviousVersionUnknown previousVersionUnknown ->
          LOGGER.trace(
              "Snapshot is from an unknown version, not checking compatibility with current version: {}",
              previousVersionUnknown);
      case final Indeterminate indeterminate ->
          LOGGER.warn(
              "Could not check compatibility of snapshot with current version: {}", indeterminate);
      case final Incompatible.UseOfPreReleaseVersion preRelease ->
          throw new IllegalStateException(
              "Cannot upgrade to or from a pre-release version: %s".formatted(preRelease));
      case final Incompatible incompatible ->
          throw new IllegalStateException(
              "Snapshot is not compatible with current version: %s".formatted(incompatible));
      case final Compatible.SameVersion sameVersion ->
          LOGGER.trace("Snapshot is from the same version as the current version: {}", sameVersion);
      case final Compatible compatible ->
          LOGGER.info("Snapshot is compatible with current version: {}", compatible);
    }
    return checkResult;
  }

  private void markMigrationsAsCompleted() {
    migrationTaskContext
        .processingState()
        .getMigrationState()
        .setMigratedByVersion(VersionUtil.getVersion());
  }

  private void logPreview(final List<MigrationTask> migrationTasks) {
    LOGGER.info(
        "Starting processing of migration tasks (use LogLevel.DEBUG for more details) ... ");
    LOGGER.debug(
        "Found {} migration tasks: {}",
        migrationTasks.size(),
        migrationTasks.stream()
            .map(MigrationTask::getIdentifier)
            .collect(Collectors.joining(", ")));
  }

  private void logSummary(final List<MigrationTask> migrationTasks) {
    LOGGER.info(
        "Completed processing of migration tasks (use LogLevel.DEBUG for more details) ... ");
    LOGGER.debug(
        "Executed {} migration tasks: {}",
        migrationTasks.size(),
        migrationTasks.stream()
            .map(MigrationTask::getIdentifier)
            .collect(Collectors.joining(", ")));
  }

  private boolean handleMigrationTask(
      final MigrationTask migrationTask, final int index, final int total) {
    if (migrationTask.needsToRun(migrationTaskContext)) {
      runMigration(migrationTask, index, total);
      return true;
    } else {
      logMigrationSkipped(migrationTask, index, total);
      return false;
    }
  }

  private void logMigrationSkipped(
      final MigrationTask migrationTask, final int index, final int total) {
    LOGGER.info(
        "Skipping {} migration ({}/{}).  It was determined it does not need to run right now.",
        migrationTask.getIdentifier(),
        index,
        total);
  }

  private void runMigration(final MigrationTask migrationTask, final int index, final int total) {
    LOGGER.info("Starting {} migration ({}/{})", migrationTask.getIdentifier(), index, total);
    final var startTime = System.currentTimeMillis();
    migrationTask.runMigration(migrationTaskContext);
    final var duration = System.currentTimeMillis() - startTime;

    LOGGER.debug("{} migration completed in {} ms.", migrationTask.getIdentifier(), duration);
    LOGGER.info("Finished {} migration ({}/{})", migrationTask.getIdentifier(), index, total);
  }
}
