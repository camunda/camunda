/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

public enum DateFilterType {
  FIXED(FIXED_DATE_FILTER),
  RELATIVE(RELATIVE_DATE_FILTER),
  ROLLING(ROLLING_DATE_FILTER),
  ;

  private final String id;

  DateFilterType(final String id) {
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
