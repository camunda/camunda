/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.activity;

import java.util.List;
import java.util.Objects;

/**
 * Represents the list of flow node instances - direct children of one parent.
 */
public class FlowNodeInstanceResponseDto {

  private Boolean isRunning;
  private List<FlowNodeInstanceDto> children;

  public FlowNodeInstanceResponseDto() {
  }

  public FlowNodeInstanceResponseDto(final Boolean running,
      final List<FlowNodeInstanceDto> children) {
    this.isRunning = running;
    this.children = children;
  }

  public List<FlowNodeInstanceDto> getChildren() {
    return children;
  }

  public FlowNodeInstanceResponseDto setChildren(
      final List<FlowNodeInstanceDto> children) {
    this.children = children;
    return this;
  }

  /**
   * @return true when instances parent is still running. False otherwise.
   */
  public Boolean getRunning() {
    return isRunning;
  }

  public FlowNodeInstanceResponseDto setRunning(final Boolean running) {
    isRunning = running;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FlowNodeInstanceResponseDto that = (FlowNodeInstanceResponseDto) o;
    return Objects.equals(isRunning, that.isRunning) &&
        Objects.equals(children, that.children);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isRunning, children);
  }
}
