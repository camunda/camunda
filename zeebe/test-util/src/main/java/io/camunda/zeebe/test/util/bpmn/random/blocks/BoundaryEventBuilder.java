/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractActivityBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.RandomProcessGenerator;

public class BoundaryEventBuilder {

  private final boolean timerEventHasTerminateEndEvent;
  private final boolean errorEventHasTerminateEndEvent;
  private final String flowNodeId;
  private final String joinGatewayId;
  private boolean joinGatewayCreated;

  public BoundaryEventBuilder(final ConstructionContext context, final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    joinGatewayId = "boundary_join_" + flowNodeId;
    timerEventHasTerminateEndEvent =
        context.getRandom().nextDouble() < RandomProcessGenerator.PROBABILITY_TERMINATE_END_EVENT;
    errorEventHasTerminateEndEvent =
        context.getRandom().nextDouble() < RandomProcessGenerator.PROBABILITY_TERMINATE_END_EVENT;
  }

  private AbstractFlowNodeBuilder<?, ?> connectJoinGateway(
      final AbstractFlowNodeBuilder<?, ?> flowNodeBuilder) {
    joinGatewayCreated = true;
    return flowNodeBuilder.exclusiveGateway(joinGatewayId);
  }

  public AbstractFlowNodeBuilder<?, ?> connectBoundaryErrorEvent(
      final AbstractFlowNodeBuilder<?, ?> flowNodeBuilder,
      final String errorCode,
      final String boundaryEventId) {
    var builder = flowNodeBuilder;
    if (!joinGatewayCreated && !errorEventHasTerminateEndEvent) {
      builder = connectJoinGateway(flowNodeBuilder);
    }

    final var boundaryEventBuilder =
        ((AbstractActivityBuilder<?, ?>) builder.moveToNode(flowNodeId))
            .boundaryEvent(boundaryEventId, b -> b.error(errorCode));

    if (errorEventHasTerminateEndEvent) {
      return boundaryEventBuilder.endEvent().terminate().moveToNode(decideNodeToMoveTo());
    } else {
      return boundaryEventBuilder.connectTo(joinGatewayId);
    }
  }

  public AbstractFlowNodeBuilder<?, ?> connectBoundaryTimerEvent(
      final AbstractFlowNodeBuilder<?, ?> flowNodeBuilder, final String boundaryEventId) {
    var builder = flowNodeBuilder;
    if (!joinGatewayCreated && !timerEventHasTerminateEndEvent) {
      builder = connectJoinGateway(flowNodeBuilder);
    }

    final var boundaryEventBuilder =
        ((AbstractActivityBuilder<?, ?>) builder.moveToNode(flowNodeId))
            .boundaryEvent(boundaryEventId, b -> b.timerWithDurationExpression(boundaryEventId));

    if (timerEventHasTerminateEndEvent) {
      return boundaryEventBuilder.endEvent().terminate().moveToNode(decideNodeToMoveTo());
    } else {
      return boundaryEventBuilder.connectTo(joinGatewayId);
    }
  }

  private String decideNodeToMoveTo() {
    // If a join gateway has been created we have to make sure to move to this gateway, otherwise
    // we would cause the task to get a second outgoing sequence flow.
    return joinGatewayCreated ? joinGatewayId : flowNodeId;
  }

  public boolean timerEventHasTerminateEndEvent() {
    return timerEventHasTerminateEndEvent;
  }

  public boolean errorEventHasTerminateEndEvent() {
    return errorEventHasTerminateEndEvent;
  }
}
