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
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeNamesResponseDto that = (FlowNodeNamesResponseDto) o;
    return Objects.equals(flowNodeNames, that.flowNodeNames);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(flowNodeNames);
  }

  @Override
  public String toString() {
    return "FlowNodeNamesResponseDto(flowNodeNames=" + getFlowNodeNames() + ")";
  }
}
