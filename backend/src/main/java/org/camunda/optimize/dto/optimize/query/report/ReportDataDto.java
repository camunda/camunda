package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.FilterMapDto;

import java.util.logging.Filter;

public class ReportDataDto {

  protected String processDefinitionId;
  protected FilterMapDto filter;
  protected ViewDto view;
  protected GroupByDto groupBy;
  protected String visualization;


  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public FilterMapDto getFilter() {
    return filter;
  }

  public void setFilter(FilterMapDto filter) {
    this.filter = filter;
  }

  public ViewDto getView() {
    return view;
  }

  public void setView(ViewDto view) {
    this.view = view;
  }

  public GroupByDto getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(GroupByDto groupBy) {
    this.groupBy = groupBy;
  }

  public String getVisualization() {
    return visualization;
  }

  public void setVisualization(String visualization) {
    this.visualization = visualization;
  }

  public void copyReportDataProperties(ReportDataDto reportData) {
    this.setProcessDefinitionId(reportData.getProcessDefinitionId());
    this.setView(reportData.getView());
    this.setGroupBy(reportData.getGroupBy());
    this.setFilter(reportData.getFilter());
    this.setVisualization(reportData.getVisualization());
  }

}
