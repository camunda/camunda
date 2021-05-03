/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
import io.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndFailJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateAndTimeoutJob;
import io.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;
import io.zeebe.test.util.bpmn.random.steps.StepActivateJobAndThrowError;
import io.zeebe.test.util.bpmn.random.steps.StepTriggerTimerBoundaryEvent;
import java.util.Random;

/** Generates a service task. The service task may have boundary events */
public class ServiceTaskBlockBuilder implements BlockBuilder {

  private final String serviceTaskId;
  private final String jobType;
  private final String errorCode;
  private final String boundaryErrorEventId;
  private final String boundaryTimerEventId;

  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryErrorEvent;
  private final boolean hasBoundaryTimerEvent;

  public ServiceTaskBlockBuilder(final IDGenerator idGenerator, final Random random) {
    serviceTaskId = idGenerator.nextId();
    jobType = "job_" + serviceTaskId;
    errorCode = "error_" + serviceTaskId;

    boundaryErrorEventId = "boundary_error_" + serviceTaskId;
    boundaryTimerEventId = "boundary_timer_" + serviceTaskId;

    hasBoundaryErrorEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_ERROR_EVENT;
    hasBoundaryTimerEvent =
        random.nextDouble() < RandomProcessGenerator.PROBABILITY_BOUNDARY_TIMER_EVENT;

    hasBoundaryEvents =
        hasBoundaryErrorEvent
            || hasBoundaryTimerEvent; // extend here for additional boundary events
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final ServiceTaskBuilder serviceTaskBuilder = nodeBuilder.serviceTask(serviceTaskId);

    serviceTaskBuilder.zeebeJobRetries("3");

    serviceTaskBuilder.zeebeJobType(jobType);

    AbstractFlowNodeBuilder<?, ?> result = serviceTaskBuilder;

    if (hasBoundaryEvents) {
      final String joinGatewayId = "boundary_join_" + serviceTaskId;
      final ExclusiveGatewayBuilder exclusiveGatewayBuilder =
          serviceTaskBuilder.exclusiveGateway(joinGatewayId);

      if (hasBoundaryErrorEvent) {
        result =
            ((ServiceTaskBuilder) exclusiveGatewayBuilder.moveToNode(serviceTaskId))
                .boundaryEvent(boundaryErrorEventId, b -> b.error(errorCode))
                .connectTo(joinGatewayId);
      }

      if (hasBoundaryTimerEvent) {
        result =
            ((ServiceTaskBuilder) exclusiveGatewayBuilder.moveToNode(serviceTaskId))
                .boundaryEvent(
                    /* the value of that variable will be calculated when the execution flow is
                    known*/
                    boundaryTimerEventId, b -> b.timerWithDurationExpression(boundaryTimerEventId))
                .connectTo(joinGatewayId);
      }
    }

    return result;
  }

  /**
   * This generates a sequence of one or more steps. The final step is always a successful
   * activation and complete cycle. The steps before are randomly determined failed attempts.
   */
  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    final var activateStep = new StepActivateBPMNElement(serviceTaskId);
    result.appendDirectSuccessor(activateStep);

    if (hasBoundaryTimerEvent) {
      // set an infinite timer as default; this can be overwritten by the execution path chosen
      result.setVariableDefault(
          boundaryTimerEventId, AbstractExecutionStep.VIRTUALLY_INFINITE.toString());
    }

    result.append(buildStepsForFailedExecutions(random));

    result.appendExecutionSuccessor(buildStepForSuccessfulExecution(random), activateStep);

    return result;
  }

  private ExecutionPathSegment buildStepsForFailedExecutions(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    // If we already have a timer boundary event we should not timeout the job,
    // otherwise this makes the test too fragile (flaky).
    if (!hasBoundaryTimerEvent && random.nextBoolean()) {
      result.appendDirectSuccessor(new StepActivateAndTimeoutJob(jobType));
    }

    if (random.nextBoolean()) {
      final boolean updateRetries = random.nextBoolean();
      result.appendDirectSuccessor(new StepActivateAndFailJob(jobType, updateRetries));
    }

    return result;
  }

  /**
   * This method build the step that results in a successful execution of the service task.
   * Successful execution here does not necessarily mean that the job is completed orderly.
   * Successful execution is any execution which moves the token past the service task, so that the
   * process can continue.
   */
  private AbstractExecutionStep buildStepForSuccessfulExecution(final Random random) {
    final AbstractExecutionStep result;

    if (hasBoundaryErrorEvent && random.nextBoolean()) {
      result = new StepActivateJobAndThrowError(jobType, errorCode);
    } else if (hasBoundaryTimerEvent && random.nextBoolean()) {
      result = new StepTriggerTimerBoundaryEvent(jobType, boundaryTimerEventId);
    } else {
      result = new StepActivateAndCompleteJob(jobType);
    }

    return result;
  }

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ServiceTaskBlockBuilder(context.getIdGenerator(), context.getRandom());
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
