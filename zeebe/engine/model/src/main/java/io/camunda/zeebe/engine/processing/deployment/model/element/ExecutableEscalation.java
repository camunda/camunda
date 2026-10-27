/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutableEscalation extends AbstractFlowElement {

  private DirectBuffer escalationCode;
  private Expression escalationCodeExpression;

  public ExecutableEscalation(final String id) {
    super(id);
  }

  /**
   * Returns the escalation code, if it has been resolved previously (and is independent of the
   * variable context). If this returns an empty {@code Optional} then the escalation code must be
   * resolved by evaluating {@code getEscalationCodeExpression()}
   *
   * @return the escalation code, if it has been resolved previously (and is independent of the
   *     variable context)
   */
  public Optional<DirectBuffer> getEscalationCode() {
    return Optional.ofNullable(escalationCode);
  }

  public void setEscalationCode(final DirectBuffer escalationCode) {
    this.escalationCode = new UnsafeBuffer(escalationCode);
  }

  public Expression getEscalationCodeExpression() {
    return escalationCodeExpression;
  }

  public void setEscalationCodeExpression(final Expression escalationCodeExpression) {
    this.escalationCodeExpression = escalationCodeExpression;
  }
}
