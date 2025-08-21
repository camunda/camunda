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
import java.time.OffsetDateTime;
import java.time.ZoneId;

public interface UsageMetricMigrationClient {

  String LANG_PAINLESS = "painless";
  String OPERATE_MIGRATOR_STEP_ID = VersionUtil.getVersion() + "-2";
  String STEP_DESCRIPTION = "Usage Metric Migration operate reindex status";

  void writeOperateMetricMigratorStep(
      final String index, final String taskId, final boolean completed) throws MigrationException;

  String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script);

  <T> T findOne(String index, SearchQuery searchQuery, final Class<T> entityClass)
      throws MigrationException;

  boolean getTask(String taskId) throws MigrationException;

  default ProcessorStep migrationStepForKey(
      final String index, final String taskId, final boolean completed) {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(taskId);
    step.setApplied(completed);
    step.setIndexName(index);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    step.setAppliedDate(OffsetDateTime.now(ZoneId.systemDefault()));
    return step;
  }
}
