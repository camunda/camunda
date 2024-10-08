/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.VariableMapper.VariableFilter;
import java.util.List;

public class VariableRdbmsService {

  private final ExecutionQueue executionQueue;
  private final VariableMapper variableMapper;

  public VariableRdbmsService(
      final ExecutionQueue executionQueue, final VariableMapper variableMapper) {
    this.executionQueue = executionQueue;
    this.variableMapper = variableMapper;
  }

  public void create(final VariableModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.key(),
            "io.camunda.db.rdbms.sql.VariableMapper.insert",
            variable));
  }

  public void update(final VariableModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.VARIABLE,
            variable.key(),
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            variable));
  }

  public VariableModel findOne(final Long key) {
    return variableMapper.findOne(key);
  }

  public List<VariableModel> findByProcessInstanceKey(final Long processInstanceKey) {
    return variableMapper.find(new VariableFilter(processInstanceKey));
  }
}
