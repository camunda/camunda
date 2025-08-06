/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric.client;

import io.camunda.migration.api.MigrationException;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public interface UsageMetricMigrationClient {

  String LANG_PAINLESS = "painless";
  String OPERATE_MIGRATOR_STEP_TYPE = "usageMetricOperatorStep";
  String STEP_DESCRIPTION = "Usage Metric Migration operate reindex status";

  void writeOperateMetricMigratorStep(
      final String index, final String taskId, final boolean completed) throws MigrationException;

  UsageMetricsEntity getFirstUsageMetricEntity(final String index, final SearchQuery searchQuery)
      throws IOException;

  String reindex(
      final String src, final String dest, final SearchQuery searchQuery, final String script);

  UsageMetricsEntity getLatestMigratedEntity(String index, SearchQuery searchQuery)
      throws IOException;

  boolean getTask(String taskId) throws MigrationException;

  MigrationStep readOperateMetricMigratorStep(final String index) throws MigrationException;

  default MigrationStep processorStepForKey(
      final String index, final String taskId, final boolean completed) {
    return new MigrationStep.Builder()
        .content(taskId)
        .applied(completed)
        .indexName(index)
        .description(STEP_DESCRIPTION)
        .version(VersionUtil.getVersion())
        .appliedDate(OffsetDateTime.now(ZoneId.systemDefault()))
        .build();
  }
}
