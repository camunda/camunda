/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.engine.util.random.steps.PickConditionCase;
import io.zeebe.engine.util.random.steps.PickDefaultCase;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExclusivelGatewayBlockBuilder implements BlockBuilder {

  private final List<BlockBuilder> blockBuilders = new ArrayList<>();
  private final List<String> branchIds = new ArrayList<>();
  private String forkGatewayId;
  private String joinGatewayId;

  public ExclusivelGatewayBlockBuilder(ConstructionContext context) {
    Random random = context.getRandom();
    IDGenerator idGenerator = context.getIdGenerator();
    int maxBranches = context.getMaxBranches();

    forkGatewayId = idGenerator.nextId();
    joinGatewayId = idGenerator.nextId();

    BlockSequenceBuilderFactory blockSequenceBuilderFactory =
        context.getBlockSequenceBuilderFactory();

    int branches = Math.max(2, random.nextInt(maxBranches));

    for (int i = 0; i < branches; i++) {
      branchIds.add("edge_" + idGenerator.nextId());
      blockBuilders.add(
          blockSequenceBuilderFactory.createBlockSequenceBuilder(context.withIncrementedDepth()));
    }
  }

  @Override
  public AbstractFlowNodeBuilder buildFlowNodes(AbstractFlowNodeBuilder nodeBuilder) {
    ExclusiveGatewayBuilder forkGateway = nodeBuilder.exclusiveGateway(forkGatewayId);

    ExclusiveGatewayBuilder joinGateway =
        blockBuilders
            .get(0)
            .buildFlowNodes(forkGateway.defaultFlow())
            .exclusiveGateway(joinGatewayId);

    AbstractFlowNodeBuilder workInProgress = joinGateway;

    for (int i = 1; i < blockBuilders.size(); i++) {
      String edgeId = branchIds.get(i);
      BlockBuilder blockBuilder = blockBuilders.get(i);

      AbstractFlowNodeBuilder outgoingEdge =
          workInProgress
              .moveToNode(forkGatewayId)
              .sequenceFlowId(edgeId)
              .conditionExpression(edgeId + " = true");

      workInProgress = blockBuilder.buildFlowNodes(outgoingEdge).connectTo(joinGatewayId);
    }

    return joinGateway;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    int branch = random.nextInt(branchIds.size());

    if (branch == 0) {
      result.append(new PickDefaultCase(forkGatewayId));
    } else {
      result.append(new PickConditionCase(forkGatewayId, branchIds.get(branch)));
    }

    BlockBuilder blockBuilder = blockBuilders.get(branch);

    result.append(blockBuilder.findRandomExecutionPath(random));

    return result;
  }
}
