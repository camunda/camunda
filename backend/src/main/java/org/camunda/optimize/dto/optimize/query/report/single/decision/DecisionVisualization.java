package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.HEAT_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
import static org.camunda.optimize.dto.optimize.ReportConstants.TABLE_VISUALIZATION;

public enum DecisionVisualization {
  NUMBER(SINGLE_NUMBER_VISUALIZATION),
  TABLE(TABLE_VISUALIZATION),
  HEAT(HEAT_VISUALIZATION),
  ;

  private final String id;

  DecisionVisualization(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
