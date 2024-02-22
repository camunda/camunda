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
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishMessage;

/**
 * Generates a receive task. It waits for a message with name {@code message_[id]} and a correlation
 * key of {@code CORRELATION_KEY_VALUE}
 */
public class ReceiveTaskBlockBuilder extends AbstractBlockBuilder {

  private static final String CORRELATION_KEY_FIELD = "correlationKey";
  private static final String CORRELATION_KEY_VALUE = "default_correlation_key";
  private final String messageName;

  public ReceiveTaskBlockBuilder(final IDGenerator idGenerator) {
    super(idGenerator.nextId());
    messageName = "message_" + elementId;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final var receiveTask = nodeBuilder.receiveTask(getElementId());
    receiveTask.message(
        messageBuilder -> {
          messageBuilder.zeebeCorrelationKeyExpression(CORRELATION_KEY_FIELD);
          messageBuilder.name(messageName);
        });

    return receiveTask;
  }

  @Override
  public ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    result.appendDirectSuccessor(
        new StepPublishMessage(messageName, CORRELATION_KEY_FIELD, CORRELATION_KEY_VALUE));

    return result;
  }

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ReceiveTaskBlockBuilder(context.getIdGenerator());
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
