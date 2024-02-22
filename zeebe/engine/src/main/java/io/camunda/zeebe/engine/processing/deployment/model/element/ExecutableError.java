/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutableError extends AbstractFlowElement {

  private DirectBuffer errorCode;
  private Expression errorCodeExpression;

  public ExecutableError(final String id) {
    super(id);
  }

  /**
   * Returns the error code, if it has been resolved previously (and is independent of the variable
   * context). If this returns an empty {@code Optional} then the error code must be resolved by
   * evaluating {@code getErrorCodeExpression()}
   *
   * @return the error code, if it has been resolved previously (and is independent of the variable
   *     context)
   */
  public Optional<DirectBuffer> getErrorCode() {
    return Optional.ofNullable(errorCode);
  }

  public void setErrorCode(final DirectBuffer errorCode) {
    this.errorCode = new UnsafeBuffer(errorCode);
  }

  public Expression getErrorCodeExpression() {
    return errorCodeExpression;
  }

  public void setErrorCodeExpression(final Expression errorCodeExpression) {
    this.errorCodeExpression = errorCodeExpression;
  }
}
