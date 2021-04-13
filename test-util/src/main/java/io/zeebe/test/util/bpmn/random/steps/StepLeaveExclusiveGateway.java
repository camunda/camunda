/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

public final class StepLeaveExclusiveGateway extends AbstractExecutionStep {

  private final String joiningGatewayId;

  public StepLeaveExclusiveGateway(final String joiningGatewayId) {
    this.joiningGatewayId = joiningGatewayId;
  }

  @Override
  public boolean isAutomatic() {
    return true;
  }

  @Override
  public int hashCode() {
    int result = joiningGatewayId != null ? joiningGatewayId.hashCode() : 0;
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

    final StepLeaveExclusiveGateway that = (StepLeaveExclusiveGateway) o;

    if (joiningGatewayId != null
        ? !joiningGatewayId.equals(that.joiningGatewayId)
        : that.joiningGatewayId != null) {
      return false;
    }
    return variables.equals(that.variables);
  }
}
