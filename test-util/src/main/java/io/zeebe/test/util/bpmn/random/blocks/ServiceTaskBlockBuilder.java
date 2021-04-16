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
import io.zeebe.test.util.bpmn.random.steps.StepActivateJobAndThrowError;
import java.util.Random;

/** Generates a service task. The service task may have boundary events */
public class ServiceTaskBlockBuilder implements BlockBuilder {

  private final String serviceTaskId;
  private final String jobType;
  private final String errorCode;

  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryErrorEvent;

  public ServiceTaskBlockBuilder(final IDGenerator idGenerator, final Random random) {
    serviceTaskId = idGenerator.nextId();
    jobType = "job_" + serviceTaskId;
    errorCode = "error_" + serviceTaskId;

    hasBoundaryErrorEvent =
        random.nextInt(100) < RandomProcessGenerator.PROBABILITY_BOUNDARY_ERROR_EVENT;

    hasBoundaryEvents = hasBoundaryErrorEvent; // extend here for additional boundary events
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final ServiceTaskBuilder serviceTaskBuilder = nodeBuilder.serviceTask(serviceTaskId);

    serviceTaskBuilder.zeebeJobRetries("3");

    serviceTaskBuilder.zeebeJobType(jobType);

    AbstractFlowNodeBuilder<?, ?> result = serviceTaskBuilder;

    if (hasBoundaryEvents) {
      final String joinGatewayId = "join_" + serviceTaskId;
      final ExclusiveGatewayBuilder exclusiveGatewayBuilder =
          serviceTaskBuilder.exclusiveGateway(joinGatewayId);

      if (hasBoundaryErrorEvent) {
        result =
            ((ServiceTaskBuilder) exclusiveGatewayBuilder.moveToNode(serviceTaskId))
                .boundaryEvent("boundary_error_" + serviceTaskId, b -> b.error(errorCode))
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

    result.append(buildStepsForFailedExecutions(random));

    result.append(buildStepForSuccessfulExecution(random));

    return result;
  }

  private ExecutionPathSegment buildStepsForFailedExecutions(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    if (random.nextBoolean()) {
      result.append(new StepActivateAndTimeoutJob(jobType));
    }

    if (random.nextBoolean()) {
      final boolean updateRetries = random.nextBoolean();
      result.append(new StepActivateAndFailJob(jobType, updateRetries));
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
