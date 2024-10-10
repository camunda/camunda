/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ExporterPositionModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;

public class ExporterPositionRdbmsService {

  private final ExecutionQueue executionQueue;
  private final ExporterPositionMapper exporterPositionMapper;

  public ExporterPositionRdbmsService(
      final ExecutionQueue executionQueue, final ExporterPositionMapper exporterPositionMapper) {
    this.executionQueue = executionQueue;
    this.exporterPositionMapper = exporterPositionMapper;
  }

  public void create(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.insert",
            variable));
  }

  public void createWithoutQueue(final ExporterPositionModel positionModel) {
    this.exporterPositionMapper.insert(positionModel);
  }

  public void update(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.update",
            variable));
  }

  public ExporterPositionModel findOne(final Long key) {
    return exporterPositionMapper.findOne(key);
  }
}
