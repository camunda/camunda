package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.PROCESS_REPORT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DECISION_REPORT_TYPE;

public enum ReportType {
  PROCESS(PROCESS_REPORT_TYPE),
  DECISION(DECISION_REPORT_TYPE),
  ;

  private final String id;

  ReportType(final String id) {
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
