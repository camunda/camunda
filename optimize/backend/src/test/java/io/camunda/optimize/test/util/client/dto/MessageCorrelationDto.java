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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MessageCorrelationDto that = (MessageCorrelationDto) o;
    return all == that.all
        && Objects.equals(processVariables, that.processVariables)
        && Objects.equals(messageName, that.messageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processVariables, messageName, all);
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
