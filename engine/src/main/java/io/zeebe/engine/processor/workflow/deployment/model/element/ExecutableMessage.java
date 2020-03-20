/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.el.Expression;
import org.agrona.DirectBuffer;

public class ExecutableMessage extends AbstractFlowElement {

  private Expression correlationKeyExpression;
  private DirectBuffer messageName;

  public ExecutableMessage(final String id) {
    super(id);
  }

  public Expression getCorrelationKeyExpression() {
    return correlationKeyExpression;
  }

  public void setCorrelationKeyExpression(final Expression correlationKey) {
    this.correlationKeyExpression = correlationKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
  }
}
