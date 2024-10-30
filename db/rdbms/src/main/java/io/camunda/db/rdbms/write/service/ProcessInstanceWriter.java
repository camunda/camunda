/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper.EndProcessInstanceDto;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;

public class ProcessInstanceWriter {

  private final ExecutionQueue executionQueue;

  public ProcessInstanceWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final ProcessInstanceDbModel processInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            processInstance.processInstanceKey(),
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insert",
            processInstance));
  }

  public void end(
      final long processInstanceKey,
      final ProcessInstanceState state,
      final OffsetDateTime endDate) {
    final var dto = new EndProcessInstanceDto(processInstanceKey, state, endDate);
    final boolean wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new ProcessInstanceWriter.EndProcessInstanceToInsertMerger(dto));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              processInstanceKey,
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateStateAndEndDate",
              dto));
    }
  }

  public static class EndProcessInstanceToInsertMerger implements QueueItemMerger {

    private final EndProcessInstanceDto dto;

    public EndProcessInstanceToInsertMerger(final EndProcessInstanceDto dto) {
      this.dto = dto;
    }

    @Override
    public boolean canBeMerged(final QueueItem queueItem) {
      return queueItem.contextType() == ContextType.PROCESS_INSTANCE
          && queueItem.id().equals(dto.processInstanceKey())
          && queueItem.parameter() instanceof ProcessInstanceDbModel;
    }

    @Override
    public QueueItem merge(final QueueItem originalItem) {
      return new QueueItem(
          originalItem.contextType(),
          originalItem.id(),
          originalItem.statementId(),
          ((ProcessInstanceDbModel) originalItem.parameter())
              .copy(b -> b
                  .state(dto.state())
                  .endDate(dto.endDate())
              ));
    }
  }
}
