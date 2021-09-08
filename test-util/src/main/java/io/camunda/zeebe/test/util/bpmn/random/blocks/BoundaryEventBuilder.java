/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;

public class BoundaryEventBuilder {

  private final String taskId;
  private final String joinGatewayId;
  private final ExclusiveGatewayBuilder exclusiveGatewayBuilder;

  public BoundaryEventBuilder(
      final String taskId, final AbstractActivityBuilder<?, ?> taskBuilder) {
    this.taskId = taskId;
    joinGatewayId = "boundary_join_" + taskId;
    exclusiveGatewayBuilder = taskBuilder.exclusiveGateway(joinGatewayId);
  }

  public AbstractFlowNodeBuilder<?, ?> connectBoundaryErrorEvent(
      final String boundaryEventId, final String errorCode) {
    return ((AbstractActivityBuilder<?, ?>) exclusiveGatewayBuilder.moveToNode(taskId))
        .boundaryEvent(boundaryEventId, b -> b.error(errorCode))
        .connectTo(joinGatewayId);
  }

  public AbstractFlowNodeBuilder<?, ?> connectBoundaryTimerEvent(final String boundaryEventId) {
    return ((AbstractActivityBuilder<?, ?>) exclusiveGatewayBuilder.moveToNode(taskId))
        .boundaryEvent(boundaryEventId, b -> b.timerWithDurationExpression(boundaryEventId))
        .connectTo(joinGatewayId);
  }
}
