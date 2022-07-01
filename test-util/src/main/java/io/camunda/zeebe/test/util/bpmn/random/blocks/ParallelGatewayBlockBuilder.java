/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ParallelGatewayBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.ScheduledExecutionStep;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepActivateBPMNElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Generates a block with a forking parallel gateway, a random number of branches, and a joining
 * parallel gateway. Each branch has a nested sequence of blocks.
 */
public class ParallelGatewayBlockBuilder implements BlockBuilder {

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();
  private final List<String> branchIds = new ArrayList<>();
  private final String forkGatewayId;
  private final String joinGatewayId;

  public ParallelGatewayBlockBuilder(final ConstructionContext context) {
    final Random random = context.getRandom();
    final IDGenerator idGenerator = context.getIdGenerator();
    final int maxBranches = context.getMaxBranches();

    forkGatewayId = "fork_" + idGenerator.nextId();
    joinGatewayId = "join_" + idGenerator.nextId();

    final BlockSequenceBuilder.BlockSequenceBuilderFactory blockSequenceBuilderFactory =
        context.getBlockSequenceBuilderFactory();

    final int branches = Math.max(2, random.nextInt(maxBranches));

    for (int i = 0; i < branches; i++) {
      branchIds.add(idGenerator.nextId());
      blockBuilders.add(
          blockSequenceBuilderFactory.createBlockSequenceBuilder(context.withIncrementedDepth()));
    }
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final ParallelGatewayBuilder forkGateway = nodeBuilder.parallelGateway(forkGatewayId);

    AbstractFlowNodeBuilder<?, ?> workInProgress =
        blockBuilders.get(0).buildFlowNodes(forkGateway).parallelGateway(joinGatewayId);

    for (int i = 1; i < blockBuilders.size(); i++) {
      final String edgeId = branchIds.get(i);
      final BlockBuilder blockBuilder = blockBuilders.get(i);

      final AbstractFlowNodeBuilder<?, ?> outgoingEdge =
          workInProgress.moveToNode(forkGatewayId).sequenceFlowId(edgeId);

      workInProgress = blockBuilder.buildFlowNodes(outgoingEdge).connectTo(joinGatewayId);
    }

    return workInProgress;
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();
    final Random random = context.getRandom();

    final var forkingGateway = new StepActivateBPMNElement(forkGatewayId);
    result.appendDirectSuccessor(forkingGateway);

    final Optional<BlockBuilder> blockContainingStartBlock =
        blockBuilders.stream()
            .filter(b -> b.equalsOrContains(context.getStartAtBlockBuilder()))
            .findFirst();

    final List<ParallelGatewayBlockBuilder.BranchPointer> branchPointers = new ArrayList<>();

    // Find the execution path of the block containing the start block first. This is required
    // as it will update the context, saying the start block has been found. As a result it will
    // make sure the execution paths of the other blocks isn't ignored.
    if (blockContainingStartBlock.isPresent()) {
      final var blockBuilder = blockContainingStartBlock.get();
      branchPointers.add(findRandomExecutionPathForBranch(context, result, blockBuilder));
      blockBuilders.stream()
          .filter(bb -> bb != blockBuilder)
          .forEach(context::addSecondaryStartBlockBuilder);
    }

    branchPointers.addAll(
        blockBuilders.stream()
            .filter(
                blockBuilder -> !blockBuilder.equalsOrContains(context.getStartAtBlockBuilder()))
            .map(blockBuilder -> findRandomExecutionPathForBranch(context, result, blockBuilder))
            .toList());
    shuffleStepsFromDifferentLists(random, result, branchPointers);

    return result;
  }

  @Override
  public String getElementId() {
    return forkGatewayId;
  }

  @Override
  public List<BlockBuilder> getPossibleStartingBlocks() {
    final List<BlockBuilder> allBlockBuilders = new ArrayList<>();
    allBlockBuilders.add(this);
    blockBuilders.forEach(
        blockBuilder -> allBlockBuilders.addAll(blockBuilder.getPossibleStartingBlocks()));
    return allBlockBuilders;
  }

  private BranchPointer findRandomExecutionPathForBranch(
      final ExecutionPathContext context,
      final ExecutionPathSegment executionPathSegment,
      final BlockBuilder blockBuilder) {
    final var branchExecutionPath = blockBuilder.findRandomExecutionPath(context);
    executionPathSegment.mergeVariableDefaults(branchExecutionPath);
    return new BranchPointer(branchExecutionPath.getScheduledSteps());
  }

  // shuffles the lists together by iteratively taking the first item from one of the lists
  private void shuffleStepsFromDifferentLists(
      final Random random,
      final ExecutionPathSegment executionPath,
      final List<BranchPointer> branchPointers) {

    purgeEmptyBranches(branchPointers);

    branchPointers.forEach(branch -> copyAutomaticSteps(branch, executionPath));

    purgeEmptyBranches(branchPointers);

    while (!branchPointers.isEmpty()) {
      final var tuple = branchPointers.get(random.nextInt(branchPointers.size()));

      takeNextItemAndAppendToExecutionPath(executionPath, tuple);
      copyAutomaticSteps(tuple, executionPath);
      purgeEmptyBranches(branchPointers);
    }
  }

  private void takeNextItemAndAppendToExecutionPath(
      final ExecutionPathSegment executionPath, final BranchPointer branchPointer) {
    executionPath.append(branchPointer.getRemainingSteps().remove(0));
  }

  /**
   * Copies a sequence of automatic steps. These steps cannot be scheduled explicitly. So whenever a
   * non-automatic step is added, all its succeeding automatic steps need to be copied as well.
   */
  private void copyAutomaticSteps(
      final BranchPointer branchPointer, final ExecutionPathSegment executionPath) {
    while (branchPointer.remainingSteps.size() > 0
        && branchPointer.remainingSteps.get(0).getStep().isAutomatic()) {
      takeNextItemAndAppendToExecutionPath(executionPath, branchPointer);
    }
  }

  private void purgeEmptyBranches(final List<BranchPointer> branchPointers) {
    branchPointers.removeIf(BranchPointer::isEmpty);
  }

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ParallelGatewayBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return true;
    }
  }

  private static final class BranchPointer {

    private final List<ScheduledExecutionStep> remainingSteps;

    private BranchPointer(final List<ScheduledExecutionStep> remainingSteps) {
      this.remainingSteps = new ArrayList<>(remainingSteps);
    }

    private List<ScheduledExecutionStep> getRemainingSteps() {
      return remainingSteps;
    }

    private boolean isEmpty() {
      return remainingSteps.isEmpty();
    }
  }
}
