/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.processoverview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

import java.time.Duration;

import static org.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit.mapToChronoUnit;

@Data
@FieldNameConstants
@NoArgsConstructor
public class KpiResultDto {

  private String reportId;
  private String collectionId;
  private String reportName;
  private String value;
  private String target;
  @JsonProperty("isBelow")
  private boolean isBelow;
  private KpiType type;
  private ViewProperty measure;
  private TargetValueUnit unit;

  @JsonIgnore
  public boolean isTargetMet() {
    if (StringUtils.isBlank(value) || StringUtils.isBlank(target)) {
      return false;
    }
    final double doubleValue = Double.parseDouble(value);
    final double doubleTarget = Double.parseDouble(target);
    if (isBelow) {
      return DURATION.equals(measure)
        ? Duration.ofMillis((long) doubleValue)
        .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit))) <= 0
        : doubleValue <= doubleTarget;
    } else {
      return DURATION.equals(measure)
        ? Duration.ofMillis((long) doubleValue)
        .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit))) >= 0
        : doubleValue >= doubleTarget;
    }
  }

}
