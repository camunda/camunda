package org.camunda.optimize.dto.optimize.query.report.single.process.result;

public class NumberProcessReportResultDto extends ProcessReportResultDto {

  private long result;

  public long getResult() {
    return result;
  }

  public void setResult(long result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.NUMBER;
  }
}
