/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import io.zeebe.el.Expression;
import java.util.Optional;

public class ExecutableMessage extends AbstractFlowElement {

  private Expression correlationKeyExpression;
  private Expression messageNameExpression;
  private String messageName;

  public ExecutableMessage(final String id) {
    super(id);
  }

  public Expression getCorrelationKeyExpression() {
    return correlationKeyExpression;
  }

  public void setCorrelationKeyExpression(final Expression correlationKey) {
    correlationKeyExpression = correlationKey;
  }

  public Expression getMessageNameExpression() {
    return messageNameExpression;
  }

  public void setMessageNameExpression(final Expression messageName) {
    messageNameExpression = messageName;
  }

  /**
   * Returns the message name, if it has been resolved previously (and is independent of the
   * variable context). If this returns an empty {@code Optional} then the message name must be
   * resolved by evaluating {@code getMessageNameExpression()}
   *
   * @return the message name, if it has been resolved previously (and is independent of the *
   *     variable context)
   */
  public Optional<String> getMessageName() {
    return Optional.ofNullable(messageName);
  }

  public void setMessageName(String messageName) {
    this.messageName = messageName;
  }
}
