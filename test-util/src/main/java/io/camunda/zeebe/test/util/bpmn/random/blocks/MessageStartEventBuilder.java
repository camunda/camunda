/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import io.zeebe.test.util.bpmn.random.steps.StepPublishStartMessage;
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
    pathSegment.appendDirectSuccessor(new StepPublishStartMessage(messageName, variables));
    return pathSegment;
  }
}
