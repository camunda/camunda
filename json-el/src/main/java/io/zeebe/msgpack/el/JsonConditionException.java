/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

public class JsonConditionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public JsonConditionException(String message) {
    super(message);
  }

  public JsonConditionException(CompiledJsonCondition condition, Exception cause) {
    super(
        String.format(
            "Expected to evaluate condition '%s' successfully, but failed because: %s",
            condition.getExpression(), cause.getMessage()),
        cause);
  }
}
