/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import static io.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DateFilterType {
  FIXED(FIXED_DATE_FILTER),
  RELATIVE(RELATIVE_DATE_FILTER),
  ROLLING(ROLLING_DATE_FILTER);

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
