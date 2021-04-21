/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public final class StepPublishStartMessage extends AbstractExecutionStep
    implements ProcessStartStep {

  private final String messageName;

  public StepPublishStartMessage(final String messageName, final Map<String, Object> variables) {
    this.messageName = messageName;
    this.variables.putAll(variables);
  }

  @Override
  public Map<String, Object> getProcessVariables() {
    return Collections.unmodifiableMap(variables);
  }

  public String getMessageName() {
    return messageName;
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    return variables;
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

    final StepPublishStartMessage that = (StepPublishStartMessage) o;

    return messageName.equals(that.messageName);
  }
}
