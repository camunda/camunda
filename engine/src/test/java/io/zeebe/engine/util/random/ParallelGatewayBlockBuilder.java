/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.engine.util.random.steps.AbstractExecutionStep;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ParallelGatewayBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParallelGatewayBlockBuilder implements BlockBuilder {

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();
  private final List<String> branchIds = new ArrayList<>();
  private String forkGatewayId;
  private String joinGatewayId;

  public ParallelGatewayBlockBuilder(ConstructionContext context) {
    Random random = context.getRandom();
    IDGenerator idGenerator = context.getIdGenerator();
    int maxBranches = context.getMaxBranches();

    forkGatewayId = idGenerator.nextId();
    joinGatewayId = idGenerator.nextId();

    BlockSequenceBuilderFactory blockSequenceBuilderFactory =
        context.getBlockSequenceBuilderFactory();

    int branches = Math.max(2, random.nextInt(maxBranches));

    for (int i = 0; i < branches; i++) {
      branchIds.add(idGenerator.nextId());
      blockBuilders.add(
          blockSequenceBuilderFactory.createBlockSequenceBuilder(context.withIncrementedDepth()));
    }
  }

  @Override
  public AbstractFlowNodeBuilder buildFlowNodes(AbstractFlowNodeBuilder nodeBuilder) {
    ParallelGatewayBuilder forkGateway = nodeBuilder.parallelGateway(forkGatewayId);

    ParallelGatewayBuilder joinGateway =
        blockBuilders.get(0).buildFlowNodes(forkGateway).parallelGateway(joinGatewayId);

    AbstractFlowNodeBuilder workInProgress = joinGateway;

    for (int i = 1; i < blockBuilders.size(); i++) {
      String edgeId = branchIds.get(i);
      BlockBuilder blockBuilder = blockBuilders.get(i);

      AbstractFlowNodeBuilder outgoingEdge =
          workInProgress.moveToNode(forkGatewayId).sequenceFlowId(edgeId);

      workInProgress = blockBuilder.buildFlowNodes(outgoingEdge).connectTo(joinGatewayId);
    }

    return joinGateway;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    List<List<AbstractExecutionStep>> stepsOfParallelPaths = new ArrayList<>();

    blockBuilders.forEach(
        blockBuilder ->
            stepsOfParallelPaths.add(
                new ArrayList<>(blockBuilder.findRandomExecutionPath(random).getSteps())));

    List<AbstractExecutionStep> shuffledSteps =
        shuffleStepsFromDifferentLists(random, stepsOfParallelPaths);

    shuffledSteps.forEach(result::append);

    return result;
  }

  // shuffle the lists together by iteratively taking the first ite
  private List<AbstractExecutionStep> shuffleStepsFromDifferentLists(
      Random random, List<List<AbstractExecutionStep>> sources) {
    List<AbstractExecutionStep> result = new ArrayList<>();

    purgeEmpytLists(sources);

    while (!sources.isEmpty()) {
      List<AbstractExecutionStep> source = sources.get(random.nextInt(sources.size()));

      AbstractExecutionStep step = source.remove(0);
      result.add(step);
      purgeEmpytLists(sources);
    }

    return result;
  }

  private void purgeEmpytLists(List<List<AbstractExecutionStep>> sources) {
    sources.removeIf(List::isEmpty);
  }
}
