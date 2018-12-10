package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataProcessReportResultDto extends ProcessReportResultDto {

  protected List<RawDataProcessInstanceDto> result;

  public List<RawDataProcessInstanceDto> getResult() {
    return result;
  }

  public void setResult(List<RawDataProcessInstanceDto> result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.RAW;
  }

}
