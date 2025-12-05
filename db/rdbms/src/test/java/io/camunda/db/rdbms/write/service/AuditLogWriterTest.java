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

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class AuditLogWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AuditLogMapper mapper = mock(AuditLogMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final AuditLogWriter writer =
      new AuditLogWriter(executionQueue, mapper, vendorDatabaseProperties);

  @Test
  void shouldCreateAuditLog() {
    final var model = new AuditLogDbModel.Builder().entityKey("key1").build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.AUDIT_LOG,
                    WriteStatementType.INSERT,
                    model.entityKey(),
                    "io.camunda.db.rdbms.sql.AuditLogMapper.insert",
                    model)));
  }
}
