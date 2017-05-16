package org.camunda.optimize.dto.optimize.query;

import java.io.Serializable;

/**
 * @author Askar Akhmerov
 */
public class HeatMapQueryDto implements Serializable {

  protected String processDefinitionId;
  protected FilterMapDto filter = new FilterMapDto();

  public FilterMapDto getFilter() {
    return filter;
  }

  public void setFilter(FilterMapDto filter) {
    this.filter = filter;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }
}
