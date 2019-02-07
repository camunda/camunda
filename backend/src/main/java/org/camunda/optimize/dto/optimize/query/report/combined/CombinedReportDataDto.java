package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.configuration.ReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;

import java.util.ArrayList;
import java.util.List;

public class CombinedReportDataDto implements ReportDataDto {

  protected ReportConfigurationDto configuration = new ReportConfigurationDto();
  protected ProcessVisualization visualization;
  protected List<String> reportIds = new ArrayList<>();

  public ReportConfigurationDto getConfiguration() {
    return configuration;
  }

  public void setConfiguration(ReportConfigurationDto configuration) {
    this.configuration = configuration;
  }

  public ProcessVisualization getVisualization() {
    return visualization;
  }

  public void setVisualization(ProcessVisualization visualization) {
    this.visualization = visualization;
  }

  public List<String> getReportIds() {
    return reportIds;
  }

  public void setReportIds(List<String> reportIds) {
    this.reportIds = reportIds;
  }

  @Override
  public String createCommandKey() {
    return "combined";
  }
}
