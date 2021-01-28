/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.engine.util.random.steps.PublishMessage;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import java.util.Random;

public class IntermediateMessageCatchEventBlockBuilder implements BlockBuilder {

  public static final String CORRELATION_KEY_FIELD = "correlationKey";
  public static final String CORRELATION_KEY_VALUE = "default_correlation_key";

  private final String id;
  private final String messageName;

  public IntermediateMessageCatchEventBlockBuilder(IDGenerator idGenerator) {
    id = idGenerator.nextId();
    messageName = "message_" + id;
  }

  @Override
  public AbstractFlowNodeBuilder buildFlowNodes(AbstractFlowNodeBuilder nodeBuilder) {

    IntermediateCatchEventBuilder result = nodeBuilder.intermediateCatchEvent(id);

    result.message(
        messageBuilder -> {
          messageBuilder.zeebeCorrelationKeyExpression(CORRELATION_KEY_FIELD);
          messageBuilder.name(messageName);
        });

    return result;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(Random random) {
    ExecutionPathSegment result = new ExecutionPathSegment();

    result.append(new PublishMessage(messageName));

    return result;
  }
}
