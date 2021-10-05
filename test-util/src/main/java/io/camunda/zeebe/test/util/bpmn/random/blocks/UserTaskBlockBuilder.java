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
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepCompleteUserTask;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepThrowError;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerBoundaryEvent;
import java.util.Random;

/** Generates a user task */
public class UserTaskBlockBuilder implements BlockBuilder {

  private final String taskId;
  private final String boundaryErrorEventId;
  private final String boundaryTimerEventId;
  private final String errorCode;
  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryErrorEvent;
  private final boolean hasBoundaryTimerEvent;

  public UserTaskBlockBuilder(final ConstructionContext context) {
    final IDGenerator idGenerator = context.getIdGenerator();
    final Random random = context.getRandom();

    taskId = idGenerator.nextId();
    boundaryErrorEventId = "boundary_error_" + taskId;
    boundaryTimerEventId = "boundary_timer_" + taskId;
    errorCode = "error_" + taskId;

    hasBoundaryErrorEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_ERROR_EVENT;
    hasBoundaryTimerEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_TIMER_EVENT;
    hasBoundaryEvents = hasBoundaryErrorEvent || hasBoundaryTimerEvent;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final UserTaskBuilder taskBuilder = nodeBuilder.userTask(taskId).name(taskId);
    AbstractFlowNodeBuilder<?, ?> result = taskBuilder;

    if (hasBoundaryEvents) {
      final BoundaryEventBuilder boundaryEventBuilder =
          new BoundaryEventBuilder(taskId, taskBuilder);

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
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();
    final var activateStep = new StepActivateBPMNElement(taskId);
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
      result.appendExecutionSuccessor(new StepThrowError(taskId, errorCode), activateStep);
    } else {
      result.appendExecutionSuccessor(new StepCompleteUserTask(taskId), activateStep);
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
