/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit.mapToChronoUnit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.Duration;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
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
    final double doubleValue;
    final double doubleTarget;
    try {
      doubleValue = Double.parseDouble(value);
      doubleTarget = Double.parseDouble(target);
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          String.format("Error parsing KPI value %s and target %s", value, target));
    }
    if (isBelow) {
      return DURATION.equals(measure)
          ? Duration.ofMillis((long) doubleValue)
                  .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit)))
              <= 0
          : doubleValue <= doubleTarget;
    } else {
      return DURATION.equals(measure)
          ? Duration.ofMillis((long) doubleValue)
                  .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit)))
              >= 0
          : doubleValue >= doubleTarget;
    }
  }

  public static final class Fields {

    public static final String reportId = "reportId";
    public static final String collectionId = "collectionId";
    public static final String reportName = "reportName";
    public static final String value = "value";
    public static final String target = "target";
    public static final String isBelow = "isBelow";
    public static final String type = "type";
    public static final String measure = "measure";
    public static final String unit = "unit";
  }
}
