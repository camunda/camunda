/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepCompleteUserTask;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepThrowError;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerBoundaryEvent;
import java.util.Random;

/** Generates a user task */
public class UserTaskBlockBuilder extends AbstractBlockBuilder {

  private final String boundaryErrorEventId;
  private final String boundaryTimerEventId;
  private final String errorCode;
  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryErrorEvent;
  private final boolean hasBoundaryTimerEvent;

  public UserTaskBlockBuilder(final ConstructionContext context) {
    super(context.getIdGenerator().nextId());
    final Random random = context.getRandom();

    boundaryErrorEventId = "boundary_error_" + elementId;
    boundaryTimerEventId = "boundary_timer_" + elementId;
    errorCode = "error_" + elementId;

    hasBoundaryErrorEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_ERROR_EVENT;
    hasBoundaryTimerEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_TIMER_EVENT;
    hasBoundaryEvents = hasBoundaryErrorEvent || hasBoundaryTimerEvent;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final UserTaskBuilder taskBuilder = nodeBuilder.userTask(getElementId()).name(getElementId());
    AbstractFlowNodeBuilder<?, ?> result = taskBuilder;

    if (hasBoundaryEvents) {
      final BoundaryEventBuilder boundaryEventBuilder =
          new BoundaryEventBuilder(getElementId(), taskBuilder);

      if (hasBoundaryErrorEvent) {
        result = boundaryEventBuilder.connectBoundaryErrorEvent(boundaryErrorEventId, errorCode);
      }

      if (hasBoundaryTimerEvent) {
        result = boundaryEventBuilder.connectBoundaryTimerEvent(boundaryTimerEventId);
      }
    }

    return result;
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();
    final Random random = context.getRandom();

    final var activateStep = new StepActivateBPMNElement(getElementId());
    result.appendDirectSuccessor(activateStep);

    if (hasBoundaryTimerEvent) {
      // set an infinite timer as default; this can be overwritten by the execution path chosen
      result.setVariableDefault(
          boundaryTimerEventId, AbstractExecutionStep.VIRTUALLY_INFINITE.toString());
    }

    if (hasBoundaryTimerEvent && random.nextBoolean()) {
      result.appendExecutionSuccessor(
          new StepTriggerTimerBoundaryEvent(boundaryTimerEventId), activateStep);
    } else if (hasBoundaryErrorEvent && random.nextBoolean()) {
      result.appendExecutionSuccessor(new StepThrowError(getElementId(), errorCode), activateStep);
    } else {
      result.appendExecutionSuccessor(new StepCompleteUserTask(getElementId()), activateStep);
    }

    return result;
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new UserTaskBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
