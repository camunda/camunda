package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.FilterMapDto;

import java.util.logging.Filter;

public class ReportDataDto {

  protected String processDefinitionId;
  protected FilterMapDto filter;

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
}
