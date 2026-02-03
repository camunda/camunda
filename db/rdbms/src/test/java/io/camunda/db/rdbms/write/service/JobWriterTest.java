/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class JobWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final JobMapper mapper = mock(JobMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final RdbmsWriterConfig config = mock(RdbmsWriterConfig.class);
  private final RdbmsWriterConfig.InsertBatchingConfig insertBatchingConfig =
      mock(RdbmsWriterConfig.InsertBatchingConfig.class);
  private final JobWriter writer =
      new JobWriter(executionQueue, mapper, vendorDatabaseProperties, config);

  @Test
  void shouldCreateJob() {
    when(config.insertBatchingConfig()).thenReturn(insertBatchingConfig);
    when(insertBatchingConfig.jobInsertBatchSize()).thenReturn(1);
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(false);

    final var model = mock(JobDbModel.class);
    final var truncatedModel = mock(JobDbModel.class);
    when(model.truncateErrorMessage(anyInt())).thenReturn(truncatedModel);
    when(model.jobKey()).thenReturn(123L);

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.JOB,
                    WriteStatementType.INSERT,
                    -1,
                    "io.camunda.db.rdbms.sql.JobMapper.insert",
                    new BatchInsertDto<>(truncatedModel))));
  }

  @Test
  void shouldMergeJobInsertionWhenPossible() {
    when(vendorDatabaseProperties.errorMessageSize()).thenReturn(5000);
    when(config.insertBatchingConfig()).thenReturn(insertBatchingConfig);
    when(insertBatchingConfig.jobInsertBatchSize()).thenReturn(10);
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(true);

    final var model = mock(JobDbModel.class);
    final var truncatedModel = mock(JobDbModel.class);
    when(model.truncateErrorMessage(anyInt())).thenReturn(truncatedModel);
    when(model.jobKey()).thenReturn(123L);

    writer.create(model);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateJobWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(false);

    final var model = mock(JobDbModel.class);
    when(model.jobKey()).thenReturn(123L);

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.JOB,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.JobMapper.update",
                    model)));
  }

  @Test
  void shouldMergeJobUpdateWhenPossible() {
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(true);

    final var model = mock(JobDbModel.class);
    when(model.jobKey()).thenReturn(123L);

    writer.update(model);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }
}
