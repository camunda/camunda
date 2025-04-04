/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper;
import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel.Builder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentWriter {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentWriter.class);

  private final ExecutionQueue executionQueue;
  private final IncidentMapper mapper;

  public IncidentWriter(final ExecutionQueue executionQueue, final IncidentMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final IncidentDbModel incident) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.INCIDENT,
            WriteStatementType.INSERT,
            incident.incidentKey(),
            "io.camunda.db.rdbms.sql.IncidentMapper.insert",
            incident));
  }

  public void update(final IncidentDbModel incident) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.INCIDENT,
            WriteStatementType.UPDATE,
            incident.incidentKey(),
            "io.camunda.db.rdbms.sql.IncidentMapper.update",
            incident));
  }

  public void resolve(final Long incidentKey) {
    final boolean wasMerged =
        mergeToQueue(incidentKey, b -> b.state(IncidentState.RESOLVED).errorMessage(null));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.INCIDENT,
              WriteStatementType.UPDATE,
              incidentKey,
              "io.camunda.db.rdbms.sql.IncidentMapper.updateState",
              new IncidentMapper.IncidentStateDto(
                  incidentKey, IncidentState.RESOLVED, null, null)));
    }
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.INCIDENT,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.IncidentMapper.updateHistoryCleanupDate",
            new HistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  private boolean mergeToQueue(final long key, final Function<Builder, Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.INCIDENT, key, IncidentDbModel.class, mergeFunction));
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
