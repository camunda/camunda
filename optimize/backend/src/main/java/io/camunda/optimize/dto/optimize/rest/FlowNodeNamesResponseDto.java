/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.HashMap;
import java.util.Map;

public class FlowNodeNamesResponseDto {

  private Map<String, String> flowNodeNames = new HashMap<>();

  public FlowNodeNamesResponseDto() {}

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames;
  }

  public void setFlowNodeNames(final Map<String, String> flowNodeNames) {
    this.flowNodeNames = flowNodeNames;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeNamesResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $flowNodeNames = getFlowNodeNames();
    result = result * PRIME + ($flowNodeNames == null ? 43 : $flowNodeNames.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeNamesResponseDto)) {
      return false;
    }
    final FlowNodeNamesResponseDto other = (FlowNodeNamesResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$flowNodeNames = getFlowNodeNames();
    final Object other$flowNodeNames = other.getFlowNodeNames();
    if (this$flowNodeNames == null
        ? other$flowNodeNames != null
        : !this$flowNodeNames.equals(other$flowNodeNames)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "FlowNodeNamesResponseDto(flowNodeNames=" + getFlowNodeNames() + ")";
  }
}
