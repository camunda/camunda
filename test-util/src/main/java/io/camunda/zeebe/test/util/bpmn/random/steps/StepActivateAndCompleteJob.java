/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public final class StepActivateAndCompleteJob extends AbstractExecutionStep {

  private final String jobType;
  private final String elementId;

  public StepActivateAndCompleteJob(final String jobType, final String elementId) {
    this(jobType, elementId, Collections.emptyMap());
  }

  public StepActivateAndCompleteJob(
      final String jobType, final String elementId, final Map<String, Object> variables) {
    this.jobType = jobType;
    this.variables.putAll(variables);
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
    return VIRTUALLY_NO_TIME;
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

    final StepActivateAndCompleteJob that = (StepActivateAndCompleteJob) o;

    return jobType.equals(that.jobType);
  }
}
