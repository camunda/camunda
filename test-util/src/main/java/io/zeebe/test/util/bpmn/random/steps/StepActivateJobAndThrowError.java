/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

public class StepActivateJobAndThrowError extends AbstractExecutionStep {

  private final String jobType;
  private final String errorCode;

  public StepActivateJobAndThrowError(final String jobType, final String errorCode) {
    super();
    this.jobType = jobType;
    this.errorCode = errorCode;
  }

  public String getJobType() {
    return jobType;
  }

  public String getErrorCode() {
    return errorCode;
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
    result = 31 * result + errorCode.hashCode();
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

    final StepActivateJobAndThrowError that = (StepActivateJobAndThrowError) o;

    if (!jobType.equals(that.jobType)) {
      return false;
    }
    return errorCode.equals(that.errorCode);
  }
}
