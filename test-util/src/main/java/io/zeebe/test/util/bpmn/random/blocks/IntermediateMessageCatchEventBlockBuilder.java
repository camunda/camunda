/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.BlockBuilder;
import io.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.IDGenerator;
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

    result.append(new StepPublishMessage(messageName));

    return result;
  }

  public static final class StepPublishMessage extends AbstractExecutionStep {

    private final String messageName;

    public StepPublishMessage(final String messageName) {
      this.messageName = messageName;
      variables.put(CORRELATION_KEY_FIELD, CORRELATION_KEY_VALUE);
    }

    public String getMessageName() {
      return messageName;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepPublishMessage that = (StepPublishMessage) o;

      if (messageName != null ? !messageName.equals(that.messageName) : that.messageName != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = messageName != null ? messageName.hashCode() : 0;
      result = 31 * result + variables.hashCode();
      return result;
    }
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
