/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.AVERAGE_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MAX_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.MIN_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.PERCENTILE_AGGREGATION_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.SUM_AGGREGATION_TYPE;

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
