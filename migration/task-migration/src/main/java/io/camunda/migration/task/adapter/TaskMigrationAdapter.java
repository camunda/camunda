/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task.adapter;

import io.camunda.migration.api.MigrationException;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TaskMigrationAdapter {
  String TASK_MIGRATION_STEP_ID = VersionUtil.getVersion() + "-2"; // TODO: Verify version to use
  String TASK_MIGRATION_STEP_TYPE = "taskMigrationStep";
  String TASK_KEY = "key";
  String STEP_DESCRIPTION = "Task Migration last migrated document";

  List<String> getDatedTaskIndices();

  void reindexDatedIndex(String sourceDatedIndex) throws MigrationException;

  void reindexMainIndex() throws MigrationException;

  void deleteIndex(String indexName) throws MigrationException;

  String getLastMigratedTaskKey() throws MigrationException;

  void writeLastMigratedEntity(String processDefinitionKey) throws MigrationException;

  List<TaskEntity> nextBatch(final String lastMigratedTaskKey) throws MigrationException;

  String updateEntities(List<TaskEntity> entities) throws MigrationException;

  Set<ImportPositionEntity> readImportPosition() throws MigrationException;

  void close() throws IOException;

  default Map<String, Object> getUpdateMap(final TaskEntity entity) {
    final Map<String, Object> updateMap = new HashMap<>();
    // TODO: Complete
    //    updateMap.put(ProcessIndex.IS_PUBLIC, entity.getIsPublic());
    //    updateMap.put(ProcessIndex.IS_FORM_EMBEDDED, entity.getIsFormEmbedded());
    //
    //    if (entity.getFormId() != null) {
    //      updateMap.put(ProcessIndex.FORM_ID, entity.getFormId());
    //    }
    //    if (entity.getFormKey() != null) {
    //      updateMap.put(ProcessIndex.FORM_KEY, entity.getFormKey());
    //    }
    return updateMap;
  }

  default TaskMigrationStep taskMigrationStep(final String taskKey) {
    final TaskMigrationStep step = new TaskMigrationStep();
    step.setContent(taskKey);
    step.setApplied(true);
    step.setIndexName(TaskTemplate.INDEX_NAME);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    step.setAppliedDate(OffsetDateTime.now(ZoneId.systemDefault()));
    return step;
  }

  default String generateDestinationIndex(final String source) {
    if (source == null || !source.contains("task-8.5.0_")) {
      throw new RuntimeException("Invalid source: " + source); // TODO: Improve?
    }
    return source.replace("8.5.0", "8.8.0");
  }
}
