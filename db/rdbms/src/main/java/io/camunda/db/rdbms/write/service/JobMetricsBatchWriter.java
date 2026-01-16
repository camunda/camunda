/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class JobMetricsBatchWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public JobMetricsBatchWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final JobMetricsBatchDbModel dbModel) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.JOB_METRICS_BATCH,
            WriteStatementType.INSERT,
            dbModel.key(),
            "io.camunda.db.rdbms.sql.JobMetricsBatchMapper.insert",
            dbModel));
  }
}
