/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public final class StepStartProcessInstance extends AbstractExecutionStep {

  private final String processId;

  public StepStartProcessInstance(final String processId, final ExecutionPathSegment pathSegment) {
    this.processId = processId;
    variables.putAll(pathSegment.collectVariables());
  }

  public String getProcessId() {
    return processId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StepStartProcessInstance that = (StepStartProcessInstance) o;

    return new EqualsBuilder()
        .append(processId, that.processId)
        .append(variables, that.variables)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(processId).append(variables).toHashCode();
  }
}
