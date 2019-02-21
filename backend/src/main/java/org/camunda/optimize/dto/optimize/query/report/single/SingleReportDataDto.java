package org.camunda.optimize.dto.optimize.query.report.single;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;

public abstract class SingleReportDataDto implements ReportDataDto, Combinable {

  protected SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();

  public SingleReportConfigurationDto getConfiguration() {
    return configuration;
  }

  public void setConfiguration(SingleReportConfigurationDto configuration) {
    this.configuration = configuration;
  }
}
