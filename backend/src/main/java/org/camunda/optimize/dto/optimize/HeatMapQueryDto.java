package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class HeatMapQueryDto implements Serializable {

  protected String processDefinitionId;
  protected FilterDto filter;

  public FilterDto getFilter() {
    return filter;
  }

  public void setFilter(FilterDto filter) {
    this.filter = filter;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }
}
