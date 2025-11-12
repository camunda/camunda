/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;

public class ExecutableCondition extends AbstractFlowElement {

  private Expression conditionExpression;

  public ExecutableCondition(final String id) {
    super(id);
  }

  public Expression getConditionExpression() {
    return conditionExpression;
  }

  public void setConditionExpression(final Expression conditionExpression) {
    this.conditionExpression = conditionExpression;
  }
}
