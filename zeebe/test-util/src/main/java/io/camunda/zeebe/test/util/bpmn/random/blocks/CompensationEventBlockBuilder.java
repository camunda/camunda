/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;

public class CompensationEventBlockBuilder extends AbstractBlockBuilder {

  private final String compensationActivityId;
  private final String compensationActivityJobType;

  private final String compensationHandlerId;
  private final String compensationHandlerJobType;

  private final String compensationThrowEventId;

  public CompensationEventBlockBuilder(final ConstructionContext context) {
    super(context.getIdGenerator().nextId());

    compensationActivityId = getElementId();
    compensationActivityJobType = "job_" + compensationActivityId;
    compensationHandlerId = "compensation_handler_" + getElementId();
    compensationHandlerJobType = "job_" + compensationHandlerId;
    compensationThrowEventId = "compensation_throw_" + getElementId();
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final var compensationActivity = nodeBuilder.serviceTask(compensationActivityId);
    compensationActivity
        .zeebeJobType(compensationActivityJobType)
        .boundaryEvent()
        .compensation(
            compensation ->
                compensation
                    .serviceTask(compensationHandlerId)
                    .zeebeJobType(compensationHandlerJobType));

    return compensationActivity
        .intermediateThrowEvent(compensationThrowEventId)
        .compensateEventDefinition()
        .compensateEventDefinitionDone();
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final var result = new ExecutionPathSegment();
    result.appendDirectSuccessor(
        new StepActivateAndCompleteJob(compensationActivityJobType, compensationActivityId));
    result.appendDirectSuccessor(
        new StepActivateAndCompleteJob(compensationHandlerJobType, compensationHandlerId));
    return result;
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new CompensationEventBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
