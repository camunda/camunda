/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPickConditionCase;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPickDefaultCase;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepRaiseIncidentThenResolveAndPickConditionCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Generates a block with a forking exclusive gateway, a default case, a random number of
 * conditional cases, and a joining exclusive gateway. The default case and each conditional case
 * then have a nested sequence of blocks.
 *
 * <p>Hints: the conditional cases all have the condition {@code [forkGatewayId]_branch =
 * "[edge_id]"} so one only needs to set the right variables when starting the process to make sure
 * that a certain edge will be executed
 */
public class ExclusiveGatewayBlockBuilder implements BlockBuilder {

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();
  private final List<String> branchIds = new ArrayList<>();
  private final String forkGatewayId;
  private final String joinGatewayId;
  private final String gatewayConditionVariable;

  public ExclusiveGatewayBlockBuilder(final ConstructionContext context) {
    final Random random = context.getRandom();
    final IDGenerator idGenerator = context.getIdGenerator();
    final int maxBranches = context.getMaxBranches();

    forkGatewayId = "fork_" + idGenerator.nextId();
    joinGatewayId = "join_" + idGenerator.nextId();

    gatewayConditionVariable = forkGatewayId + "_branch";

    final BlockSequenceBuilder.BlockSequenceBuilderFactory blockSequenceBuilderFactory =
        context.getBlockSequenceBuilderFactory();

    final int branches = Math.max(2, random.nextInt(maxBranches));

    for (int i = 0; i < branches; i++) {
      branchIds.add("edge_" + idGenerator.nextId());
      blockBuilders.add(
          blockSequenceBuilderFactory.createBlockSequenceBuilder(context.withIncrementedDepth()));
    }
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {
    final ExclusiveGatewayBuilder forkGateway = nodeBuilder.exclusiveGateway(forkGatewayId);

    AbstractFlowNodeBuilder<?, ?> workInProgress =
        blockBuilders
            .get(0)
            .buildFlowNodes(forkGateway.defaultFlow())
            .exclusiveGateway(joinGatewayId);

    for (int i = 1; i < blockBuilders.size(); i++) {
      final String edgeId = branchIds.get(i);
      final BlockBuilder blockBuilder = blockBuilders.get(i);

      final AbstractFlowNodeBuilder<?, ?> outgoingEdge =
          workInProgress
              .moveToNode(forkGatewayId)
              .sequenceFlowId(edgeId)
              .conditionExpression(gatewayConditionVariable + " = \"" + edgeId + "\"");

      workInProgress = blockBuilder.buildFlowNodes(outgoingEdge).connectTo(joinGatewayId);
    }

    return workInProgress;
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();
    final Random random = context.getRandom();

    final Optional<Integer> branchContainingStartBlock =
        blockBuilders.stream()
            .filter(b -> b.equalsOrContains(context.getStartAtElementId()))
            .map(blockBuilders::indexOf)
            .findFirst();

    final int branch = branchContainingStartBlock.orElse(random.nextInt(branchIds.size()));
    if (branchContainingStartBlock.isEmpty()) {
      addRandomGatewayExecutionStep(result, random, branch);
    }
    addRandomBranchExecutionSteps(context, result, branch);

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

  private void addRandomGatewayExecutionStep(final ExecutionPathSegment result, final Random random, final int branch) {
    // If the branch is the default flow we should always add the default steps
    // If not we randomly match the condition, or throw an incident and resolve it before matching
    final int stepNumber = isDefaultFlow(branch) ? 0 : random.nextInt(1, 3);
    final var executionStep = switch (stepNumber) {
      case 0 -> new StepPickDefaultCase(forkGatewayId, gatewayConditionVariable);
      case 1 -> new StepPickConditionCase(forkGatewayId, gatewayConditionVariable, branchIds.get(branch));
      default -> new StepRaiseIncidentThenResolveAndPickConditionCase(forkGatewayId, gatewayConditionVariable, branchIds.get(branch));
    };
    result.appendDirectSuccessor(executionStep);
  }

  final boolean isDefaultFlow(final int branch) {
    return branch == 0;
  }

  private void addRandomBranchExecutionSteps(final ExecutionPathContext context, final ExecutionPathSegment result,
      final int branch) {
    final BlockBuilder blockBuilder = blockBuilders.get(branch);
    result.append(blockBuilder.findRandomExecutionPath(context));
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ExclusiveGatewayBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return true;
    }
  }
}
