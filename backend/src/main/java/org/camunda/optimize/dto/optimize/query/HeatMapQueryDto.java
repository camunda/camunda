package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class HeatMapQueryDto implements Serializable {

  protected String processDefinitionId;
  protected List<FilterDto> filter = new ArrayList<>();

  public List<FilterDto> getFilter() {
    return filter;
  }

  public void setFilter(List<FilterDto> filter) {
    this.filter = filter;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }
}
