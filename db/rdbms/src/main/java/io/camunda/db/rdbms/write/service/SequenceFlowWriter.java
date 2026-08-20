/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class SequenceFlowWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public SequenceFlowWriter(final ExecutionQueue executionQueue, final SequenceFlowMapper mapper) {
    super(mapper);
    this.executionQueue = executionQueue;
  }

  public void create(final SequenceFlowDbModel sequenceFlow) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.SEQUENCE_FLOW,
            WriteStatementType.INSERT,
            sequenceFlow.sequenceFlowId(),
            "io.camunda.db.rdbms.sql.SequenceFlowMapper.insert",
            sequenceFlow));
  }

  public void createIfNotExists(final SequenceFlowDbModel sequenceFlow) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.SEQUENCE_FLOW,
            WriteStatementType.INSERT,
            sequenceFlow.sequenceFlowId(),
            "io.camunda.db.rdbms.sql.SequenceFlowMapper.createIfNotExists",
            sequenceFlow));
  }

  public void delete(final SequenceFlowDbModel sequenceFlow) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.SEQUENCE_FLOW,
            WriteStatementType.DELETE,
            sequenceFlow.sequenceFlowId(),
            "io.camunda.db.rdbms.sql.SequenceFlowMapper.delete",
            sequenceFlow));
  }
}
