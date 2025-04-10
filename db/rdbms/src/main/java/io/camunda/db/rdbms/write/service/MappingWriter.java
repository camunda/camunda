/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.MappingDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class MappingWriter {

  private final ExecutionQueue executionQueue;

  public MappingWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final MappingDbModel mapping) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MAPPING,
            WriteStatementType.INSERT,
            mapping.mappingKey(),
            "io.camunda.db.rdbms.sql.MappingMapper.insert",
            mapping));
  }

  public void delete(final String mappingId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MAPPING,
            WriteStatementType.DELETE,
            mappingId,
            "io.camunda.db.rdbms.sql.MappingMapper.delete",
            mappingId));
  }
}
