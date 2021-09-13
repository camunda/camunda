/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StepTriggerTimerBoundaryEvent extends AbstractExecutionStep {

  private final String boundaryTimerEventId;

  public StepTriggerTimerBoundaryEvent(final String boundaryTimerEventId) {
    this.boundaryTimerEventId = boundaryTimerEventId;
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    final var result = new HashMap<>(variables);
    result.put(boundaryTimerEventId, activationDuration.toString());
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
    return Objects.hash(super.hashCode(), boundaryTimerEventId);
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
    final StepTriggerTimerBoundaryEvent that = (StepTriggerTimerBoundaryEvent) o;
    return boundaryTimerEventId.equals(that.boundaryTimerEventId);
  }

  public String getBoundaryTimerEventId() {
    return boundaryTimerEventId;
  }
}
