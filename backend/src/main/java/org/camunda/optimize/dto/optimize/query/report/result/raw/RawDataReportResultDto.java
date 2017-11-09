package org.camunda.optimize.dto.optimize.query.report.result.raw;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;

import java.util.List;

public class RawDataReportResultDto extends ReportResultDto {

  protected List<RawDataProcessInstanceDto> result;

  public List<RawDataProcessInstanceDto> getResult() {
    return result;
  }

  public void setResult(List<RawDataProcessInstanceDto> result) {
    this.result = result;
  }

}
