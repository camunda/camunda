/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;

public interface UsageMetricMigrationClient {

  String LANG_PAINLESS = "painless";
  String OPERATE_MIGRATOR_STEP_ID = VersionUtil.getVersion() + "-2";
  String TASKLIST_MIGRATOR_STEP_ID = VersionUtil.getVersion() + "-2";
  String OPERATE_STEP_DESCRIPTION = "Usage Metric Migration operate reindex status";
  String TASKLIST_STEP_DESCRIPTION = "TU Metric Migration tasklist reindex status";

  void persistMigratorStep(
      final String index,
      final String id,
      final String taskId,
      final String description,
      final boolean completed)
      throws MigrationException;

  String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script);

  <T> T findOne(String index, SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException;

  <T> Collection<T> findAll(
      final String index, final SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException;

  TaskStatus getTask(String taskId) throws MigrationException;

  String reindex(
      final String src,
      final String dest,
      final SearchQuery searchQuery,
      final String script,
      final Map<String, ?> params);

  Collection<String> getAllAssigneesInMetrics(String index) throws IOException;

  default ProcessorStep processorStep(
      final String index, final String taskId, final boolean completed, final String description) {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(taskId);
    step.setApplied(completed);
    step.setIndexName(index);
    step.setDescription(description);
    step.setVersion(VersionUtil.getVersion());
    step.setAppliedDate(OffsetDateTime.now(ZoneId.systemDefault()));
    return step;
  }

  record TaskStatus(
      String taskId,
      boolean found,
      boolean completed,
      String description,
      long total,
      long created,
      long updated,
      long deleted) {

    public static TaskStatus notFound() {
      return new TaskStatus("", false, false, "", 0, 0, 0, 0);
    }
  }
}
