/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
public class FindingsDto {
  private Finding lowerOutlier;
  private Finding higherOutlier;
  private Double lowerOutlierHeat = 0.0D;
  private Double higherOutlierHeat = 0.0D;
  private Double heat = 0.0D;
  private Long totalCount;

  public Optional<Finding> getLowerOutlier() {
    return Optional.ofNullable(lowerOutlier);
  }

  public Optional<Finding> getHigherOutlier() {
    return Optional.ofNullable(higherOutlier);
  }

  public void setLowerOutlier(Long boundValue, Double percentile, Double relation, Long count) {
    this.lowerOutlier = new Finding(boundValue, percentile, relation, count);
  }

  public void setHigherOutlier(Long boundValue, Double percentile, Double relation, Long count) {
    this.higherOutlier = new Finding(boundValue, percentile, relation, count);
  }

  public Long getOutlierCount() {
    return Optional.ofNullable(lowerOutlier).map(Finding::getCount).orElse(0L)
      + Optional.ofNullable(higherOutlier).map(Finding::getCount).orElse(0L);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public class Finding {
    private Long boundValue;
    private Double percentile;
    private Double relation;
    private Long count = 0L;
  }
}

