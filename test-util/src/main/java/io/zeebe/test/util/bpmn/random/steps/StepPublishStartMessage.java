/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.util.Map;

public final class StepPublishStartMessage extends AbstractExecutionStep {

  private final String messageName;

  public StepPublishStartMessage(final String messageName, final Map<String, Object> variables) {
    this.messageName = messageName;
    this.variables.putAll(variables);
  }

  public String getMessageName() {
    return messageName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final StepPublishStartMessage that = (StepPublishStartMessage) o;

    if (messageName != null ? !messageName.equals(that.messageName) : that.messageName != null) {
      return false;
    }
    return variables.equals(that.variables);
  }

  @Override
  public int hashCode() {
    int result = messageName != null ? messageName.hashCode() : 0;
    result = 31 * result + variables.hashCode();
    return result;
  }
}
