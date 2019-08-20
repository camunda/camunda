/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
  private Double heat;

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

