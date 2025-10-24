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
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper.EndProcessInstanceDto;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper.ProcessInstanceTagsDto;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpdateHistoryCleanupDateMerger;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class ProcessInstanceWriter {

  private static final int PROCESS_INSTANCE_TAG_INSERT_BLOCK_SIZE = 10000;

  private final ProcessInstanceMapper mapper;
  private final ExecutionQueue executionQueue;

  public ProcessInstanceWriter(
      final ProcessInstanceMapper mapper, final ExecutionQueue executionQueue) {
    this.mapper = mapper;
    this.executionQueue = executionQueue;
  }

  public void create(final ProcessInstanceDbModel processInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            processInstance.processInstanceKey(),
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insert",
            processInstance));
  }

  public void update(final ProcessInstanceDbModel processInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.UPDATE,
            processInstance.processInstanceKey(),
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.update",
            processInstance));
  }

  public void finish(
      final long key, final ProcessInstanceState state, final OffsetDateTime endDate) {
    final boolean wasMerged = mergeToQueue(key, b -> b.state(state).endDate(endDate));

    if (!wasMerged) {
      final var dto = new EndProcessInstanceDto(key, state, endDate);
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              WriteStatementType.UPDATE,
              key,
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateStateAndEndDate",
              dto));
    }
  }

  public void createIncident(final long key) {
    final boolean wasMerged = mergeToQueue(key, b -> b.numIncidents(b.numIncidents() + 1));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              WriteStatementType.UPDATE,
              key,
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.incrementIncidentCount",
              key));
    }
  }

  public void resolveIncident(final long key) {
    final boolean wasMerged = mergeToQueue(key, b -> b.numIncidents(b.numIncidents() - 1));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              WriteStatementType.UPDATE,
              key,
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.decrementIncidentCount",
              key));
    }
  }

  public void createTags(final ProcessInstanceTagsDto tagList) {
    for (int i = 0; i < tagList.tags().size(); i += PROCESS_INSTANCE_TAG_INSERT_BLOCK_SIZE) {
      final var block =
          tagList
              .tags()
              .subList(
                  i, Math.min(i + PROCESS_INSTANCE_TAG_INSERT_BLOCK_SIZE, tagList.tags().size()));
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              WriteStatementType.INSERT,
              tagList.processInstanceKey(),
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insertTags",
              new ProcessInstanceTagsDto(tagList.processInstanceKey(), block)));
    }
  }

  private boolean mergeToQueue(
      final long key,
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(
            ContextType.PROCESS_INSTANCE, key, ProcessInstanceDbModel.class, mergeFunction));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new UpdateHistoryCleanupDateMerger(
                ContextType.PROCESS_INSTANCE, processInstanceKey, historyCleanupDate));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.PROCESS_INSTANCE,
              WriteStatementType.UPDATE,
              processInstanceKey,
              "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateHistoryCleanupDate",
              new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                  .processInstanceKey(processInstanceKey)
                  .historyCleanupDate(historyCleanupDate)
                  .build()));
    }
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
