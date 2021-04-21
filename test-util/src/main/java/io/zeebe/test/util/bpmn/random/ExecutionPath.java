/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** Execution path to execute a random process from start to finish. This class is immutable. */
public final class ExecutionPath {
  private final List<ScheduledExecutionStep> steps = new ArrayList<>();
  private final String processId;

  public ExecutionPath(final String processId, final ExecutionPathSegment pathSegment) {
    this.processId = processId;
    steps.addAll(pathSegment.getScheduledSteps());
  }

  public String getProcessId() {
    return processId;
  }

  public List<ScheduledExecutionStep> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  @Override
  public int hashCode() {
    int result = steps.hashCode();
    result = 31 * result + processId.hashCode();
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

    final ExecutionPath that = (ExecutionPath) o;

    if (!steps.equals(that.steps)) {
      return false;
    }
    return processId.equals(that.processId);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
