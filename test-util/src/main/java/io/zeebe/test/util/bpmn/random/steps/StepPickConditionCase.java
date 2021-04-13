/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

// this class could also be called "Set variables when starting the process so that the engine
// will select a certain condition"
public final class StepPickConditionCase extends AbstractExecutionStep {

  private final String forkingGatewayId;
  private final String edgeId;

  public StepPickConditionCase(
      final String forkingGatewayId, final String gatewayConditionVariable, final String edgeId) {
    this.forkingGatewayId = forkingGatewayId;
    this.edgeId = edgeId;
    variables.put(gatewayConditionVariable, edgeId);
  }

  @Override
  public boolean isAutomatic() {
    return true;
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

    final StepPickConditionCase that = (StepPickConditionCase) o;

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

  public void removeVariable(final String variable) {
    variables.remove(variable);
  }
}
