/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.write.domain.JobDbModel;
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
  private final JobWriter writer = new JobWriter(executionQueue, mapper, vendorDatabaseProperties);

  @Test
  void shouldCreateJob() {
    when(vendorDatabaseProperties.errorMessageSize()).thenReturn(5000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(20000);

    final var model = mock(JobDbModel.class);
    final var truncatedModel = mock(JobDbModel.class);
    when(model.truncateErrorMessage(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.jobKey()).thenReturn(123L);

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.JOB,
                    WriteStatementType.INSERT,
                    123L,
                    "io.camunda.db.rdbms.sql.JobMapper.insert",
                    truncatedModel)));
  }

  @Test
  void shouldUpdateJob() {
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
}
