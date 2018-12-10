package org.camunda.optimize.dto.optimize.query.report.single.result;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.camunda.optimize.dto.optimize.ReportConstants.MAP_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.NUMBER_RESULT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.RAW_RESULT_TYPE;

public enum ResultType {
  @JsonProperty(MAP_RESULT_TYPE)
  MAP,
  @JsonProperty(NUMBER_RESULT_TYPE)
  NUMBER,
  @JsonProperty(RAW_RESULT_TYPE)
  RAW,
  ;
}
