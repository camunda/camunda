/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.autogeneration;

import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import java.util.ArrayList;
import java.util.List;

public class CorrelationValueDto {

  private String businessKey;
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();

  public CorrelationValueDto() {}

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public List<SimpleProcessVariableDto> getVariables() {
    return variables;
  }

  public void setVariables(final List<SimpleProcessVariableDto> variables) {
    this.variables = variables;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CorrelationValueDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $businessKey = getBusinessKey();
    result = result * PRIME + ($businessKey == null ? 43 : $businessKey.hashCode());
    final Object $variables = getVariables();
    result = result * PRIME + ($variables == null ? 43 : $variables.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CorrelationValueDto)) {
      return false;
    }
    final CorrelationValueDto other = (CorrelationValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$businessKey = getBusinessKey();
    final Object other$businessKey = other.getBusinessKey();
    if (this$businessKey == null
        ? other$businessKey != null
        : !this$businessKey.equals(other$businessKey)) {
      return false;
    }
    final Object this$variables = getVariables();
    final Object other$variables = other.getVariables();
    if (this$variables == null
        ? other$variables != null
        : !this$variables.equals(other$variables)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CorrelationValueDto(businessKey="
        + getBusinessKey()
        + ", variables="
        + getVariables()
        + ")";
  }
}
