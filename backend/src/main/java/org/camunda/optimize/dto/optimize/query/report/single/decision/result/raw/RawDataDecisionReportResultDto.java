package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataDecisionReportResultDto extends DecisionReportResultDto {

  protected List<RawDataDecisionInstanceDto> data;

  public List<RawDataDecisionInstanceDto> getData() {
    return data;
  }

  public void setData(List<RawDataDecisionInstanceDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
