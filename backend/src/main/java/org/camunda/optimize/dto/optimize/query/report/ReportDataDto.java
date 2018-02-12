package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;

import java.util.ArrayList;
import java.util.List;

public class ReportDataDto {

  protected String processDefinitionId;
  protected List<FilterDto> filter = new ArrayList<>();
  protected ViewDto view;
  protected GroupByDto groupBy;
  protected String visualization;
  protected String configuration;


  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public List<FilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<FilterDto> filter) {
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

  public String getConfiguration() {
    return configuration;
  }

  public void setConfiguration(String configuration) {
    this.configuration = configuration;
  }
}
