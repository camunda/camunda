package org.camunda.optimize.dto.optimize.query.report.single;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

import java.util.HashMap;

public abstract class SingleReportDataDto implements ReportDataDto, Combinable {

  protected Object configuration = new HashMap<>();

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Object configuration) {
    this.configuration = configuration;
  }
}
