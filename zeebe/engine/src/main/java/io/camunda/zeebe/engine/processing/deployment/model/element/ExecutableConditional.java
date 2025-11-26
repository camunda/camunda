/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.List;

public class ExecutableConditional extends AbstractFlowElement {

  private Expression conditionExpression;
  private List<String> variableNames;
  private List<String> variableEvents;

  public ExecutableConditional(final String id) {
    super(id);
  }

  public Expression getConditionExpression() {
    return conditionExpression;
  }

  public void setConditionExpression(final Expression conditionExpression) {
    this.conditionExpression = conditionExpression;
  }

  public List<String> getVariableEvents() {
    return variableEvents;
  }

  public void setVariableEvents(final List<String> variableEvents) {
    this.variableEvents = variableEvents;
  }

  public List<String> getVariableNames() {
    return variableNames;
  }

  public void setVariableNames(final List<String> variableNames) {
    this.variableNames = variableNames;
  }
}
