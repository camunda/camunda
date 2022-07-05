/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

@Data
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class KpiResultDto {

  private String reportId;
  private String reportName;
  private String value;
  private String target;
  @JsonProperty("isBelow")
  private boolean isBelow;
  private KpiType type;
  private ViewProperty measure;
  private TargetValueUnit unit;

}
