/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class ExporterPositionService {

  private final ExecutionQueue executionQueue;
  private final ExporterPositionMapper exporterPositionMapper;

  public ExporterPositionService(
      final ExecutionQueue executionQueue, final ExporterPositionMapper exporterPositionMapper) {
    this.executionQueue = executionQueue;
    this.exporterPositionMapper = exporterPositionMapper;
  }

  public void create(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            WriteStatementType.INSERT,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.insert",
            variable));
  }

  public void createWithoutQueue(final ExporterPositionModel positionModel) {
    exporterPositionMapper.insert(positionModel);
  }

  public void update(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            WriteStatementType.UPDATE,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.update",
            variable));
  }

  public ExporterPositionModel findOne(final int key) {
    return exporterPositionMapper.findOne(key);
  }
}
