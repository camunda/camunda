package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProcessReportMapResultDto extends ProcessReportResultDto {

  private Map<String, Long> data = new LinkedHashMap<>();

  public Map<String, Long> getData() {
    return data;
  }

  public void setData(Map<String, Long> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.FREQUENCY_MAP;
  }
}
