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
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditLogWriterTest {

  private ExecutionQueue executionQueue;
  private AuditLogMapper mapper;
  private VendorDatabaseProperties vendorDatabaseProperties;
  private AuditLogWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    mapper = mock(AuditLogMapper.class);
    vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    writer = new AuditLogWriter(executionQueue, mapper, vendorDatabaseProperties);
  }

  @Test
  void shouldCreateAuditLog() {
    final var model = new AuditLogDbModel.Builder().entityKey("key1").build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
