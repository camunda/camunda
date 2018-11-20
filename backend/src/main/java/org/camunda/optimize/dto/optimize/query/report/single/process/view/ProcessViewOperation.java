package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_RAW_DATA_OPERATION;

public enum ProcessViewOperation {
  RAW(VIEW_RAW_DATA_OPERATION),
  COUNT(VIEW_COUNT_OPERATION),
  AVG(VIEW_AVERAGE_OPERATION),
  MIN(VIEW_MIN_OPERATION),
  MAX(VIEW_MAX_OPERATION),
  MEDIAN(VIEW_MEDIAN_OPERATION),
  ;

  private final String id;

  ProcessViewOperation(final String id) {
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
