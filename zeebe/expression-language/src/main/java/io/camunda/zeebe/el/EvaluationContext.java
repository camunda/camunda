/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

/** The context for evaluating an expression. */
public interface EvaluationContext {
  /**
   * Returns the value of the variable with the given name.
   *
   * @param variableName the name of the variable
   * @return the variable value as MessagePack encoded buffer, or {@code null} if the variable is
   *     not present
   */
  Object getVariable(String variableName);
}
