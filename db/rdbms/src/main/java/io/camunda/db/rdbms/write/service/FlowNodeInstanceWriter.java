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

  public void end(final long flowNodeKey, final FlowNodeState state, final OffsetDateTime endDate) {
    final var dto = new EndFlowNodeDto(flowNodeKey, state, endDate);
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.FLOW_NODE,
            flowNodeKey,
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate",
            dto));
  }
}
