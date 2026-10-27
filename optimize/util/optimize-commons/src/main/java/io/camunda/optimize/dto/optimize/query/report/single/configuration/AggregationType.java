/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import static io.camunda.optimize.dto.optimize.ReportConstants.AVERAGE_AGGREGATION_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.MAX_AGGREGATION_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.MIN_AGGREGATION_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.PERCENTILE_AGGREGATION_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.SUM_AGGREGATION_TYPE;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AggregationType {
  AVERAGE(AVERAGE_AGGREGATION_TYPE),
  MIN(MIN_AGGREGATION_TYPE),
  MAX(MAX_AGGREGATION_TYPE),
  SUM(SUM_AGGREGATION_TYPE),
  PERCENTILE(PERCENTILE_AGGREGATION_TYPE);

  private final String id;

  AggregationType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
