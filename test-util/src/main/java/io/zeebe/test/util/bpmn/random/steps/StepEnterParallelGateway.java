/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

public final class StepEnterParallelGateway extends AbstractExecutionStep {

  private final String forkingGatewayId;

  public StepEnterParallelGateway(final String forkingGatewayId) {
    this.forkingGatewayId = forkingGatewayId;
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

    final StepEnterParallelGateway that = (StepEnterParallelGateway) o;

    return forkingGatewayId.equals(that.forkingGatewayId);
  }
}
