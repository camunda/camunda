/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StepPublishMessage that = (StepPublishMessage) o;

    if (messageName != null ? !messageName.equals(that.messageName) : that.messageName != null) {
      return false;
    }
    if (correlationKeyValue != null
        ? !correlationKeyValue.equals(that.correlationKeyValue)
        : that.correlationKeyValue != null) {
      return false;
    }

    return variables.equals(that.variables);
  }

  @Override
  public int hashCode() {
    int result = messageName != null ? messageName.hashCode() : 0;
    result = 31 * result + (correlationKeyValue != null ? correlationKeyValue.hashCode() : 0);
    result = 31 * result + variables.hashCode();
    return result;
  }
}
