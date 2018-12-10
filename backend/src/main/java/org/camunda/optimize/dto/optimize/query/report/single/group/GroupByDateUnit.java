package org.camunda.optimize.dto.optimize.query.report.single.group;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_HOUR;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MONTH;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_WEEK;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_YEAR;

public enum GroupByDateUnit {
  YEAR(DATE_UNIT_YEAR),
  MONTH(DATE_UNIT_MONTH),
  WEEK(DATE_UNIT_WEEK),
  DAY(DATE_UNIT_DAY),
  HOUR(DATE_UNIT_HOUR),
  ;

  private final String id;

  GroupByDateUnit(final String id) {
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
