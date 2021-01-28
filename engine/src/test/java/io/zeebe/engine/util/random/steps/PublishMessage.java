/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random.steps;

import io.zeebe.engine.util.random.IntermediateMessageCatchEventBlockBuilder;

public final class PublishMessage extends AbstractExecutionStep {

  private final String messageName;

  public PublishMessage(String messageName) {
    this.messageName = messageName;
    variables.put(
        IntermediateMessageCatchEventBlockBuilder.CORRELATION_KEY_FIELD,
        IntermediateMessageCatchEventBlockBuilder.CORRELATION_KEY_VALUE);
  }

  public String getMessageName() {
    return messageName;
  }
}
