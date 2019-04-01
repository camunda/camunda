package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class ProcessDurationReportNumberResultDto extends ProcessReportResultDto {

  private AggregationResultDto data;

  public AggregationResultDto getData() {
    return data;
  }

  public void setData(AggregationResultDto data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.DURATION_NUMBER;
  }
}
