/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;

/**
 * A representation of an element that execute feel script expression. For example, a script task.
 */
public interface ExecutableScript {

  Expression getExpression();

  void setExpression(Expression expression);

  String getResultVariable();

  void setResultVariable(String resultVariable);
}
