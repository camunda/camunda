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

import io.camunda.db.rdbms.sql.UsageMetricTUMapper;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class UsageMetricTUWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final UsageMetricTUMapper mapper = mock(UsageMetricTUMapper.class);
  private final UsageMetricTUWriter writer = new UsageMetricTUWriter(executionQueue, mapper);

  @Test
  void shouldCreateUsageMetricTU() {
    final var model = mock(UsageMetricTUDbModel.class);
    when(model.getId()).thenReturn("metric1");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.USAGE_METRIC_TU,
                    WriteStatementType.INSERT,
                    "metric1",
                    "io.camunda.db.rdbms.sql.UsageMetricTUMapper.insert",
                    model)));
  }
}
