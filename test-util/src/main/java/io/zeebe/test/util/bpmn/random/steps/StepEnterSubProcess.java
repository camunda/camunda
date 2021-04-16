/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Map;

public final class StepEnterSubProcess extends AbstractExecutionStep {

  private final String subProcessId;

  public StepEnterSubProcess(
      final String subProcessId, final String subProcessBoundaryTimerEventId) {
    this.subProcessId = subProcessId;
    /* temporary value to have a timer that will not fire in normal execution; if the execution
     * path includes a StepTimeoutSubProcess, then the value will be overwritten with the correct
     * time for that execution path
     */
    variables.put(subProcessBoundaryTimerEventId, VIRTUALLY_INFINITE.toString());
  }

  @Override
  public Map<String, Object> updateVariables(
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
    int result = super.hashCode();
    result = 31 * result + subProcessId.hashCode();
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

    final StepEnterSubProcess that = (StepEnterSubProcess) o;

    return subProcessId.equals(that.subProcessId);
  }
}
