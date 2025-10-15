/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import io.camunda.migration.api.MigrationException;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface TaskMigrationAdapter {
  String TASK_MIGRATION_STEP_ID = VersionUtil.getVersion() + "-1";
  String TASK_MIGRATION_STEP_TYPE = "processorStep";
  String LEGACY_INDEX_RETENTION_POLICY_NAME = "task-migration-retention-policy";

  boolean migrationIndexExists() throws MigrationException;

  boolean migrationIsCompleted() throws MigrationException;

  void markMigrationAsCompleted() throws MigrationException;

  List<String> getLegacyDatedIndices() throws MigrationException;

  void reindexLegacyDatedIndex(String legacyDatedIndex) throws MigrationException;

  void reindexLegacyMainIndex() throws MigrationException;

  void deleteLegacyIndex(String legacyIndex) throws MigrationException;

  String getLastMigratedTaskId() throws MigrationException;

  void writeLastMigratedTaskId(String taskId) throws MigrationException;

  List<TaskEntityPair> nextBatch(final String lastMigratedTaskId) throws MigrationException;

  String updateAcrossAllIndices(List<TaskWithIndex> tasksWithIndex) throws MigrationException;

  Set<ImportPositionEntity> getImportPositions() throws MigrationException;

  void applyRetentionOnLegacyRuntimeIndex() throws MigrationException;

  void blockArchiving() throws MigrationException;

  void resumeArchiving() throws MigrationException;

  void close() throws IOException;
}
