/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.engine.util.random.steps.ActivateAndCompleteJob;
import io.zeebe.engine.util.random.steps.ActivateAndFailJob;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import java.util.Random;

public class ServiceTaskBlockBuilder implements BlockBuilder {

  private final String serviceTaskId;
  private final String jobType;

  public ServiceTaskBlockBuilder(IDGenerator idGenerator) {
    serviceTaskId = idGenerator.nextId();
    jobType = "job_" + serviceTaskId;
  }

  @Override
  public AbstractFlowNodeBuilder buildFlowNodes(AbstractFlowNodeBuilder nodeBuilder) {
    ServiceTaskBuilder result = nodeBuilder.serviceTask(serviceTaskId);

    result.zeebeJobRetries("3");

    result.zeebeJobType("job_" + serviceTaskId);

    return result;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    if (random.nextBoolean()) {
      result.append(new ActivateAndFailJob(jobType));
    }
    result.append(new ActivateAndCompleteJob(jobType));

    return result;
  }
}
