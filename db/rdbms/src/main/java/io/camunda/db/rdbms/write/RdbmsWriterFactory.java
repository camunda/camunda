/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsWriterFactory {

  private final SqlSessionFactory sqlSessionFactory;
  private final ExporterPositionMapper exporterPositionMapper;

  public RdbmsWriterFactory(
      final SqlSessionFactory sqlSessionFactory,
      final ExporterPositionMapper exporterPositionMapper) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.exporterPositionMapper = exporterPositionMapper;
  }

  public RdbmsWriter createWriter(final long partitionId, final int queueSize) {
    final var executionQueue = new DefaultExecutionQueue(sqlSessionFactory, partitionId, queueSize);
    return new RdbmsWriter(
        executionQueue, new ExporterPositionService(executionQueue, exporterPositionMapper));
  }
}
