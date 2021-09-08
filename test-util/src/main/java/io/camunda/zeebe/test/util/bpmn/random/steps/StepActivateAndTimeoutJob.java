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

public final class StepActivateAndTimeoutJob extends AbstractExecutionStep {

  private final String jobType;
  private final String elementId;

  public StepActivateAndTimeoutJob(final String jobType, final String elementId) {
    this.jobType = jobType;
    this.elementId = elementId;
  }

  public String getJobType() {
    return jobType;
  }

  public String getElementId() {
    return elementId;
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
    return DEFAULT_DELTA;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + jobType.hashCode();
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

    final StepActivateAndTimeoutJob that = (StepActivateAndTimeoutJob) o;

    return jobType.equals(that.jobType);
  }
}
