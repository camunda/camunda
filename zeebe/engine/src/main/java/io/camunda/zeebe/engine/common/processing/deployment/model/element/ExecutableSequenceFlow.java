/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;

public class ExecutableSequenceFlow extends AbstractFlowElement {

  private ExecutableFlowNode source;
  private ExecutableFlowNode target;
  private Expression condition;

  public ExecutableSequenceFlow(final String id) {
    super(id);
  }

  public ExecutableFlowNode getTarget() {
    return target;
  }

  public void setTarget(final ExecutableFlowNode target) {
    this.target = target;
  }

  public ExecutableFlowNode getSource() {
    return source;
  }

  public void setSource(final ExecutableFlowNode source) {
    this.source = source;
  }

  public Expression getCondition() {
    return condition;
  }

  public void setCondition(final Expression condition) {
    this.condition = condition;
  }
}
