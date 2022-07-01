/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StepStartProcessInstance extends AbstractExecutionStep
    implements ProcessStartStep {

  private final String processId;
  private final List<String> startElementIds;

  public StepStartProcessInstance(final String processId, final Map<String, Object> variables) {
    this.processId = processId;
    this.variables.putAll(variables);
    startElementIds = new ArrayList<>();
  }

  public StepStartProcessInstance(
      final String processId,
      final Map<String, Object> variables,
      final List<String> startElementIds) {
    this.processId = processId;
    this.variables.putAll(variables);
    this.startElementIds = startElementIds;
  }

  @Override
  public Map<String, Object> getProcessVariables() {
    return Collections.unmodifiableMap(variables);
  }

  public String getProcessId() {
    return processId;
  }

  public List<String> getStartElementIds() {
    return startElementIds;
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
    result = 31 * result + processId.hashCode();
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

    final StepStartProcessInstance that = (StepStartProcessInstance) o;

    return processId.equals(that.processId);
  }
}
