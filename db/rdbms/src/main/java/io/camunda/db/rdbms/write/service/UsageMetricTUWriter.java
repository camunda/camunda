/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class UsageMetricTUWriter {

  private final ExecutionQueue executionQueue;
  private final UsageMetricTUMapper mapper;

  public UsageMetricTUWriter(
      final ExecutionQueue executionQueue, final UsageMetricTUMapper usageMetricTUMapper) {
    this.executionQueue = executionQueue;
    mapper = usageMetricTUMapper;
  }

  public void create(final UsageMetricTUDbModel dbModel) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USAGE_METRIC_TU,
            WriteStatementType.INSERT,
            dbModel.getId(),
            "io.camunda.db.rdbms.sql.UsageMetricTUMapper.insert",
            dbModel));
  }

  public int cleanupMetrics(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupMetrics(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
