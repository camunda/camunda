/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;

public final class StepPublishMessage extends AbstractExecutionStep {

  private final String messageName;
  private final String correlationKeyValue;

  public StepPublishMessage(
      final String messageName,
      final String correlationKeyVariable,
      final String correlationKeyValue) {
    this.messageName = messageName;
    this.correlationKeyValue = correlationKeyValue;

    variables.put(correlationKeyVariable, correlationKeyValue);
  }

  public String getMessageName() {
    return messageName;
  }

  public String getCorrelationKeyValue() {
    return correlationKeyValue;
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
    result = 31 * result + messageName.hashCode();
    result = 31 * result + correlationKeyValue.hashCode();
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

    final StepPublishMessage that = (StepPublishMessage) o;

    if (!messageName.equals(that.messageName)) {
      return false;
    }
    return correlationKeyValue.equals(that.correlationKeyValue);
  }
}
