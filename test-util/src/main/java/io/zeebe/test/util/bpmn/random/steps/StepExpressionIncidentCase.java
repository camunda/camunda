/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

// This class removes the variable set by the `StepPickConditionCase` to make sure an incident is
// raised. This same variable can later be provided (through variable update) and the incident can
// then be resolved
public final class StepExpressionIncidentCase extends AbstractExecutionStep {

  private final String forkingGatewayId;
  private final String edgeId;
  private final String gatewayConditionVariable;

  public StepExpressionIncidentCase(
      final String forkingGatewayId,
      final String gatewayConditionVariable,
      final String edgeId,
      final StepPickConditionCase pickConditionCase) {
    this.forkingGatewayId = forkingGatewayId;
    this.edgeId = edgeId;
    this.gatewayConditionVariable = gatewayConditionVariable;
    pickConditionCase.removeVariable(gatewayConditionVariable);
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
    result = 31 * result + forkingGatewayId.hashCode();
    result = 31 * result + edgeId.hashCode();
    result = 31 * result + gatewayConditionVariable.hashCode();
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

    final StepExpressionIncidentCase that = (StepExpressionIncidentCase) o;

    if (!forkingGatewayId.equals(that.forkingGatewayId)) {
      return false;
    }
    if (!edgeId.equals(that.edgeId)) {
      return false;
    }
    return gatewayConditionVariable.equals(that.gatewayConditionVariable);
  }

  public String getGatewayElementId() {
    return forkingGatewayId;
  }

  public String getGatewayConditionVariable() {
    return gatewayConditionVariable;
  }

  public String getEdgeId() {
    return edgeId;
  }
}
