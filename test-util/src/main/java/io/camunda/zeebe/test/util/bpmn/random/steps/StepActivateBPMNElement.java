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
import java.util.Objects;

public class StepActivateBPMNElement extends AbstractExecutionStep {

  private final String elementId;

  public StepActivateBPMNElement(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    return variables;
  }

  @Override
  public boolean isAutomatic() {
    return true;
  }

  @Override
  public Duration getDeltaTime() {
    return VIRTUALLY_NO_TIME;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), elementId);
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
    final StepActivateBPMNElement that = (StepActivateBPMNElement) o;
    return elementId.equals(that.elementId);
  }
}
