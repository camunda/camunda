/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.test.util.bpmn.random.blocks.StepStartProcessInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** Execution path to execute a random workflow from start to finish. This class is immutable. */
public final class ExecutionPath {
  private final List<AbstractExecutionStep> steps = new ArrayList<>();

  public ExecutionPath(final ExecutionPathSegment pathSegment) {
    steps.add(new StepStartProcessInstance(pathSegment));
    steps.addAll(pathSegment.getSteps());
  }

  public List<AbstractExecutionStep> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
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

    return new EqualsBuilder().append(steps, that.steps).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(steps).toHashCode();
  }
}
