/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.EndFlowNodeDto;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceWriter {

  private static final Logger LOG = LoggerFactory.getLogger(FlowNodeInstanceWriter.class);

  private final ExecutionQueue executionQueue;

  public FlowNodeInstanceWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final FlowNodeInstanceDbModel flowNode) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.FLOW_NODE,
            flowNode.flowNodeInstanceKey(),
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.insert",
            flowNode));
  }

  public void end(
      final long flowNodeInstanceKey, final FlowNodeState state, final OffsetDateTime endDate) {
    final var dto = new EndFlowNodeDto(flowNodeInstanceKey, state, endDate);
    final boolean wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(new EndFlowNodeToInsertMerger(dto));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              flowNodeInstanceKey,
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate",
              dto));
    }
  }

  public static class EndFlowNodeToInsertMerger implements QueueItemMerger {

    private final EndFlowNodeDto dto;

    public EndFlowNodeToInsertMerger(final EndFlowNodeDto dto) {
      this.dto = dto;
    }

    @Override
    public boolean canBeMerged(final QueueItem queueItem) {
      return queueItem.contextType() == ContextType.FLOW_NODE
          && queueItem.id().equals(dto.flowNodeInstanceKey())
          && queueItem.parameter() instanceof FlowNodeInstanceDbModel;
    }

    @Override
    public QueueItem merge(final QueueItem originalItem) {
      final var newParameter =
          ((FlowNodeInstanceDbModel) originalItem.parameter())
              .copy(b -> b
                  .state(dto.state())
                  .endDate(dto.endDate())
              );

      return new QueueItem(
          originalItem.contextType(), originalItem.id(), originalItem.statementId(), newParameter);
    }
  }
}
