/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class SingleReportTargetValueDto {

  private SingleReportCountChartDto countChart = new SingleReportCountChartDto();
  private DurationProgressDto durationProgress = new DurationProgressDto();
  private Boolean active = false;
  private CountProgressDto countProgress = new CountProgressDto();
  private SingleReportDurationChartDto durationChart = new SingleReportDurationChartDto();
  private Boolean isKpi;
}
