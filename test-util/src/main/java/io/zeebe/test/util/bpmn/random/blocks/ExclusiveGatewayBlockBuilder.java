/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.ExclusiveGatewayBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    final int branch = random.nextInt(branchIds.size());

    if (branch == 0) {
      result.append(new StepPickDefaultCase(forkGatewayId, gatewayConditionVariable));
    } else {
      result.append(
          new StepPickConditionCase(
              forkGatewayId, gatewayConditionVariable, branchIds.get(branch)));
    }

    final BlockBuilder blockBuilder = blockBuilders.get(branch);

    result.append(blockBuilder.findRandomExecutionPath(random));

    return result;
  }

  // this class could also be called "Set variables when starting the process so that the engine
  // will select a certain condition"
  public static final class StepPickConditionCase extends AbstractExecutionStep {

    private final String forkingGatewayId;
    private final String edgeId;

    public StepPickConditionCase(
        final String forkingGatewayId, final String gatewayConditionVariable, final String edgeId) {
      this.forkingGatewayId = forkingGatewayId;
      this.edgeId = edgeId;
      variables.put(gatewayConditionVariable, edgeId);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepPickConditionCase that = (StepPickConditionCase) o;

      return new EqualsBuilder()
          .append(forkingGatewayId, that.forkingGatewayId)
          .append(edgeId, that.edgeId)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37).append(forkingGatewayId).append(edgeId).toHashCode();
    }
  }

  public static final class StepPickDefaultCase extends AbstractExecutionStep {

    private final String forkingGatewayId;

    public StepPickDefaultCase(
        final String forkingGatewayId, final String gatewayConditionVariable) {
      this.forkingGatewayId = forkingGatewayId;
      variables.put(gatewayConditionVariable, "default-case");
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepPickDefaultCase that = (StepPickDefaultCase) o;

      return new EqualsBuilder().append(forkingGatewayId, that.forkingGatewayId).isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37).append(forkingGatewayId).toHashCode();
    }
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
