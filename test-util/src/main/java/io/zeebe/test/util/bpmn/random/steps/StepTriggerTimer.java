/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

public final class StepTriggerTimer extends AbstractExecutionStep {

  private final Duration timeToAdd;

  public StepTriggerTimer(final Duration timeToAdd) {
    this.timeToAdd = timeToAdd;
  }

  public Duration getTimeToAdd() {
    return timeToAdd;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StepTriggerTimer that = (StepTriggerTimer) o;

    if (timeToAdd != null ? !timeToAdd.equals(that.timeToAdd) : that.timeToAdd != null) {
      return false;
    }
    return variables.equals(that.variables);
  }

  @Override
  public int hashCode() {
    int result = timeToAdd != null ? timeToAdd.hashCode() : 0;
    result = 31 * result + variables.hashCode();
    return result;
  }
}
