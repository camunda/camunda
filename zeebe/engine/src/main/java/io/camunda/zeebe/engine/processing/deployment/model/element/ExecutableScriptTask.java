/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;

public final class ExecutableScriptTask extends ExecutableJobWorkerTask
    implements ExecutableScript {

  private Expression expression;
  private String resultVariable;

  public ExecutableScriptTask(final String id) {
    super(id);
  }

  @Override
  public Expression getExpression() {
    return expression;
  }

  @Override
  public void setExpression(final Expression expression) {
    this.expression = expression;
  }

  @Override
  public String getResultVariable() {
    return resultVariable;
  }

  @Override
  public void setResultVariable(final String resultVariable) {
    this.resultVariable = resultVariable;
  }
}
