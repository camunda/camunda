/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.client.dto;

import java.util.HashMap;
import java.util.Map;

public class MessageCorrelationDto {

  Map<String, VariableValueDto> processVariables = new HashMap<>();
  private String messageName;
  private boolean all;

  public MessageCorrelationDto() {}

  public String getMessageName() {
    return messageName;
  }

  public void setMessageName(final String messageName) {
    this.messageName = messageName;
  }

  public boolean isAll() {
    return all;
  }

  public void setAll(final boolean all) {
    this.all = all;
  }

  public Map<String, VariableValueDto> getProcessVariables() {
    return processVariables;
  }

  public void setProcessVariables(final Map<String, VariableValueDto> processVariables) {
    this.processVariables = processVariables;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MessageCorrelationDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $messageName = getMessageName();
    result = result * PRIME + ($messageName == null ? 43 : $messageName.hashCode());
    result = result * PRIME + (isAll() ? 79 : 97);
    final Object $processVariables = getProcessVariables();
    result = result * PRIME + ($processVariables == null ? 43 : $processVariables.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MessageCorrelationDto)) {
      return false;
    }
    final MessageCorrelationDto other = (MessageCorrelationDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$messageName = getMessageName();
    final Object other$messageName = other.getMessageName();
    if (this$messageName == null
        ? other$messageName != null
        : !this$messageName.equals(other$messageName)) {
      return false;
    }
    if (isAll() != other.isAll()) {
      return false;
    }
    final Object this$processVariables = getProcessVariables();
    final Object other$processVariables = other.getProcessVariables();
    if (this$processVariables == null
        ? other$processVariables != null
        : !this$processVariables.equals(other$processVariables)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MessageCorrelationDto(messageName="
        + getMessageName()
        + ", all="
        + isAll()
        + ", processVariables="
        + getProcessVariables()
        + ")";
  }
}
