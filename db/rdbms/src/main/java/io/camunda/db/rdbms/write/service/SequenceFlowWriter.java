/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class SequenceFlowWriter {

  private final ExecutionQueue executionQueue;
  private final SequenceFlowMapper mapper;

  public SequenceFlowWriter(final ExecutionQueue executionQueue, final SequenceFlowMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
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

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.SEQUENCE_FLOW,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.SequenceFlowMapper.updateHistoryCleanupDate",
            new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
