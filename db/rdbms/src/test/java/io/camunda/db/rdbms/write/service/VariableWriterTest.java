/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.Test;

class VariableWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final VariableMapper mapper = mock(VariableMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final VariableWriter writer =
      new VariableWriter(executionQueue, mapper, vendorDatabaseProperties);

  @Test
  void shouldCreateVariable() {
    final var model = mock(VariableDbModel.class);

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateVariable() {
    final var model = mock(VariableDbModel.class);

    writer.update(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
