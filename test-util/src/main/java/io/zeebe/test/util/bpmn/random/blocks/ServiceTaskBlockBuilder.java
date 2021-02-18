/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
import java.util.Random;

/** Generates a service task * */
public class ServiceTaskBlockBuilder implements BlockBuilder {

  private final String serviceTaskId;
  private final String jobTypeId;

  public ServiceTaskBlockBuilder(final IDGenerator idGenerator) {
    serviceTaskId = idGenerator.nextId();
    jobTypeId = "job_" + serviceTaskId;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final ServiceTaskBuilder result = nodeBuilder.serviceTask(serviceTaskId);

    result.zeebeJobRetries("3");

    result.zeebeJobType("job_" + serviceTaskId);

    return result;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    if (random.nextBoolean()) {
      final boolean updateRetries = random.nextBoolean();
      result.append(new StepActivateAndFailJob(jobTypeId, updateRetries));
    }

    result.append(new StepActivateAndCompleteJob(jobTypeId));

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

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ServiceTaskBlockBuilder(context.getIdGenerator());
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
