package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class ProcessDurationReportNumberResultDto extends ProcessReportResultDto {

  private OperationResultDto result;

  public OperationResultDto getResult() {
    return result;
  }

  public void setResult(OperationResultDto result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.DURATION_NUMBER;
  }
}
