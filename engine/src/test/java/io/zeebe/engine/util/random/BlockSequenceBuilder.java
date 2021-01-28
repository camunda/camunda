/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BlockSequenceBuilder implements BlockBuilder {

  private final List<BlockBuilderFactory> BLOCK_BUILDER_FACTORIES =
      Arrays.asList(
          new ServiceTaskBlockBuilderFactory(),
          new IntermediateMessageCatchEventBlockBuilderFactory(),
          new SubProcessBlockBuilderFactory(),
          new ExclusiveGatewayBlockBuilderFactory(),
          new ParallelGatewayBlockBuilderFactory());

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();

  public BlockSequenceBuilder(ConstructionContext context) {
    final Random random = context.getRandom();
    final int maxDepth = context.getMaxDepth();
    final int maxBlocks = context.getMaxBlocks();

    int currentDepth = context.getCurrentDepth();

    if (currentDepth < maxDepth) {

      int steps =
          random.nextInt(maxBlocks)
              - currentDepth; // reduce the number of steps in a sequence when we are nested

      for (int step = 0; step <= steps; step++) {

        BlockBuilderFactory blockBuilderFactory;
        do {
          blockBuilderFactory =
              BLOCK_BUILDER_FACTORIES.get(random.nextInt(BLOCK_BUILDER_FACTORIES.size()));
        } while ((currentDepth == maxDepth - 1) && (blockBuilderFactory.isAddingDepth()));

        blockBuilders.add(blockBuilderFactory.createBlockBuilder(context));
      }
    }
  }

  public AbstractFlowNodeBuilder buildFlowNodes(final AbstractFlowNodeBuilder nodeBuilder) {
    AbstractFlowNodeBuilder workflowWorkInProgress = nodeBuilder;

    // workflowWorkInProgress = workflowWorkInProgress.sequenceFlowId(idGenerator.nextId());

    for (BlockBuilder builder : blockBuilders) {
      workflowWorkInProgress = builder.buildFlowNodes(workflowWorkInProgress);
    }

    return workflowWorkInProgress;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    blockBuilders.forEach(
        blockBuilder -> result.append(blockBuilder.findRandomExecutionPath(random)));

    return result;
  }
}
