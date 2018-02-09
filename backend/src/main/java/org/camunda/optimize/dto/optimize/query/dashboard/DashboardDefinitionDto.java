package org.camunda.optimize.dto.optimize.query.dashboard;

import java.util.List;

public class DashboardDefinitionDto extends BaseDashboardDefinitionDto {


  protected List<ReportLocationDto> reports;

  public List<ReportLocationDto> getReports() {
    return reports;
  }

  public void setReports(List<ReportLocationDto> reports) {
    this.reports = reports;
  }
}
