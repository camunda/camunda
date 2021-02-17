/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import java.util.Map;

public final class MessageStartEventBuilder implements StartEventBlockBuilder {

  private final String startEventId;
  private final String messageName;

  public MessageStartEventBuilder(final ConstructionContext context) {
    final var idGenerator = context.getIdGenerator();
    startEventId = idGenerator.nextId();
    messageName = "message_" + startEventId;
  }

  @Override
  public io.zeebe.model.bpmn.builder.StartEventBuilder buildStartEvent(
      final ProcessBuilder processBuilder) {
    return processBuilder
        .startEvent(startEventId)
        .message(messageBuilder -> messageBuilder.name(messageName));
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(
      final String processId, final Map<String, Object> variables) {
    final var pathSegment = new ExecutionPathSegment();
    pathSegment.append(new StepPublishStartMessage(messageName, variables));
    return pathSegment;
  }

  public static final class StepPublishStartMessage extends AbstractExecutionStep {

    private final String messageName;

    public StepPublishStartMessage(final String messageName, final Map<String, Object> variables) {
      this.messageName = messageName;
      this.variables.putAll(variables);
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

      final StepPublishStartMessage that = (StepPublishStartMessage) o;

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
}
