/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

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
  public int hashCode() {
    int result = forkingGatewayId != null ? forkingGatewayId.hashCode() : 0;
    result = 31 * result + (edgeId != null ? edgeId.hashCode() : 0);
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

    final StepExpressionIncidentCase that = (StepExpressionIncidentCase) o;

    if (forkingGatewayId != null
        ? !forkingGatewayId.equals(that.forkingGatewayId)
        : that.forkingGatewayId != null) {
      return false;
    }
    if (edgeId != null ? !edgeId.equals(that.edgeId) : that.edgeId != null) {
      return false;
    }
    return variables.equals(that.variables);
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
