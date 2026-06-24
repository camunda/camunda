/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;

public class ProcessDefinitionWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionWriter(
      final ProcessDefinitionMapper processDefinitionMapper, final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public void create(final ProcessDefinitionDbModel processDefinition) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_DEFINITION,
            WriteStatementType.INSERT,
            processDefinition.processDefinitionKey(),
            "io.camunda.db.rdbms.sql.ProcessDefinitionMapper.insert",
            processDefinition));
  }

  public void deleteByKeys(final List<Long> processDefinitionKeys) {
    processDefinitionMapper.deleteByKeys(processDefinitionKeys);
  }
}
