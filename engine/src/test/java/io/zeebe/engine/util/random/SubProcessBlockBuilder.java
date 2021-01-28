/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import java.util.Random;

public class SubProcessBlockBuilder implements BlockBuilder {

  private BlockBuilder empbeddedSubProcessBuilder;
  private final String subProcessId;
  private final String subProcessStartEventId;
  private final String subProcessEndEventId;

  public SubProcessBlockBuilder(ConstructionContext context) {
    Random random = context.getRandom();
    IDGenerator idGenerator = context.getIdGenerator();
    BlockSequenceBuilderFactory factory = context.getBlockSequenceBuilderFactory();
    int maxDepth = context.getMaxDepth();
    int currentDepth = context.getCurrentDepth();

    subProcessId = idGenerator.nextId();
    subProcessStartEventId = idGenerator.nextId();
    subProcessEndEventId = idGenerator.nextId();

    boolean goDeeper = random.nextInt(maxDepth) > currentDepth;

    if (goDeeper) {
      empbeddedSubProcessBuilder =
          factory.createBlockSequenceBuilder(context.withIncrementedDepth());
    }
  }

  @Override
  public AbstractFlowNodeBuilder buildFlowNodes(AbstractFlowNodeBuilder nodeBuilder) {
    SubProcessBuilder subProcessBuilder = nodeBuilder.subProcess(subProcessId);

    AbstractFlowNodeBuilder workInProgress =
        subProcessBuilder.embeddedSubProcess().startEvent(subProcessStartEventId);

    if (empbeddedSubProcessBuilder != null) {
      workInProgress = empbeddedSubProcessBuilder.buildFlowNodes(workInProgress);
    }

    return workInProgress.endEvent(subProcessEndEventId).subProcessDone();
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    if (empbeddedSubProcessBuilder != null) {
      result.append(empbeddedSubProcessBuilder.findRandomExecutionPath(random));
    }

    return result;
  }
}
