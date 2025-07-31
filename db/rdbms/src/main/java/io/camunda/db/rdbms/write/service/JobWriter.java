/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class JobWriter {

  private final ExecutionQueue executionQueue;
  private final JobMapper mapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public JobWriter(
      final ExecutionQueue executionQueue,
      final JobMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final JobDbModel job) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.INSERT,
            job.jobKey(),
            "io.camunda.db.rdbms.sql.JobMapper.insert",
            job.truncateErrorMessage(
                vendorDatabaseProperties.errorMessageSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void update(final JobDbModel job) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.UPDATE,
            job.jobKey(),
            "io.camunda.db.rdbms.sql.JobMapper.update",
            job));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.JobMapper.updateHistoryCleanupDate",
            new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
