package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessDurationReportMapResultDto extends ProcessReportResultDto {

  private Map<String, AggregationResultDto> result = new LinkedHashMap<>();

  public Map<String, AggregationResultDto> getResult() {
    return result;
  }

  public void setResult(Map<String, AggregationResultDto> result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.DURATION_MAP;
  }
}
