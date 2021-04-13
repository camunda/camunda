/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.util.Collections;
import java.util.Map;

public final class StepActivateAndCompleteJob extends AbstractExecutionStep {

  private final String jobType;

  public StepActivateAndCompleteJob(final String jobType) {
    this(jobType, Collections.emptyMap());
  }

  public StepActivateAndCompleteJob(final String jobType, final Map<String, Object> variables) {
    this.jobType = jobType;
    this.variables.putAll(variables);
  }

  public String getJobType() {
    return jobType;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public int hashCode() {
    int result = jobType != null ? jobType.hashCode() : 0;
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

    final StepActivateAndCompleteJob that = (StepActivateAndCompleteJob) o;

    if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
      return false;
    }
    return variables.equals(that.variables);
  }
}
