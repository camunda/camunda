/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.AuditLogMapper.BatchInsertAuditLogsDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class AuditLogWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final AuditLogMapper mapper = mock(AuditLogMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final RdbmsWriterConfig config = mock(RdbmsWriterConfig.class);
  private final AuditLogWriter writer =
      new AuditLogWriter(executionQueue, mapper, vendorDatabaseProperties, config);

  @Test
  void shouldCreateAuditLog() {
    final var model = new AuditLogDbModel.Builder().entityKey("key1").build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.contextType() == ContextType.AUDIT_LOG
                        && queueItem.statementType() == WriteStatementType.INSERT
                        && queueItem
                            .statementId()
                            .equals("io.camunda.db.rdbms.sql.AuditLogMapper.insert")
                        && queueItem.parameter() instanceof BatchInsertAuditLogsDto
                        && ((BatchInsertAuditLogsDto) queueItem.parameter()).auditLogs().size() == 1
                        && ((BatchInsertAuditLogsDto) queueItem.parameter())
                            .auditLogs()
                            .getFirst()
                            .equals(model)));
  }
}
