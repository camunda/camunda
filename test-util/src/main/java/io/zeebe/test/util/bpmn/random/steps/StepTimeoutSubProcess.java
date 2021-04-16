/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class StepTimeoutSubProcess extends AbstractExecutionStep {

  private final String subProcessId;
  private final String subProcessBoundaryTimerEventId;

  public StepTimeoutSubProcess(
      final String subProcessId, final String subProcessBoundaryTimerEventId) {
    this.subProcessId = subProcessId;
    this.subProcessBoundaryTimerEventId = subProcessBoundaryTimerEventId;
  }

  public String getSubProcessBoundaryTimerEventId() {
    return subProcessBoundaryTimerEventId;
  }

  @Override
  public Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    final var result = new HashMap<>(variables);
    result.put(subProcessBoundaryTimerEventId, activationDuration.toString());
    return result;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public Duration getDeltaTime() {
    return DEFAULT_DELTA;
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

    final StepTimeoutSubProcess that = (StepTimeoutSubProcess) o;

    return subProcessId.equals(that.subProcessId);
  }
}
