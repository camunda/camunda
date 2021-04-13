/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

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
  public Duration getDeltaTime() {
    return VIRTUALLY_NO_TIME;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + forkingGatewayId.hashCode();
    result = 31 * result + edgeId.hashCode();
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

    final StepPickConditionCase that = (StepPickConditionCase) o;

    if (!forkingGatewayId.equals(that.forkingGatewayId)) {
      return false;
    }
    return edgeId.equals(that.edgeId);
  }

  public void removeVariable(final String variable) {
    variables.remove(variable);
  }
}
