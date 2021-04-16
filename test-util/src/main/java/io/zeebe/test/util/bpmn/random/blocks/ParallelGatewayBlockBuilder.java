/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ParallelGatewayBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.steps.StepEnterParallelGateway;
import java.util.ArrayList;
import java.util.List;
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
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    final var forkingGateway = new StepEnterParallelGateway(forkGatewayId);
    result.append(forkingGateway);

    final var branchPointers =
        blockBuilders.stream()
            .map(
                blockBuilder -> {
                  final var branchExecutionPath = blockBuilder.findRandomExecutionPath(random);
                  result.mergeVariableDefaults(branchExecutionPath);
                  return new BranchPointer(forkingGateway, branchExecutionPath.getSteps());
                });

    return result;
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
    final var remainingSteps = branchPointer.getRemainingSteps();
    final var logicalPredecessor = branchPointer.getLogicalPredecessor();

    final AbstractExecutionStep nextStep = remainingSteps.remove(0);

    executionPath.append(nextStep, logicalPredecessor);

    branchPointer.setLogicalPredecessor(nextStep);
  }

  /**
   * Copies a sequence of automatic steps. These steps cannot be scheduled explicitly. So whenever a
   * non-automatic step is added, all its succeeding automatic steps need to be copied as well.
   */
  private void copyAutomaticSteps(
      final BranchPointer branchPointer, final ExecutionPathSegment executionPath) {
    while (branchPointer.remainingSteps.size() > 0
        && branchPointer.remainingSteps.get(0).isAutomatic()) {
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

    private AbstractExecutionStep logicalPredecessor;

    private final List<AbstractExecutionStep> remainingSteps;

    private BranchPointer(
        final AbstractExecutionStep logicalPredecessor,
        final List<AbstractExecutionStep> remainingSteps) {
      this.logicalPredecessor = logicalPredecessor;
      this.remainingSteps = remainingSteps;
    }

    private List<AbstractExecutionStep> getRemainingSteps() {
      return remainingSteps;
    }

    private AbstractExecutionStep getLogicalPredecessor() {
      return logicalPredecessor;
    }

    private void setLogicalPredecessor(final AbstractExecutionStep logicalPredecessor) {
      this.logicalPredecessor = logicalPredecessor;
    }

    private boolean isEmpty() {
      return remainingSteps.isEmpty();
    }
  }
}
