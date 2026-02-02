/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertJobMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class JobWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;
  private final RdbmsWriterConfig config;

  public JobWriter(
      final ExecutionQueue executionQueue,
      final JobMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.config = config;
  }

  public void create(final JobDbModel job) {
    final var truncatedJob =
        job.truncateErrorMessage(
            vendorDatabaseProperties.errorMessageSize(),
            vendorDatabaseProperties.charColumnMaxBytes());

    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertJobMerger(truncatedJob, config.insertBatchingConfig().jobInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.JOB,
              WriteStatementType.INSERT,
              -1,
              "io.camunda.db.rdbms.sql.JobMapper.insert",
              new JobMapper.BatchInsertJobsDto.Builder().job(truncatedJob).build()));
    }
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
}
