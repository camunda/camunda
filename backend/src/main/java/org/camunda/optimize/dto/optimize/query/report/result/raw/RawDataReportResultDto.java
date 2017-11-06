package org.camunda.optimize.dto.optimize.query.report.result.raw;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;

import java.util.List;

public class RawDataReportResultDto extends ReportResultDto {

  protected List<RawDataProcessInstanceDto> rawData;

  public List<RawDataProcessInstanceDto> getRawData() {
    return rawData;
  }

  public void setRawData(List<RawDataProcessInstanceDto> rawData) {
    this.rawData = rawData;
  }

  public void copyReportDataProperties(ReportDataDto reportData) {
    this.setProcessDefinitionId(reportData.getProcessDefinitionId());
    this.setView(reportData.getView());
    this.setGroupBy(reportData.getGroupBy());
    this.setFilter(reportData.getFilter());
    this.setVisualization(reportData.getVisualization());
  }
}
