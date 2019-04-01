package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.HashMap;
import java.util.Map;

public class DecisionReportMapResultDto extends DecisionReportResultDto {

  private Map<String, Long> data = new HashMap<>();

  public Map<String, Long> getData() {
    return data;
  }

  public void getData(Map<String, Long> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.FREQUENCY_MAP;
  }
}
