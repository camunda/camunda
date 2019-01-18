package org.camunda.optimize.dto.optimize.query.report.single;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;

public abstract class SingleReportDataDto implements ReportDataDto, Combinable {

  protected ReportConfigurationDto configuration = new ReportConfigurationDto();

  public ReportConfigurationDto getConfiguration() {
    return configuration;
  }

  public void setConfiguration(ReportConfigurationDto configuration) {
    this.configuration = configuration;
  }
}
