/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metrics;

import io.micrometer.core.instrument.Statistic;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

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
