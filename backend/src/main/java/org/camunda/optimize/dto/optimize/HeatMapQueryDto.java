package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class HeatMapQueryDto implements Serializable {
  public static String START_DATE = "start_date";
  public static String END_DATE = "end_date";

  protected String processDefinitionId;
  protected List<String> flowNodes;
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

  public List<String> getFlowNodes() {
    return flowNodes;
  }

  public void setFlowNodes(List<String> flowNodes) {
    this.flowNodes = flowNodes;
  }
}
