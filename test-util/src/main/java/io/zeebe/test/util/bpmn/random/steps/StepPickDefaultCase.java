/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

public final class StepPickDefaultCase extends AbstractExecutionStep {

  private final String forkingGatewayId;

  public StepPickDefaultCase(final String forkingGatewayId, final String gatewayConditionVariable) {
    this.forkingGatewayId = forkingGatewayId;
    variables.put(gatewayConditionVariable, "default-case");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StepPickDefaultCase that = (StepPickDefaultCase) o;

    if (forkingGatewayId != null
        ? !forkingGatewayId.equals(that.forkingGatewayId)
        : that.forkingGatewayId != null) {
      return false;
    }
    return variables.equals(that.variables);
  }

  @Override
  public int hashCode() {
    int result = forkingGatewayId != null ? forkingGatewayId.hashCode() : 0;
    result = 31 * result + variables.hashCode();
    return result;
  }
}
