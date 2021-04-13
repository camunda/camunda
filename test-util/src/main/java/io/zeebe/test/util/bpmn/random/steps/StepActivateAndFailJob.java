/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

public final class StepActivateAndFailJob extends AbstractExecutionStep {

  private final String jobType;
  private final boolean updateRetries;

  public StepActivateAndFailJob(final String jobType, final boolean updateRetries) {
    this.jobType = jobType;
    this.updateRetries = updateRetries;
  }

  public String getJobType() {
    return jobType;
  }

  public boolean isUpdateRetries() {
    return updateRetries;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public int hashCode() {
    int result = jobType != null ? jobType.hashCode() : 0;
    result = 31 * result + (updateRetries ? 1 : 0);
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

    final StepActivateAndFailJob that = (StepActivateAndFailJob) o;

    if (updateRetries != that.updateRetries) {
      return false;
    }
    if (jobType != null ? !jobType.equals(that.jobType) : that.jobType != null) {
      return false;
    }
    return variables.equals(that.variables);
  }
}
