package org.camunda.optimize.dto.optimize.query.report.single.process.result.duration;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessDurationReportMapResultDto extends ProcessReportResultDto {

  private Map<String, AggregationResultDto> data = new LinkedHashMap<>();

  public Map<String, AggregationResultDto> getData() {
    return data;
  }

  public void setData(Map<String, AggregationResultDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.DURATION_MAP;
  }
}
