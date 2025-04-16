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
import io.camunda.zeebe.engine.state.migration.to_8_6.OrderedCommandDistributionMigration;
import io.camunda.zeebe.engine.state.migration.to_8_7.IdempotentCommandDistributionMigration;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.ClusterContext;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbMigratorImpl implements DbMigrator {

  // add new migration tasks here, migrations are executed in the order they appear in the list
  public static final List<MigrationTask> MIGRATION_TASKS =
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
          new RoutingInfoMigration(),
          new OrderedCommandDistributionMigration(),
          new IdempotentCommandDistributionMigration());
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());
  // Be mindful of https://github.com/camunda/camunda/issues/7248. In particular, that issue
  // should be solved first, before adding any migration that can take a long time
  private final MutableMigrationTaskContext migrationTaskContext;
  private final List<MigrationTask> migrationTasks;
  private final boolean versionCheckRestrictionEnabled;

  private int skippedMigrations = 0;

  public DbMigratorImpl(
      final boolean versionCheckRestrictionEnabled,
      final ClusterContext clusterContext,
      final MutableProcessingState processingState) {
    this(
        versionCheckRestrictionEnabled,
        new MigrationTaskContextImpl(clusterContext, processingState),
        MIGRATION_TASKS);
  }

  public DbMigratorImpl(
      final MutableMigrationTaskContext migrationTaskContext,
      final List<MigrationTask> migrationTasks) {
    this(true, migrationTaskContext, migrationTasks);
  }

  public DbMigratorImpl(
      final boolean versionCheckRestrictionEnabled,
      final MutableMigrationTaskContext migrationTaskContext,
      final List<MigrationTask> migrationTasks) {
    this.versionCheckRestrictionEnabled = versionCheckRestrictionEnabled;
    this.migrationTaskContext = migrationTaskContext;
    this.migrationTasks = migrationTasks;
  }

  @Override
  public void runMigrations() {
    if (checkVersionCompatibility() instanceof Compatible.SameVersion) {
      LOGGER.debug("No migrations to run, snapshot is the same as current version");
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
      case final Incompatible.UseOfPreReleaseVersion preRelease -> {
        final String errorMsg =
            "Cannot upgrade to or from a pre-release version: %s".formatted(preRelease);
        if (versionCheckRestrictionEnabled) {
          throw new IllegalStateException(errorMsg);
        } else {
          LOGGER.warn(
              "Detected issue with migration, but ignoring as configured. Details: '{}'", errorMsg);
        }
      }
      case final Incompatible incompatible -> {
        final String errorMsg =
            "Snapshot is not compatible with current version: %s".formatted(incompatible);
        if (versionCheckRestrictionEnabled) {
          throw new IllegalStateException(errorMsg);
        } else {
          LOGGER.warn(
              "Detected issue with migration, but ignoring as configured. Details: '{}'", errorMsg);
        }
      }
      case final Compatible.SameVersion sameVersion ->
          LOGGER.trace("Snapshot is from the same version as the current version: {}", sameVersion);
      case final Compatible compatible ->
          LOGGER.debug("Snapshot is compatible with current version: {}", compatible);
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
        "Starting processing {} migration tasks (use LogLevel.DEBUG for more details) ... ",
        migrationTasks.size());
    LOGGER.debug(
        "Found {} migration tasks: {}",
        migrationTasks.size(),
        migrationTasks.stream()
            .map(MigrationTask::getIdentifier)
            .collect(Collectors.joining(", ")));
  }

  private void logSummary(final List<MigrationTask> migrationTasks) {
    final var executedTasks = migrationTasks.size() - skippedMigrations;

    LOGGER.info(
        "Completed processing of {}/{} migration tasks (use LogLevel.DEBUG for more details) ... ",
        executedTasks,
        migrationTasks.size());
    LOGGER.debug(
        "Executed {} migration tasks ({} skipped out of {}): {}",
        executedTasks,
        skippedMigrations,
        migrationTasks.size(),
        migrationTasks.stream()
            .map(MigrationTask::getIdentifier)
            .collect(Collectors.joining(", ")));
    skippedMigrations = 0;
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
    skippedMigrations++;
    LOGGER.debug(
        "Skipping {} migration ({}/{}). It was determined it does not need to run right now.",
        migrationTask.getIdentifier(),
        index,
        total);
  }

  private void runMigration(final MigrationTask migrationTask, final int index, final int total) {
    LOGGER.debug("Starting {} migration ({}/{})", migrationTask.getIdentifier(), index, total);
    final var startTime = System.currentTimeMillis();
    migrationTask.runMigration(migrationTaskContext);
    final var duration = System.currentTimeMillis() - startTime;

    LOGGER.debug(
        "Finished {} migration ({}/{}) in {} ms.",
        migrationTask.getIdentifier(),
        index,
        total,
        duration);
  }
}
