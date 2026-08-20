/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class JobMetricsBatchWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final JobMetricsBatchMapper jobMetricsBatchMapper = mock(JobMetricsBatchMapper.class);
  private final JobMetricsBatchWriter writer =
      new JobMetricsBatchWriter(executionQueue, jobMetricsBatchMapper);

  @Test
  void shouldCreateJobMetricsBatch() {
    final var model = mock(JobMetricsBatchDbModel.class);
    when(model.key()).thenReturn("batch-key-123");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.JOB_METRICS_BATCH,
                    WriteStatementType.INSERT,
                    "batch-key-123",
                    "io.camunda.db.rdbms.sql.JobMetricsBatchMapper.insert",
                    model)));
  }
}
