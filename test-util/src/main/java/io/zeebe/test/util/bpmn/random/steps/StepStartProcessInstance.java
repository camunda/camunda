/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.util.Map;

public final class StepStartProcessInstance extends AbstractExecutionStep {

  private final String processId;

  public StepStartProcessInstance(final String processId, final Map<String, Object> variables) {
    this.processId = processId;
    this.variables.putAll(variables);
  }

  public String getProcessId() {
    return processId;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public int hashCode() {
    int result = processId != null ? processId.hashCode() : 0;
    result = 31 * result + variables.hashCode();
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

    final StepStartProcessInstance that = (StepStartProcessInstance) o;

    if (processId != null ? !processId.equals(that.processId) : that.processId != null) {
      return false;
    }
    return variables.equals(that.variables);
  }
}
