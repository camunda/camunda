/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

import org.agrona.DirectBuffer;

/** The context for evaluating an expression. */
public interface EvaluationContext {

  /**
   * Returns the value of the variable with the given name.
   *
   * @param variableName the name of the variable
   * @return the variable value as MessagePack encoded buffer, or {@code null} if the variable is
   *     not present
   */
  DirectBuffer getVariable(String variableName);
}
