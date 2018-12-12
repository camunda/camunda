package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.HashMap;
import java.util.Map;

public class DecisionReportMapResultDto extends DecisionReportResultDto {

  private Map<String, Long> result = new HashMap<>();

  public Map<String, Long> getResult() {
    return result;
  }

  public void setResult(Map<String, Long> result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.MAP;
  }
}
