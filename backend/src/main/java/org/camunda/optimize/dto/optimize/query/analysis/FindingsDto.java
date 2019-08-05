/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class FindingsDto {
  private Finding lowerOutlier;
  private Finding higherOutlier;
  private Double heat;
  private Long outlierCount = 0L;
  public void setLowerOutlier(Double percentile, Double relation) {
    this.lowerOutlier = new Finding(percentile, relation);
  }

  public void setHigherOutlier(Double percentile, Double relation) {
    this.higherOutlier = new Finding(percentile, relation);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public class Finding {
    private Double percentile;
    private Double relation;
  }
}

