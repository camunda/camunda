/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.Optional;
import java.util.Random;

public class RandomWorkflowBuilder {

  private static final BlockSequenceBuilderFactory factory = new BlockSequenceBuilderFactory();

  private final BlockBuilder blockBuilder;

  private final String processId;

  private final String startEventId;
  private final String endEventId;

  public RandomWorkflowBuilder(
      long seed,
      Optional<Integer> optMaxBlocks,
      Optional<Integer> optMaxDepth,
      Optional<Integer> optMaxBranches) {
    Random random = new Random(seed);

    IDGenerator idGenerator = new IDGenerator(0);

    processId = "process_" + seed;

    startEventId = idGenerator.nextId();
    endEventId = idGenerator.nextId();

    int maxBlocks = optMaxBlocks.orElse(5);
    int maxDepth = optMaxDepth.orElse(5);
    int maxBranches = optMaxBranches.orElse(5);

    ConstructionContext context =
        new ConstructionContext(random, idGenerator, factory, maxBlocks, maxDepth, maxBranches, 0);
    blockBuilder = factory.createBlockSequenceBuilder(context);
  }

  public BpmnModelInstance buildWorkflow() {

    AbstractFlowNodeBuilder workflowWorkInProgress =
        Bpmn.createExecutableProcess(processId).startEvent(startEventId);

    workflowWorkInProgress = blockBuilder.buildFlowNodes(workflowWorkInProgress);

    return workflowWorkInProgress.endEvent(endEventId).done();
  }

  public ExecutionPath findRandomExecutionPath(long seed) {
    return new ExecutionPath(processId, blockBuilder.findRandomExecutionPath(new Random(seed)));
  }
}
