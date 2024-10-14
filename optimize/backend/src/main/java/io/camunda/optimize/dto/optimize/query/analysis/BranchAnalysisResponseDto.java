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
    final int PRIME = 59;
    int result = 1;
    final Object $endEvent = getEndEvent();
    result = result * PRIME + ($endEvent == null ? 43 : $endEvent.hashCode());
    final Object $total = getTotal();
    result = result * PRIME + ($total == null ? 43 : $total.hashCode());
    final Object $followingNodes = getFollowingNodes();
    result = result * PRIME + ($followingNodes == null ? 43 : $followingNodes.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BranchAnalysisResponseDto)) {
      return false;
    }
    final BranchAnalysisResponseDto other = (BranchAnalysisResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$endEvent = getEndEvent();
    final Object other$endEvent = other.getEndEvent();
    if (this$endEvent == null ? other$endEvent != null : !this$endEvent.equals(other$endEvent)) {
      return false;
    }
    final Object this$total = getTotal();
    final Object other$total = other.getTotal();
    if (this$total == null ? other$total != null : !this$total.equals(other$total)) {
      return false;
    }
    final Object this$followingNodes = getFollowingNodes();
    final Object other$followingNodes = other.getFollowingNodes();
    if (this$followingNodes == null
        ? other$followingNodes != null
        : !this$followingNodes.equals(other$followingNodes)) {
      return false;
    }
    return true;
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
