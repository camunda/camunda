package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataDecisionReportResultDto extends DecisionReportResultDto {

  protected List<RawDataDecisionInstanceDto> result;

  public List<RawDataDecisionInstanceDto> getResult() {
    return result;
  }

  public void setResult(List<RawDataDecisionInstanceDto> result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.RAW;
  }

}
