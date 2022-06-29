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
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EventBasedGatewayBlockBuilder implements BlockBuilder {

  private static final String CORRELATION_KEY_FIELD = "correlationKey";
  private static final String CORRELATION_KEY_VALUE = "default_correlation_key";
  private static final String MESSAGE_NAME_PREFIX = "message_";

  private final String forkGatewayId;
  private final String joinGatewayId;
  private final List<Tuple<String, BlockBuilder>> branches = new ArrayList<>();

  public EventBasedGatewayBlockBuilder(final ConstructionContext context) {
    final Random random = context.getRandom();
    final IDGenerator idGenerator = context.getIdGenerator();
    final int maxBranches = context.getMaxBranches();

    forkGatewayId = "fork_" + idGenerator.nextId();
    joinGatewayId = "join_" + idGenerator.nextId();

    final var blockSequenceBuilderFactory = context.getBlockSequenceBuilderFactory();

    // must have at least two branches
    final int numberOfBranches = Math.max(2, random.nextInt(maxBranches));

    for (int i = 0; i < numberOfBranches; i++) {
      final var branchId = idGenerator.nextId();
      final var branchBuilder =
          blockSequenceBuilderFactory.createBlockSequenceBuilder(context.withIncrementedDepth());
      branches.add(new Tuple<>(branchId, branchBuilder));
    }
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final var forkGateway = nodeBuilder.eventBasedGateway(forkGatewayId);

    final var firstBranch = addBranch(forkGateway, branches.get(0));
    final var joinGateway = firstBranch.exclusiveGateway(joinGatewayId);

    branches.stream()
        .skip(1)
        .forEach(branch -> addBranch(forkGateway, branch).connectTo(joinGatewayId));

    return joinGateway;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    final int branchNumber = random.nextInt(branches.size());
    final var branch = branches.get(branchNumber);
    final var blockBuilder = branch.getRight();

    final var executionStep =
        new StepPublishMessage(
            getMessageName(branch), CORRELATION_KEY_FIELD, CORRELATION_KEY_VALUE);
    result.appendDirectSuccessor(executionStep);
    result.append(blockBuilder.findRandomExecutionPath(random));

    return result;
  }

  @Override
  public String getElementId() {
    return forkGatewayId;
  }

  private AbstractFlowNodeBuilder<?, ?> addBranch(
      final io.camunda.zeebe.model.bpmn.builder.EventBasedGatewayBuilder gatewayBuilder,
      final Tuple<String, BlockBuilder> branch) {

    final var branchId = branch.getLeft();
    final var branchBlockBuilder = branch.getRight();

    final var catchEvent = gatewayBuilder.intermediateCatchEvent(branchId);
    catchEvent.message(
        messageBuilder -> {
          messageBuilder.zeebeCorrelationKeyExpression(CORRELATION_KEY_FIELD);
          messageBuilder.name(getMessageName(branch));
        });

    return branchBlockBuilder.buildFlowNodes(catchEvent);
  }

  private String getMessageName(final Tuple<String, BlockBuilder> branch) {
    final String branchId = branch.getLeft();
    return MESSAGE_NAME_PREFIX + branchId;
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new EventBasedGatewayBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return true;
    }
  }
}
