/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.camunda.zeebe.test.util.bpmn.random.IDGenerator;
import io.camunda.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import java.util.Random;

/**
 * Generates an intermediate message catch event. It waits for a message with name {@code
 * message_[id]} and a correlation key of {@code CORRELATION_KEY_VALUE}
 */
public class IntermediateMessageCatchEventBlockBuilder implements BlockBuilder {

  public static final String CORRELATION_KEY_FIELD = "correlationKey";
  public static final String CORRELATION_KEY_VALUE = "default_correlation_key";

  private final String id;
  private final String messageName;

  public IntermediateMessageCatchEventBlockBuilder(final IDGenerator idGenerator) {
    id = idGenerator.nextId();
    messageName = "message_" + id;
  }

  @Override
  public AbstractFlowNodeBuilder<?, ?> buildFlowNodes(
      final AbstractFlowNodeBuilder<?, ?> nodeBuilder) {

    final IntermediateCatchEventBuilder result = nodeBuilder.intermediateCatchEvent(id);

    result.message(
        messageBuilder -> {
          messageBuilder.zeebeCorrelationKeyExpression(CORRELATION_KEY_FIELD);
          messageBuilder.name(messageName);
        });

    return result;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final Random random) {
    final ExecutionPathSegment result = new ExecutionPathSegment();

    result.appendDirectSuccessor(
        new StepPublishMessage(messageName, CORRELATION_KEY_FIELD, CORRELATION_KEY_VALUE));

    return result;
  }

  @Override
  public String getElementId() {
    return id;
  }

  @Override
  public BlockBuilder findRandomStartingPlace(final Random random) {
    return this;
  }

  @Override
  public boolean equalsOrContains(final BlockBuilder blockBuilder) {
    return this == blockBuilder;
  }

  public static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new IntermediateMessageCatchEventBlockBuilder(context.getIdGenerator());
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
