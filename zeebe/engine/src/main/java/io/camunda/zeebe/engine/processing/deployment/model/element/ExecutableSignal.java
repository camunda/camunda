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

public class ExecutableSignal extends AbstractFlowElement {

  private Expression signalNameExpression;
  private String signalName;

  public ExecutableSignal(final String id) {
    super(id);
  }

  public Expression getSignalNameExpression() {
    return signalNameExpression;
  }

  public void setSignalNameExpression(final Expression signalName) {
    signalNameExpression = signalName;
  }

  /**
   * Returns the signal name, if it has been resolved previously (and is independent of the variable
   * context). If this returns an empty {@code Optional} then the signal name must be resolved by
   * evaluating {@code getSignalNameExpression()}
   *
   * @return the signal name, if it has been resolved previously (and is independent of the *
   *     variable context)
   */
  public Optional<String> getSignalName() {
    return Optional.ofNullable(signalName);
  }

  public void setSignalName(final String signalName) {
    this.signalName = signalName;
  }
}
