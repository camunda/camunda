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

import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class FormWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final FormWriter writer = new FormWriter(executionQueue);

  @Test
  void shouldCreateForm() {
    final var model = new FormDbModel(123L, "form1", "Form Name", "schema", 1L, false);

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.FORM,
                    WriteStatementType.INSERT,
                    model.formKey(),
                    "io.camunda.db.rdbms.sql.FormMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateForm() {
    final var model = new FormDbModel(123L, "form1", "Updated Form", "schema", 1L, false);

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.FORM,
                    WriteStatementType.UPDATE,
                    model.formKey(),
                    "io.camunda.db.rdbms.sql.FormMapper.update",
                    model)));
  }
}
