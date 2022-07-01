/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Builds a sequence of blocks.
 *
 * <p>Hints: Depending on random value this may also create a sequence with no block. If current
 * depth is at max depth it will only pick block builders which do not add depth.
 */
public class BlockSequenceBuilder implements BlockBuilder {

  private static final List<BlockBuilderFactory> BLOCK_BUILDER_FACTORIES =
      Arrays.asList(
          JobWorkerTaskBlockBuilder.serviceTaskFactory(),
          JobWorkerTaskBlockBuilder.businessRuleTaskFactory(),
          JobWorkerTaskBlockBuilder.scriptTaskFactory(),
          JobWorkerTaskBlockBuilder.sendTaskFactory(),
          new IntermediateMessageCatchEventBlockBuilder.Factory(),
          new SubProcessBlockBuilder.Factory(),
          new ExclusiveGatewayBlockBuilder.Factory(),
          new ParallelGatewayBlockBuilder.Factory(),
          new ReceiveTaskBlockBuilder.Factory(),
          new EventBasedGatewayBlockBuilder.Factory(),
          new ReceiveTaskBlockBuilder.Factory(),
          new CallActivityBlockBuilder.Factory(),
          new UserTaskBlockBuilder.Factory(),
          new ManualTaskBlockBuilder.Factory(),
          new IntermediateThrowEventBlockBuilder.Factory());

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();
  private final String dummyElementId;

  public BlockSequenceBuilder(final ConstructionContext context) {
    dummyElementId = context.getIdGenerator().nextId();
    final Random random = context.getRandom();
    final int maxDepth = context.getMaxDepth();
    final int maxBlocks = context.getMaxBlocks();

    final int currentDepth = context.getCurrentDepth();

    if (currentDepth < maxDepth) {

      // reduce the number of steps in a sequence when we are nested and guarantee at least 1 step
      final int steps = random.nextInt(Math.max(0, maxBlocks - currentDepth)) + 1;

      for (int step = 0; step < steps; step++) {

        BlockBuilderFactory blockBuilderFactory;
        do {
          blockBuilderFactory =
              BLOCK_BUILDER_FACTORIES.get(random.nextInt(BLOCK_BUILDER_FACTORIES.size()));
        } while ((currentDepth == maxDepth - 1) && (blockBuilderFactory.isAddingDepth()));

        blockBuilders.add(blockBuilderFactory.createBlockBuilder(context));
      }
    }
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    AbstractFlowNodeBuilder<?, ?> processWorkInProgress = nodeBuilder;

    for (final BlockBuilder builder : blockBuilders) {
      processWorkInProgress = builder.buildFlowNodes(processWorkInProgress);
    }

    return processWorkInProgress;
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    blockBuilders.forEach(
        blockBuilder -> result.append(blockBuilder.findRandomExecutionPath(context)));

    return result;
  }

  /**
   * The BlockSequenceBuilder is a special case. This is not an executable block, but is responsible
   * for building the sequence of blocks. It has no sensible element id, yet it must be a unique
   * value, so a dummy element id is generated and returned here.
   */
  @Override
  public String getElementId() {
    return dummyElementId;
  }

  @Override
  public List<BlockBuilder> getPossibleStartingBlocks() {
    final List<BlockBuilder> allBlockBuilders = new ArrayList<>();
    blockBuilders.forEach(
        blockBuilder -> allBlockBuilders.addAll(blockBuilder.getPossibleStartingBlocks()));
    return allBlockBuilders;
  }

  @Override
  public boolean equalsOrContains(final String startingElementId) {
    return getElementId().equals(startingElementId)
        || getPossibleStartingElementIds().contains(startingElementId);
  }

  /**
   * When building a parallel gateway we need to know the element id of the next element in the
   * process as we need to append it to the list of start elements. Since each branch of the
   * parallel gateway is a BlockSequenceBuilder, we cannot use the element id of this block as it is
   * a dummy id (we cannot start at a BlockSequenceBlock). Instead, we need to return the element id
   * of the first block inside this block.
   *
   * @return the element id of the block inside this block
   */
  public String getFirstBlockElementId() {
    return blockBuilders.get(0).getElementId();
  }

  public static class BlockSequenceBuilderFactory {

    public BlockSequenceBuilder createBlockSequenceBuilder(final ConstructionContext context) {
      return new BlockSequenceBuilder(context);
    }
  }
}
