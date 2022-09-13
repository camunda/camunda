/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metrics;

import io.micrometer.core.instrument.Statistic;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class MetricResponseDto {
  private String name;
  private String description;
  private String baseUnit;
  private List<StatisticDto> measurements;
  private List<TagDto> availableTags;

  @Data
  @NoArgsConstructor
  public static class StatisticDto {
    private Statistic statistic;
    private Double value;
  }

  @Data
  @NoArgsConstructor
  public static class TagDto {
    private String tag;
    private List<String> values;
  }
}
