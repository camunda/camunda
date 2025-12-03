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
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentWriterTest {

  private ExecutionQueue executionQueue;
  private IncidentMapper mapper;
  private VendorDatabaseProperties vendorDatabaseProperties;
  private IncidentWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    mapper = mock(IncidentMapper.class);
    vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    writer = new IncidentWriter(executionQueue, mapper, vendorDatabaseProperties);
  }

  @Test
  void shouldCreateIncident() {
    final var model = new IncidentDbModel.Builder().incidentKey(123L).errorMessage("error").build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateIncident() {
    final var model = new IncidentDbModel.Builder().incidentKey(123L).errorMessage("error").build();

    writer.update(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldResolveIncidentWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    writer.resolve(123L);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
