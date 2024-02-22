/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Map;

public class StepThrowError extends AbstractExecutionStep {

  private final String elementId;
  private final String errorCode;

  public StepThrowError(final String elementId, final String errorCode) {
    this.elementId = elementId;
    this.errorCode = errorCode;
  }

  public String getElementId() {
    return elementId;
  }

  public String getErrorCode() {
    return errorCode;
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    return variables;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public Duration getDeltaTime() {
    return VIRTUALLY_NO_TIME;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + elementId.hashCode();
    result = 31 * result + errorCode.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final StepThrowError that = (StepThrowError) o;
    return errorCode.equals(that.errorCode) && elementId.equals(that.elementId);
  }
}
