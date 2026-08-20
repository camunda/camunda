/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BranchAnalysisResponseDto {

  /** The end event the branch analysis is referred to. */
  protected String endEvent;

  /** The total amount of tokens that went from the gateway to the end event. */
  protected Long total;

  /** All branch analysis information of the flow nodes from the gateway to the end event. */
  protected Map<String, BranchAnalysisOutcomeDto> followingNodes = new HashMap<>();

  public BranchAnalysisResponseDto() {}

  public String getEndEvent() {
    return endEvent;
  }

  public void setEndEvent(final String endEvent) {
    this.endEvent = endEvent;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(final Long total) {
    this.total = total;
  }

  public Map<String, BranchAnalysisOutcomeDto> getFollowingNodes() {
    return followingNodes;
  }

  public void setFollowingNodes(final Map<String, BranchAnalysisOutcomeDto> followingNodes) {
    this.followingNodes = followingNodes;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BranchAnalysisResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(endEvent, total, followingNodes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BranchAnalysisResponseDto that = (BranchAnalysisResponseDto) o;
    return Objects.equals(endEvent, that.endEvent)
        && Objects.equals(total, that.total)
        && Objects.equals(followingNodes, that.followingNodes);
  }

  @Override
  public String toString() {
    return "BranchAnalysisResponseDto(endEvent="
        + getEndEvent()
        + ", total="
        + getTotal()
        + ", followingNodes="
        + getFollowingNodes()
        + ")";
  }
}
