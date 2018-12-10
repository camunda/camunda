package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;

public enum DateFilterType {
  FIXED(FIXED_DATE_FILTER),
  RELATIVE(RELATIVE_DATE_FILTER),
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
