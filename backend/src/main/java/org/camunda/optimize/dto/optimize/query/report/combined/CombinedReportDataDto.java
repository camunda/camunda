package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

import java.util.List;

public class CombinedReportDataDto implements ReportDataDto {

  protected Object configuration;
  protected List<String> reportIds;

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Object configuration) {
    this.configuration = configuration;
  }

  public List<String> getReportIds() {
    return reportIds;
  }

  public void setReportIds(List<String> reportIds) {
    this.reportIds = reportIds;
  }
}
