/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractJobWorkerTaskBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.RandomProcessGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndCompleteJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndFailJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateAndTimeoutJob;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateJobAndThrowError;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepTriggerTimerBoundaryEvent;
import java.util.Random;
import java.util.function.Function;

/**
 * Generates a task that is based on a job and is processed by a job worker (e.g. a service task).
 * The task may have boundary events
 */
public class JobWorkerTaskBlockBuilder implements BlockBuilder {

  private final String taskId;
  private final String jobType;
  private final String errorCode;
  private final String boundaryErrorEventId;
  private final String boundaryTimerEventId;

  private final boolean hasBoundaryEvents;
  private final boolean hasBoundaryErrorEvent;
  private final boolean hasBoundaryTimerEvent;

  private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>>
      taskBuilder;

  public JobWorkerTaskBlockBuilder(
      final IDGenerator idGenerator,
      final Random random,
      final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>>
          taskBuilder) {
    this.taskBuilder = taskBuilder;

    taskId = idGenerator.nextId();
    jobType = "job_" + taskId;
    errorCode = "error_" + taskId;

    boundaryErrorEventId = "boundary_error_" + taskId;
    boundaryTimerEventId = "boundary_timer_" + taskId;

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

    final var jobWorkerTaskBuilder = taskBuilder.apply(nodeBuilder);

    jobWorkerTaskBuilder.id(taskId).name(taskId);
    jobWorkerTaskBuilder.zeebeJobRetries("3");
    jobWorkerTaskBuilder.zeebeJobType(jobType);

    AbstractFlowNodeBuilder<?, ?> result = jobWorkerTaskBuilder;

    if (hasBoundaryEvents) {
      final BoundaryEventBuilder boundaryEventBuilder =
          new BoundaryEventBuilder(taskId, jobWorkerTaskBuilder);

      if (hasBoundaryErrorEvent) {
        result = boundaryEventBuilder.connectBoundaryErrorEvent(boundaryErrorEventId, errorCode);
      }

      if (hasBoundaryTimerEvent) {
        result = boundaryEventBuilder.connectBoundaryTimerEvent(boundaryTimerEventId);
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

    final var activateStep = new StepActivateBPMNElement(taskId);
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
      result.appendDirectSuccessor(new StepActivateAndTimeoutJob(jobType, taskId));
    }

    if (random.nextBoolean()) {
      final boolean updateRetries = random.nextBoolean();
      result.appendDirectSuccessor(new StepActivateAndFailJob(jobType, updateRetries, taskId));
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
      result = new StepActivateJobAndThrowError(jobType, errorCode, taskId);
    } else if (hasBoundaryTimerEvent && random.nextBoolean()) {
      result = new StepTriggerTimerBoundaryEvent(boundaryTimerEventId);
    } else {
      result = new StepActivateAndCompleteJob(jobType, taskId);
    }

    return result;
  }

  public static BlockBuilderFactory serviceTaskFactory() {
    return new Factory(AbstractFlowNodeBuilder::serviceTask);
  }

  public static BlockBuilderFactory businessRuleTaskFactory() {
    return new Factory(AbstractFlowNodeBuilder::businessRuleTask);
  }

  public static BlockBuilderFactory scriptTaskFactory() {
    return new Factory(AbstractFlowNodeBuilder::scriptTask);
  }

  public static BlockBuilderFactory sendTaskFactory() {
    return new Factory(AbstractFlowNodeBuilder::sendTask);
  }

  private static class Factory implements BlockBuilderFactory {

    private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>>
        taskBuilder;

    public Factory(
        final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>>
            taskBuilder) {
      this.taskBuilder = taskBuilder;
    }

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new JobWorkerTaskBlockBuilder(
          context.getIdGenerator(), context.getRandom(), taskBuilder);
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
