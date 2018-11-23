package org.camunda.optimize.dto.optimize.query.report.single.result.raw;

import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;

import java.util.List;

public class RawDataSingleReportResultDto extends SingleReportResultDto {

  protected List<RawDataProcessInstanceDto> result;

  public List<RawDataProcessInstanceDto> getResult() {
    return result;
  }

  public void setResult(List<RawDataProcessInstanceDto> result) {
    this.result = result;
  }

}
