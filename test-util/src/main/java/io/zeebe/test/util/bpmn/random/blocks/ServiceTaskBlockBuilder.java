/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
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

    hasBoundaryErrorEvent = random.nextInt(100) < 10;

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

  public static final class StepActivateAndCompleteJob extends AbstractExecutionStep {
    private final String jobType;

    public StepActivateAndCompleteJob(final String jobType) {
      this.jobType = jobType;
    }

    public String getJobType() {
      return jobType;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepActivateAndCompleteJob that = (StepActivateAndCompleteJob) o;

      if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = jobType != null ? jobType.hashCode() : 0;
      result = 31 * result + variables.hashCode();
      return result;
    }
  }

  public static final class StepActivateAndFailJob extends AbstractExecutionStep {
    private final String jobType;
    private final boolean updateRetries;

    public StepActivateAndFailJob(final String jobType, final boolean updateRetries) {
      this.jobType = jobType;
      this.updateRetries = updateRetries;
    }

    public String getJobType() {
      return jobType;
    }

    public boolean isUpdateRetries() {
      return updateRetries;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepActivateAndFailJob that = (StepActivateAndFailJob) o;

      if (updateRetries != that.updateRetries) {
        return false;
      }
      if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = jobType != null ? jobType.hashCode() : 0;
      result = 31 * result + (updateRetries ? 1 : 0);
      result = 31 * result + variables.hashCode();
      return result;
    }
  }

  public static final class StepActivateAndTimeoutJob extends AbstractExecutionStep {
    private final String jobType;

    public StepActivateAndTimeoutJob(final String jobType) {
      this.jobType = jobType;
    }

    public String getJobType() {
      return jobType;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepActivateAndTimeoutJob that = (StepActivateAndTimeoutJob) o;

      if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = jobType != null ? jobType.hashCode() : 0;
      result = 31 * result + variables.hashCode();
      return result;
    }
  }

  public static class StepActivateJobAndThrowError extends AbstractExecutionStep {

    private final String jobType;
    private final String errorCode;

    public StepActivateJobAndThrowError(final String jobType, final String errorCode) {
      super();
      this.jobType = jobType;
      this.errorCode = errorCode;
    }

    public String getJobType() {
      return jobType;
    }

    public String getErrorCode() {
      return errorCode;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepActivateJobAndThrowError that = (StepActivateJobAndThrowError) o;

      if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
        return false;
      }
      if (errorCode != null ? !errorCode.equals(that.errorCode) : that.errorCode != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = jobType != null ? jobType.hashCode() : 0;
      result = errorCode != null ? errorCode.hashCode() : 0;
      result = 31 * result + variables.hashCode();
      return result;
    }
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
