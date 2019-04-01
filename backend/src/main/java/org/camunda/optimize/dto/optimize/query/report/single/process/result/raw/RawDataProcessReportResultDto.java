package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.List;

public class RawDataProcessReportResultDto extends ProcessReportResultDto {

  protected List<RawDataProcessInstanceDto> data;

  public List<RawDataProcessInstanceDto> getData() {
    return data;
  }

  public void setData(List<RawDataProcessInstanceDto> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

}
