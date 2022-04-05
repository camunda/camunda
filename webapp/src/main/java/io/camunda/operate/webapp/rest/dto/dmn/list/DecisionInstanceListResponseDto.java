/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceListResponseDto {

  private List<DecisionInstanceForListDto> decisionInstances = new ArrayList<>();

  private long totalCount;

  public List<DecisionInstanceForListDto> getDecisionInstances() {
    return decisionInstances;
  }

  public DecisionInstanceListResponseDto setDecisionInstances(
      final List<DecisionInstanceForListDto> decisionInstances) {
    this.decisionInstances = decisionInstances;
    return this;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public DecisionInstanceListResponseDto setTotalCount(final long totalCount) {
    this.totalCount = totalCount;
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
    final DecisionInstanceListResponseDto that = (DecisionInstanceListResponseDto) o;
    return totalCount == that.totalCount &&
        Objects.equals(decisionInstances, that.decisionInstances);
  }

  @Override
  public int hashCode() {
    return Objects.hash(decisionInstances, totalCount);
  }
}
