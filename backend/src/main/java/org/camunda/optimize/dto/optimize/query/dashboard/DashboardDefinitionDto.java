package org.camunda.optimize.dto.optimize.query.dashboard;

import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;

import java.util.ArrayList;
import java.util.List;

public class DashboardDefinitionDto extends BaseDashboardDefinitionDto implements CollectionEntity {


  protected List<ReportLocationDto> reports = new ArrayList<>();

  public List<ReportLocationDto> getReports() {
    return reports;
  }

  public void setReports(List<ReportLocationDto> reports) {
    this.reports = reports;
  }
}
